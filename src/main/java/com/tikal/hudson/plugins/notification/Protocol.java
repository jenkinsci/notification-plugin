/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.tikal.hudson.plugins.notification;


import java.io.IOException;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.URL;

import com.tikal.hudson.plugins.notification.model.JobState;

public enum Protocol {

	UDP {
		@Override
		protected void send(String url, byte[] data) throws IOException {
            HostnamePort hostnamePort = HostnamePort.parseUrl(url);
            DatagramSocket socket = new DatagramSocket();
            DatagramPacket packet = new DatagramPacket(data, data.length, InetAddress.getByName(hostnamePort.hostname), hostnamePort.port);
            socket.send(packet);
		}

		@Override
		public void validateUrl(String url) {
			try {
				HostnamePort hnp = HostnamePort.parseUrl(url);
				if (hnp == null) {
					throw new Exception();
				}
			} catch (Exception e) {
				throw new RuntimeException("Invalid Url: hostname:port");
			}
		}
	},
	TCP {
		@Override
		protected void send(String url, byte[] data) throws IOException {
            HostnamePort hostnamePort = HostnamePort.parseUrl(url);
            SocketAddress endpoint = new InetSocketAddress(InetAddress.getByName(hostnamePort.hostname), hostnamePort.port);
            Socket socket = new Socket();
            socket.connect(endpoint);
            OutputStream output = socket.getOutputStream();
            output.write(data);
            output.flush();
            output.close();
		}
	},
	HTTP {
		@Override
		protected void send(String url, byte[] data) throws IOException {
            URL targetUrl = new URL(url);
            if (!targetUrl.getProtocol().startsWith("http")) {
              throw new IllegalArgumentException("Not an http(s) url: " + url);
            }

            HttpURLConnection connection = (HttpURLConnection) targetUrl.openConnection();
            connection.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
            connection.setFixedLengthStreamingMode(data.length);
            connection.setDoInput(true);
            connection.setDoOutput(true);

            connection.connect();
            try {
              OutputStream output = connection.getOutputStream();
              try {
                output.write(data);
                output.flush();
              } finally {
                output.close();
              }
            } finally {
              // Follow an HTTP Temporary Redirect if we get one,
              //
              // NB: Normally using the HttpURLConnection interface, we'd call
              // connection.setInstanceFollowRedirects(true) to enable 307 redirect following but
              // since we have the connection in streaming mode this does not work and we instead
              // re-direct manually.
              if (307 == connection.getResponseCode()) {
                String location = connection.getHeaderField("Location");
                connection.disconnect();
                send(location, data);
              } else {
                connection.disconnect();
              }
            }
		}

		public void validateUrl(String url) {
			try {
				new URL(url);
			} catch (MalformedURLException e) {
				throw new RuntimeException("Invalid Url: http://hostname:port/path");
			}
		}
	};

	public void sendNotification(Endpoint endpoint, JobState jobState) throws IOException {
		send(endpoint.getUrl(), endpoint.getOutput().serialize(jobState));
	}

	abstract protected void send(String url, byte[] data) throws IOException;

	public void validateUrl(String url) {
		try {
			HostnamePort hnp = HostnamePort.parseUrl(url);
			if (hnp == null) {
				throw new Exception();
			}
		} catch (Exception e) {
			throw new RuntimeException("Invalid Url: hostname:port");
		}
	}
}
