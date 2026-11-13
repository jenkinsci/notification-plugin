/*
 * The MIT License
 *
 * Copyright (c) 2011, CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.tikal.hudson.plugins.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.base.MoreObjects;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * @author Kohsuke Kawaguchi
 */
class ProtocolTest {

    static class Request {
        private final String url;
        private final String method;
        private final String body;
        private final String userInfo;

        Request(HttpExchange he) throws IOException {
            InetSocketAddress address = he.getLocalAddress();
            this.url = "http://" + address.getHostString() + ":"
                    + address.getPort()
                    + he.getRequestURI().toString();
            this.method = he.getRequestMethod();
            this.body = new String(he.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String auth = he.getRequestHeaders().getFirst("Authorization");
            this.userInfo =
                    (null == auth) ? null : new String(Base64.getDecoder().decode(auth.split(" ")[1])) + "@";
        }

        Request(String url, String method, String body) {
            this.url = url;
            this.method = method;
            this.body = body;
            this.userInfo = null;
        }

        @Override
        public int hashCode() {
            return Objects.hash(url, method, body);
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof Request other)) {
                return false;
            }
            return Objects.equals(url, other.url)
                    && Objects.equals(method, other.method)
                    && Objects.equals(body, other.body);
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("url", url)
                    .add("method", method)
                    .add("body", body)
                    .toString();
        }

        public String getUrl() {
            return url;
        }

        public String getUrlWithAuthority() {
            if (null == userInfo) {
                // Detect possible bug: userInfo never moved from URI to Authorization header
                return null;
            } else {
                return url.replaceFirst("^http://", "http://" + userInfo);
            }
        }
    }

    static class RecordingServlet implements HttpHandler {
        private final BlockingQueue<Request> requests;

        public RecordingServlet(BlockingQueue<Request> requests) {
            this.requests = requests;
        }

        @Override
        public void handle(HttpExchange he) throws IOException {

            Request request = new Request(he);
            try {
                requests.put(request);
            } catch (InterruptedException e) {
                throw new IOException("Interrupted while adding request to queue", e);
            }
            he.sendResponseHeaders(HttpURLConnection.HTTP_OK, 0);
            he.close();
        }
    }

    static class RedirectHandler implements HttpHandler {
        private final BlockingQueue<Request> requests;
        private final String redirectURI;

        public RedirectHandler(BlockingQueue<Request> requests, String redirectURI) {
            this.requests = Objects.requireNonNull(requests);
            this.redirectURI = Objects.requireNonNull(redirectURI);
        }

        @Override
        public void handle(HttpExchange he) throws IOException {
            Request request = new Request(he);
            try {
                requests.put(request);
            } catch (InterruptedException e) {
                throw new IOException("Interrupted while adding request to queue", e);
            }
            he.getResponseHeaders().set("Location", redirectURI);
            he.sendResponseHeaders(307, -1);
            he.close();
        }
    }

    private List<HttpServer> servers;

    interface UrlFactory {
        String getUrl(String path);
    }

    private UrlFactory startServer(HttpHandler handler, String path) throws Exception {
        return startSecureServer(handler, path, "");
    }

    private UrlFactory startSecureServer(HttpHandler handler, String path, String authority) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext(path, handler);

        server.start();
        servers.add(server);

        if (!authority.isEmpty()) {
            authority += "@";
        }

        InetSocketAddress address = server.getAddress();
        final URL serverUrl =
                new URL(String.format("http://%s%s:%d", authority, address.getHostString(), address.getPort()));
        return new UrlFactory() {
            @Override
            public String getUrl(String path) {
                try {
                    return new URL(serverUrl, path).toExternalForm();
                } catch (MalformedURLException e) {
                    throw new IllegalArgumentException(e);
                }
            }
        };
    }

    @BeforeEach
    void setUp() {
        servers = new LinkedList<>();
    }

    @AfterEach
    void tearDown() {
        for (HttpServer server : servers) {
            server.stop(1);
        }
    }

    @Test
    void testHttpPost() throws Exception {
        BlockingQueue<Request> requests = new LinkedBlockingQueue<>();

        UrlFactory urlFactory = startServer(new RecordingServlet(requests), "/realpath");

        assertTrue(requests.isEmpty());

        String uri = urlFactory.getUrl("/realpath");
        Protocol.HTTP.send(uri, "Hello".getBytes(), 30000, true);

        assertEquals(new Request(uri, "POST", "Hello"), requests.take());
        assertTrue(requests.isEmpty());
    }

    @Test
    void testHttpPostWithBasicAuth() throws Exception {
        BlockingQueue<Request> requests = new LinkedBlockingQueue<>();

        UrlFactory urlFactory = startSecureServer(new RecordingServlet(requests), "/realpath", "fred:foo");

        assertTrue(requests.isEmpty());

        String uri = urlFactory.getUrl("/realpath");
        Protocol.HTTP.send(uri, "Hello".getBytes(), 30000, true);

        Request theRequest = requests.take();
        assertTrue(requests.isEmpty());
        assertEquals(new Request(uri, "POST", "Hello").getUrl(), theRequest.getUrlWithAuthority());
    }

    @Test
    void testHttpPostWithRedirects() throws Exception {
        BlockingQueue<Request> requests = new LinkedBlockingQueue<>();

        UrlFactory urlFactory = startServer(new RecordingServlet(requests), "/realpath");

        String redirectUri = urlFactory.getUrl("/realpath");
        UrlFactory redirectorUrlFactory = startServer(new RedirectHandler(requests, redirectUri), "/path");

        assertTrue(requests.isEmpty());

        String uri = redirectorUrlFactory.getUrl("/path");
        Protocol.HTTP.send(uri, "RedirectMe".getBytes(), 30000, true);

        assertEquals(new Request(uri, "POST", "RedirectMe"), requests.take());
        assertEquals(new Request(redirectUri, "POST", "RedirectMe"), requests.take());
        assertTrue(requests.isEmpty());
    }
}
