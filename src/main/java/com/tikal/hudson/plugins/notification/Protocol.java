package com.tikal.hudson.plugins.notification;

import hudson.model.Job;
import hudson.model.Run;

import java.io.IOException;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.tikal.hudson.plugins.notification.model.BuildState;
import com.tikal.hudson.plugins.notification.model.JobState;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.HttpParams;


@SuppressWarnings("rawtypes")
public enum Protocol {

	UDP {
		@Override
		protected void send(String url, byte[] data) {
			try {
				HostnamePort hostnamePort = HostnamePort.parseUrl(url);
				DatagramSocket socket = new DatagramSocket();
				DatagramPacket packet = new DatagramPacket(data, data.length, InetAddress.getByName(hostnamePort.hostname), hostnamePort.port);
				socket.send(packet);
			} catch (Exception e) {
				e.printStackTrace();
			}
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
		protected void send(String url, byte[] data) {
			try {
				HostnamePort hostnamePort = HostnamePort.parseUrl(url);
				SocketAddress endpoint = new InetSocketAddress(InetAddress.getByName(hostnamePort.hostname), hostnamePort.port);
				Socket socket = new Socket();
				socket.connect(endpoint);
				OutputStream output = socket.getOutputStream();
				output.write(data);
				output.flush();
				output.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	},
	HTTP {
		@Override
		protected void send(String url, byte[] data) {
			try {
                List<NameValuePair> params = new ArrayList<NameValuePair>();
                params.add(new BasicNameValuePair("data", new String(data, "UTF-8")));
                UrlEncodedFormEntity entity = new UrlEncodedFormEntity(params, "UTF-8");

                HttpPost post = new HttpPost(url);
                post.setEntity(entity);

                new DefaultHttpClient().execute(post);

			} catch (IOException e) {
				e.printStackTrace();
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

	private Gson gson = new GsonBuilder().create();

	public void sendNotification(String url, Job job, Run run, Phase phase, String status) {
		send(url, buildMessage(job, run, phase, status));
	}

	private byte[] buildMessage(Job job, Run run, Phase phase, String status) {
		JobState jobState = new JobState();
		jobState.setName(job.getName());
		jobState.setUrl(job.getUrl());
		BuildState buildState = new BuildState();
		buildState.setNumber(run.number);
		buildState.setUrl(run.getUrl());
		buildState.setPhase(phase);
		buildState.setStatus(status);
		jobState.setBuild(buildState);
		return gson.toJson(jobState).getBytes();
	}

	abstract protected void send(String url, byte[] data);

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