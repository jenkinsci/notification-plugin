package com.tikal.hudson.plugins.notification;

import static com.tikal.hudson.plugins.notification.UrlType.PUBLIC;
import static com.tikal.hudson.plugins.notification.UrlType.SECRET;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.isA;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.tikal.hudson.plugins.notification.model.JobState;
import hudson.EnvVars;
import hudson.model.Job;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.util.List;
import jenkins.model.Jenkins;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PhaseTest {
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
    private PrintStream logger;

    @Mock
    private Jenkins jenkins;

    @Test
    void testIsRun() throws ReflectiveOperationException {
        Endpoint endPoint = new Endpoint(null);
        Method isRunMethod = Phase.class.getDeclaredMethod("isRun", Endpoint.class, Result.class, Result.class);
        isRunMethod.setAccessible(true);

        assertEquals(
                Boolean.TRUE,
                isRunMethod.invoke(Phase.QUEUED, endPoint, null, null),
                "returns true for null endpoint event");

        endPoint.setEvent("all");
        for (Phase phaseValue : Phase.values()) {
            assertEquals(
                    Boolean.TRUE,
                    isRunMethod.invoke(phaseValue, endPoint, null, null),
                    "all Event returns true for Phase " + phaseValue.toString());
        }

        endPoint.setEvent("queued");
        assertEquals(
                Boolean.TRUE,
                isRunMethod.invoke(Phase.QUEUED, endPoint, null, null),
                "queued Event returns true for Phase Queued");
        assertEquals(
                Boolean.FALSE,
                isRunMethod.invoke(Phase.STARTED, endPoint, null, null),
                "queued Event returns false for Phase Started");

        endPoint.setEvent("started");
        assertEquals(
                Boolean.TRUE,
                isRunMethod.invoke(Phase.STARTED, endPoint, null, null),
                "started Event returns true for Phase Started");
        assertEquals(
                Boolean.FALSE,
                isRunMethod.invoke(Phase.COMPLETED, endPoint, null, null),
                "started Event returns false for Phase Completed");

        endPoint.setEvent("completed");
        assertEquals(
                Boolean.TRUE,
                isRunMethod.invoke(Phase.COMPLETED, endPoint, null, null),
                "completed Event returns true for Phase Completed");
        assertEquals(
                Boolean.FALSE,
                isRunMethod.invoke(Phase.FINALIZED, endPoint, null, null),
                "completed Event returns false for Phase Finalized");

        endPoint.setEvent("finalized");
        assertEquals(
                Boolean.TRUE,
                isRunMethod.invoke(Phase.FINALIZED, endPoint, null, null),
                "finalized Event returns true for Phase Finalized");
        assertEquals(
                Boolean.FALSE,
                isRunMethod.invoke(Phase.QUEUED, endPoint, null, null),
                "finalized Event returns true for Phase Queued");

        endPoint.setEvent("failed");
        assertEquals(
                Boolean.FALSE,
                isRunMethod.invoke(Phase.FINALIZED, endPoint, null, null),
                "failed Event returns false for Phase Finalized and no status");
        assertEquals(
                Boolean.FALSE,
                isRunMethod.invoke(Phase.FINALIZED, endPoint, Result.SUCCESS, null),
                "failed Event returns false for Phase Finalized and success status");
        assertEquals(
                Boolean.TRUE,
                isRunMethod.invoke(Phase.FINALIZED, endPoint, Result.FAILURE, null),
                "failed Event returns true for Phase Finalized and success failure");
        assertEquals(
                Boolean.FALSE,
                isRunMethod.invoke(Phase.COMPLETED, endPoint, Result.FAILURE, null),
                "failed Event returns false for Phase not Finalized and success failure");

        endPoint.setEvent("failedAndFirstSuccess");
        assertEquals(
                Boolean.FALSE,
                isRunMethod.invoke(Phase.FINALIZED, endPoint, null, null),
                "failedAndFirstSuccess Event returns false for Phase Finalized and no status");
        assertEquals(
                Boolean.FALSE,
                isRunMethod.invoke(Phase.FINALIZED, endPoint, Result.SUCCESS, null),
                "failedAndFirstSuccess Event returns false for Phase Finalized and no previous status");
        assertEquals(
                Boolean.TRUE,
                isRunMethod.invoke(Phase.FINALIZED, endPoint, Result.FAILURE, null),
                "failedAndFirstSuccess Event returns true for Phase Finalized and no previous status and failed status");
        assertEquals(
                Boolean.TRUE,
                isRunMethod.invoke(Phase.FINALIZED, endPoint, Result.FAILURE, Result.FAILURE),
                "failedAndFirstSuccess Event returns true for Phase Finalized and failed status");
        assertEquals(
                Boolean.TRUE,
                isRunMethod.invoke(Phase.FINALIZED, endPoint, Result.SUCCESS, Result.FAILURE),
                "failedAndFirstSuccess Event returns true for Phase Finalized and success status with previous status of failure");
        assertEquals(
                Boolean.FALSE,
                isRunMethod.invoke(Phase.FINALIZED, endPoint, Result.SUCCESS, Result.SUCCESS),
                "failedAndFirstSuccess Event returns false for Phase Finalized and success status with previous status of success");
        assertEquals(
                Boolean.FALSE,
                isRunMethod.invoke(Phase.COMPLETED, endPoint, Result.SUCCESS, Result.FAILURE),
                "failedAndFirstSuccess Event returns false for Phase not Finalized");
    }

    @Test
    void testRunNoProperty() {
        when(run.getParent()).thenReturn(job);

        Phase.STARTED.handle(run, listener, 0L);

        verify(job).getProperty(HudsonNotificationProperty.class);
        verifyNoInteractions(listener, endpoint, property);
    }

    @Test
    void testRunNoPreviousRunUrlNull() {
        when(run.getParent()).thenReturn(job);
        when(job.getProperty(HudsonNotificationProperty.class)).thenReturn(property);
        when(property.getEndpoints()).thenReturn(List.of(endpoint));
        when(endpoint.getUrlInfo()).thenReturn(urlInfo);

        Phase.STARTED.handle(run, listener, 0L);

        verify(run).getPreviousCompletedBuild();
        verifyNoInteractions(listener);
    }

    @Test
    void testRunNoPreviousRunUrlTypePublicUnresolvedUrl() throws IOException, InterruptedException {
        when(run.getParent()).thenReturn(job);
        when(job.getProperty(HudsonNotificationProperty.class)).thenReturn(property);
        when(property.getEndpoints()).thenReturn(List.of(endpoint));
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
    void testRunPreviousRunUrlTypePublic() throws IOException, InterruptedException {
        byte[] data = "data".getBytes();
        try (MockedStatic<Jenkins> jenkinsMockedStatic = mockStatic(Jenkins.class)) {
            jenkinsMockedStatic.when(Jenkins::getInstanceOrNull).thenReturn(jenkins);
            jenkinsMockedStatic.when(Jenkins::get).thenReturn(jenkins);

            Protocol httpProtocolSpy = spy(Protocol.HTTP);
            when(endpoint.getProtocol()).thenReturn(httpProtocolSpy);
            doNothing().when(httpProtocolSpy).send(anyString(), any(byte[].class), anyInt(), anyBoolean());

            Format jsonFormatSpy = spy(Format.JSON);
            JobState jobState = new JobState();
            when(endpoint.getFormat()).thenReturn(jsonFormatSpy);
            doReturn(data).when(jsonFormatSpy).serialize(isA(JobState.class));
            assertEquals(data, jsonFormatSpy.serialize(jobState));

            when(run.getParent()).thenReturn(job);
            when(job.getProperty(HudsonNotificationProperty.class)).thenReturn(property);
            when(property.getEndpoints()).thenReturn(List.of(endpoint));
            when(endpoint.getUrlInfo()).thenReturn(urlInfo);
            when(endpoint.getBranch()).thenReturn("branchName");
            when(run.getEnvironment(listener)).thenReturn(environment);
            when(urlInfo.getUrlOrId()).thenReturn("$someUrl");
            when(urlInfo.getUrlType()).thenReturn(PUBLIC);
            when(environment.expand("$someUrl")).thenReturn("expandedUrl");
            when(environment.containsKey("BRANCH_NAME")).thenReturn(true);
            when(environment.get("BRANCH_NAME")).thenReturn("branchName");
            when(listener.getLogger()).thenReturn(logger);
            when(endpoint.getTimeout()).thenReturn(42);

            Phase.STARTED.handle(run, listener, 1L);

            verify(logger).printf("Notifying endpoint with %s%n", "url 'expandedUrl'");
            verify(httpProtocolSpy).send("expandedUrl", data, 42, false);
            verify(run).getPreviousCompletedBuild();
        }
    }

    @Test
    void testRunPreviousRunUrlTypeSecret() throws IOException, InterruptedException {
        byte[] data = "data".getBytes();
        try (MockedStatic<Jenkins> jenkinsMockedStatic = mockStatic(Jenkins.class);
                MockedStatic<Utils> utilsMockedStatic = mockStatic(Utils.class)) {
            jenkinsMockedStatic.when(Jenkins::getInstanceOrNull).thenReturn(jenkins);
            jenkinsMockedStatic.when(Jenkins::get).thenReturn(jenkins);
            utilsMockedStatic
                    .when(() -> Utils.getSecretUrl("credentialsId", jenkins))
                    .thenReturn("$secretUrl");

            Protocol httpProtocolSpy = spy(Protocol.HTTP);
            when(endpoint.getProtocol()).thenReturn(httpProtocolSpy);
            doNothing().when(httpProtocolSpy).send(anyString(), any(byte[].class), anyInt(), anyBoolean());

            Format jsonFormatSpy = spy(Format.JSON);
            JobState jobState = new JobState();
            when(endpoint.getFormat()).thenReturn(jsonFormatSpy);
            doReturn(data).when(jsonFormatSpy).serialize(isA(JobState.class));
            assertEquals(data, jsonFormatSpy.serialize(jobState));

            when(run.getParent()).thenReturn(job);
            when(job.getProperty(HudsonNotificationProperty.class)).thenReturn(property);
            when(property.getEndpoints()).thenReturn(List.of(endpoint));
            when(endpoint.getUrlInfo()).thenReturn(urlInfo);
            when(endpoint.getBranch()).thenReturn(".*");
            when(run.getEnvironment(listener)).thenReturn(environment);
            when(job.getParent()).thenReturn(jenkins);
            when(urlInfo.getUrlOrId()).thenReturn("credentialsId");
            when(urlInfo.getUrlType()).thenReturn(SECRET);
            when(environment.expand("$secretUrl")).thenReturn("secretUrl");
            when(listener.getLogger()).thenReturn(logger);
            when(endpoint.getTimeout()).thenReturn(42);

            Phase.STARTED.handle(run, listener, 1L);

            verify(logger).printf("Notifying endpoint with %s%n", "credentials id 'credentialsId'");
            verify(httpProtocolSpy).send("secretUrl", data, 42, false);
            verify(run).getPreviousCompletedBuild();
        }
    }
}
