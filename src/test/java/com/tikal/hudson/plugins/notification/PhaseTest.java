package com.tikal.hudson.plugins.notification;

import static com.tikal.hudson.plugins.notification.UrlType.PUBLIC;
import static com.tikal.hudson.plugins.notification.UrlType.SECRET;
import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.Method;

import com.tikal.hudson.plugins.notification.model.JobState;
import hudson.EnvVars;
import hudson.model.*;
import jenkins.model.Jenkins;
import org.junit.Test;

import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class PhaseTest {
  @Mock
  private Run run;
  @Mock
  private Job job;
  @Mock
  private TaskListener listener;
  @Mock
  private HudsonNotificationProperty property;
  @Mock
  private Endpoint endpoint;
  @Mock
  private UrlInfo urlInfo;
  @Mock
  private EnvVars environment;
  @Mock
  private Protocol protocol;
  @Mock
  private Format format;
  @Mock
  private PrintStream logger;
  @Mock
  private Jenkins jenkins;

  @Test
  public void testIsRun() throws ReflectiveOperationException {
    Endpoint endPoint = new Endpoint(null);
    Method isRunMethod = Phase.class.getDeclaredMethod("isRun", Endpoint.class, Result.class, Result.class);
    isRunMethod.setAccessible(true);

    assertEquals("returns true for null endpoint event",
            isRunMethod.invoke(Phase.QUEUED, endPoint, null, null), Boolean.TRUE);

    endPoint.setEvent("all");
    for (Phase phaseValue : Phase.values()) {
      assertEquals("all Event returns true for Phase " + phaseValue.toString(),
              isRunMethod.invoke(phaseValue, endPoint, null, null), Boolean.TRUE);
    }

    endPoint.setEvent("queued");
    assertEquals("queued Event returns true for Phase Queued",
            isRunMethod.invoke(Phase.QUEUED, endPoint, null, null), Boolean.TRUE);
    assertEquals("queued Event returns false for Phase Started",
            isRunMethod.invoke(Phase.STARTED, endPoint, null, null), Boolean.FALSE);

    endPoint.setEvent("started");
    assertEquals("started Event returns true for Phase Started",
            isRunMethod.invoke(Phase.STARTED, endPoint, null, null), Boolean.TRUE);
    assertEquals("started Event returns false for Phase Completed",
            isRunMethod.invoke(Phase.COMPLETED, endPoint, null, null), Boolean.FALSE);


    endPoint.setEvent("completed");
    assertEquals("completed Event returns true for Phase Completed",
            isRunMethod.invoke(Phase.COMPLETED, endPoint, null, null), Boolean.TRUE);
    assertEquals("completed Event returns false for Phase Finalized",
            isRunMethod.invoke(Phase.FINALIZED, endPoint, null, null), Boolean.FALSE);


    endPoint.setEvent("finalized");
    assertEquals("finalized Event returns true for Phase Finalized",
            isRunMethod.invoke(Phase.FINALIZED, endPoint, null, null), Boolean.TRUE);
    assertEquals("finalized Event returns true for Phase Queued",
            isRunMethod.invoke(Phase.QUEUED, endPoint, null, null), Boolean.FALSE);


    endPoint.setEvent("failed");
    assertEquals("failed Event returns false for Phase Finalized and no status",
            isRunMethod.invoke(Phase.FINALIZED, endPoint, null, null), Boolean.FALSE);
    assertEquals("failed Event returns false for Phase Finalized and success status",
            isRunMethod.invoke(Phase.FINALIZED, endPoint, Result.SUCCESS, null), Boolean.FALSE);
    assertEquals("failed Event returns true for Phase Finalized and success failure",
            isRunMethod.invoke(Phase.FINALIZED, endPoint, Result.FAILURE, null), Boolean.TRUE);
    assertEquals("failed Event returns false for Phase not Finalized and success failure",
            isRunMethod.invoke(Phase.COMPLETED, endPoint, Result.FAILURE, null), Boolean.FALSE);

    endPoint.setEvent("failedAndFirstSuccess");
    assertEquals("failedAndFirstSuccess Event returns false for Phase Finalized and no status",
            isRunMethod.invoke(Phase.FINALIZED, endPoint, null, null), Boolean.FALSE);
    assertEquals("failedAndFirstSuccess Event returns false for Phase Finalized and no previous status",
            isRunMethod.invoke(Phase.FINALIZED, endPoint, Result.SUCCESS, null), Boolean.FALSE);
    assertEquals("failedAndFirstSuccess Event returns true for Phase Finalized and no previous status and failed status",
            isRunMethod.invoke(Phase.FINALIZED, endPoint, Result.FAILURE, null), Boolean.TRUE);
    assertEquals("failedAndFirstSuccess Event returns true for Phase Finalized and failed status",
            isRunMethod.invoke(Phase.FINALIZED, endPoint, Result.FAILURE, Result.FAILURE), Boolean.TRUE);
    assertEquals("failedAndFirstSuccess Event returns true for Phase Finalized and success status with previous status of failure",
            isRunMethod.invoke(Phase.FINALIZED, endPoint, Result.SUCCESS, Result.FAILURE), Boolean.TRUE);
    assertEquals("failedAndFirstSuccess Event returns false for Phase Finalized and success status with previous status of success",
            isRunMethod.invoke(Phase.FINALIZED, endPoint, Result.SUCCESS, Result.SUCCESS), Boolean.FALSE);
    assertEquals("failedAndFirstSuccess Event returns false for Phase not Finalized",
            isRunMethod.invoke(Phase.COMPLETED, endPoint, Result.SUCCESS, Result.FAILURE), Boolean.FALSE);
  }

  @Test
  public void testRunNoProperty() {
    when(run.getParent()).thenReturn(job);

    Phase.STARTED.handle(run, listener, 0L);

    verify(job).getProperty(HudsonNotificationProperty.class);
    verifyNoInteractions(listener, endpoint, property);
  }

  @Test
  public void testRunNoPreviousRunUrlNull() {
    when(run.getParent()).thenReturn(job);
    when(job.getProperty(HudsonNotificationProperty.class)).thenReturn(property);
    when(property.getEndpoints()).thenReturn(asList(endpoint));
    when(endpoint.getUrlInfo()).thenReturn(urlInfo);

    Phase.STARTED.handle(run, listener, 0L);

    verify(run).getPreviousCompletedBuild();
    verifyNoInteractions(listener);
  }

  @Test
  public void testRunNoPreviousRunUrlTypePublicUnresolvedUrl() throws IOException, InterruptedException {
    when(run.getParent()).thenReturn(job);
    when(job.getProperty(HudsonNotificationProperty.class)).thenReturn(property);
    when(property.getEndpoints()).thenReturn(asList(endpoint));
    when(endpoint.getUrlInfo()).thenReturn(urlInfo);
    when(run.getEnvironment(listener)).thenReturn(environment);
    when(urlInfo.getUrlOrId()).thenReturn("$someUrl");
    when(urlInfo.getUrlType()).thenReturn(PUBLIC);
    when(environment.expand("$someUrl")).thenReturn("$someUrl");
    when(listener.getLogger()).thenReturn(logger);

    Phase.STARTED.handle(run, listener, 0L);

    verify(logger).printf("Ignoring sending notification due to unresolved variable: %s%n", "url '$someUrl'");
    verify(run).getPreviousCompletedBuild();
  }

  @Test
  public void testRunPreviousRunUrlTypePublic() throws IOException, InterruptedException {
    byte[] data = "data".getBytes();
    try (MockedStatic<Jenkins> jenkinsMockedStatic = mockStatic(Jenkins.class)) {
      jenkinsMockedStatic.when(Jenkins::getInstanceOrNull).thenReturn(jenkins);
      jenkinsMockedStatic.when(Jenkins::getInstance).thenReturn(jenkins);

      when(run.getParent()).thenReturn(job);
      when(job.getProperty(HudsonNotificationProperty.class)).thenReturn(property);
      when(property.getEndpoints()).thenReturn(asList(endpoint));
      when(endpoint.getUrlInfo()).thenReturn(urlInfo);
      when(endpoint.getBranch()).thenReturn("branchName");
      when(run.getEnvironment(listener)).thenReturn(environment);
      when(urlInfo.getUrlOrId()).thenReturn("$someUrl");
      when(urlInfo.getUrlType()).thenReturn(PUBLIC);
      when(environment.expand("$someUrl")).thenReturn("expandedUrl");
      when(environment.containsKey("BRANCH_NAME")).thenReturn(true);
      when(environment.get("BRANCH_NAME")).thenReturn("branchName");
      when(listener.getLogger()).thenReturn(logger);
      when(endpoint.getProtocol()).thenReturn(protocol);
      when(endpoint.getTimeout()).thenReturn(42);
      when(endpoint.getFormat()).thenReturn(format);
      when(format.serialize(isA(JobState.class))).thenReturn(data);

      Phase.STARTED.handle(run, listener, 1L);

      verify(logger).printf("Notifying endpoint with %s%n", "url 'expandedUrl'");
      verify(protocol).send("expandedUrl", data, 42, false);
      verify(run).getPreviousCompletedBuild();
    }
  }

  @Test
  public void testRunPreviousRunUrlTypeSecret() throws IOException, InterruptedException {
    byte[] data = "data".getBytes();
    try (MockedStatic<Jenkins> jenkinsMockedStatic = mockStatic(Jenkins.class);
         MockedStatic<Utils> utilsMockedStatic = mockStatic(Utils.class)) {
      jenkinsMockedStatic.when(Jenkins::getInstanceOrNull).thenReturn(jenkins);
      jenkinsMockedStatic.when(Jenkins::getInstance).thenReturn(jenkins);
      utilsMockedStatic.when(() -> Utils.getSecretUrl("credentialsId", jenkins)).thenReturn("$secretUrl");

      when(run.getParent()).thenReturn(job);
      when(job.getProperty(HudsonNotificationProperty.class)).thenReturn(property);
      when(property.getEndpoints()).thenReturn(asList(endpoint));
      when(endpoint.getUrlInfo()).thenReturn(urlInfo);
      when(endpoint.getBranch()).thenReturn(".*");
      when(run.getEnvironment(listener)).thenReturn(environment);
      when(job.getParent()).thenReturn(jenkins);
      when(urlInfo.getUrlOrId()).thenReturn("credentialsId");
      when(urlInfo.getUrlType()).thenReturn(SECRET);
      when(environment.expand("$secretUrl")).thenReturn("secretUrl");
      when(listener.getLogger()).thenReturn(logger);
      when(endpoint.getProtocol()).thenReturn(protocol);
      when(endpoint.getTimeout()).thenReturn(42);
      when(endpoint.getFormat()).thenReturn(format);
      when(format.serialize(isA(JobState.class))).thenReturn(data);

      Phase.STARTED.handle(run, listener, 1L);

      verify(logger).printf( "Notifying endpoint with %s%n","credentials id 'credentialsId'");
      verify(protocol).send("secretUrl", data, 42, false);
      verify(run).getPreviousCompletedBuild();
    }
  }
}
