/*
+ * The MIT License
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

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.common.base.Objects;
import com.google.common.io.CharStreams;

import org.mortbay.jetty.HttpHeaders;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.bio.SocketConnector;
import org.mortbay.jetty.servlet.ServletHandler;
import org.mortbay.jetty.servlet.ServletHolder;

import junit.framework.TestCase;

/**
 * @author Kohsuke Kawaguchi
 */
public class ProtocolTest extends TestCase {

  static class Request {
    private final String url;
    private final String method;
    private final String body;

    Request(HttpServletRequest request) throws IOException {
      this(request.getRequestURL().toString(), request.getMethod(), CharStreams.toString(request.getReader()));
    }

    Request(String url, String method, String body) {
      this.url = url;
      this.method = method;
      this.body = body;
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(url, method, body);
    }

    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof Request)) {
        return false;
      }
      Request other = (Request) obj;
      return Objects.equal(url, other.url)
          && Objects.equal(method, other.method)
          && Objects.equal(body, other.body);
    }

    @Override
    public String toString() {
      return Objects.toStringHelper(this)
          .add("url", url)
          .add("method", method)
          .add("body", body)
          .toString();
    }
  }

  static class RecordingServlet extends HttpServlet {
    private final BlockingQueue<Request> requests;

    public RecordingServlet(BlockingQueue<Request> requests) {
      this.requests = requests;
    }

    @Override
    protected void doPost(HttpServletRequest httpRequest, HttpServletResponse httpResponse)
        throws ServletException, IOException {

      Request request = new Request(httpRequest);
      try {
        requests.put(request);
      } catch (InterruptedException e) {
        throw new ServletException(e);
      }

      doPost(request, httpResponse);
    }

    protected void doPost(Request request, HttpServletResponse httpResponse) throws IOException {
      // noop
    }
  }

  static class RedirectHandler extends RecordingServlet {
    private final String redirectURI;

    RedirectHandler(BlockingQueue<Request> requests, String redirectURI) {
      super(requests);
      this.redirectURI = redirectURI;
    }

    @Override
    protected void doPost(Request request, HttpServletResponse httpResponse) throws IOException {
      httpResponse.setStatus(HttpServletResponse.SC_TEMPORARY_REDIRECT);
      httpResponse.setHeader(HttpHeaders.LOCATION, redirectURI);
    }
  }

  private List<Server> servers;

  interface UrlFactory {
    String getUrl(String path);
  }

  private UrlFactory startServer(Servlet servlet, String path) throws Exception {
    SocketConnector connector = new SocketConnector();
    connector.setPort(0);
    connector.open();

    Server server = new Server();
    server.addConnector(connector);

    ServletHandler servletHandler = new ServletHandler();
    servletHandler.addServletWithMapping(new ServletHolder(servlet), path);
    server.addHandler(servletHandler);

    server.start();
    servers.add(server);

    final URL serverUrl = new URL(String.format("http://localhost:%d", connector.getLocalPort()));
    return new UrlFactory() {
      public String getUrl(String path) {
        try {
          return new URL(serverUrl, path).toExternalForm();
        } catch (MalformedURLException e) {
          throw new IllegalArgumentException(e);
        }
      }
    };
  }

  @Override
  public void setUp() throws Exception {
    servers = new LinkedList<Server>();
  }

  @Override
  public void tearDown() throws Exception {
    for (Server server : servers) {
      server.stop();
    }
  }

  public void testHttpPost() throws Exception {
    BlockingQueue<Request> requests = new LinkedBlockingQueue<Request>();

    UrlFactory urlFactory = startServer(new RecordingServlet(requests), "/realpath");

    assertTrue(requests.isEmpty());

    String uri = urlFactory.getUrl("/realpath");
    Protocol.HTTP.send(uri, "Hello".getBytes());

    assertEquals(new Request(uri, "POST", "Hello"), requests.take());
    assertTrue(requests.isEmpty());
  }

  public void testHttpPostWithRedirects() throws Exception {
    BlockingQueue<Request> requests = new LinkedBlockingQueue<Request>();

    UrlFactory urlFactory = startServer(new RecordingServlet(requests), "/realpath");

    String redirectUri = urlFactory.getUrl("/realpath");
    UrlFactory redirectorUrlFactory = startServer(new RedirectHandler(requests, redirectUri), "/path");

    assertTrue(requests.isEmpty());

    String uri = redirectorUrlFactory.getUrl("/path");
    Protocol.HTTP.send(uri, "RedirectMe".getBytes());

    assertEquals(new Request(uri, "POST", "RedirectMe"), requests.take());
    assertEquals(new Request(redirectUri, "POST", "RedirectMe"), requests.take());
    assertTrue(requests.isEmpty());
  }
}
