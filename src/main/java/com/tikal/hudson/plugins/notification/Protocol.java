package com.tikal.hudson.plugins.notification;

import hudson.EnvVars;
import hudson.model.AbstractBuild;
import hudson.model.Job;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.PasswordParameterValue;
import hudson.model.Run;

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
import java.net.URLConnection;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.tikal.hudson.plugins.notification.model.BuildState;
import com.tikal.hudson.plugins.notification.model.JobState;

@SuppressWarnings("rawtypes")
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
            URLConnection connection = targetUrl.openConnection();
            if (connection instanceof HttpURLConnection) {
                ((HttpURLConnection) connection)
                        .setRequestProperty("Content-Type", "application/json;charset=UTF-8");
                ((HttpURLConnection) connection)
                        .setFixedLengthStreamingMode(data.length);
            }
            connection.setDoInput(false);
            connection.setDoOutput(true);
            OutputStream output = connection.getOutputStream();
            output.write(data);
            output.flush();
            output.close();
		}

		public void validateUrl(String url) {
			try {
				new URL(url);
			} catch (MalformedURLException e) {
				throw new RuntimeException("Invalid Url: http://hostname:port/path");
			}
		}
	};

	private Gson gson = new GsonBuilder().setFieldNamingPolicy(
			FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();

	public void sendNotification(String url, Job job, Run run, Phase phase, String status) throws IOException {
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
		try {
			buildState.setFullUrl(run.getAbsoluteUrl());
		} catch (IllegalStateException ignored) {
			// Ignored
		}
		jobState.setBuild(buildState);

		ParametersAction paramsAction = run.getAction(ParametersAction.class);
		if (paramsAction != null && run instanceof AbstractBuild) {
			AbstractBuild build = (AbstractBuild) run;
			EnvVars env = new EnvVars();
			for (ParameterValue value : paramsAction.getParameters())
				if (!value.isSensitive())
					value.buildEnvVars(build, env);
			buildState.setParameters(env);
		}

		return gson.toJson(jobState).getBytes();
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
