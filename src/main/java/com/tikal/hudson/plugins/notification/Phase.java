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

import com.tikal.hudson.plugins.notification.model.BuildState;
import com.tikal.hudson.plugins.notification.model.JobState;
import com.tikal.hudson.plugins.notification.model.ScmState;
import com.tikal.hudson.plugins.notification.model.TestState;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.model.AbstractBuild;
import hudson.model.Executor;
import hudson.model.Job;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.User;
import hudson.plugins.git.util.Build;
import hudson.plugins.git.util.BuildData;
import hudson.scm.ChangeLogSet;
import hudson.tasks.test.AbstractTestResultAction;
import hudson.tasks.test.TestResult;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.tokenmacro.TokenMacro;

@SuppressWarnings({"unchecked", "rawtypes"})
public enum Phase {
    QUEUED,
    STARTED,
    COMPLETED,
    FINALIZED,
    NONE;

    private Result findLastBuildThatFinished(Run run) {
        Run previousRun = run.getPreviousCompletedBuild();
        while (previousRun != null) {
            Result previousResults = previousRun.getResult();
            if (previousResults == null) {
                throw new IllegalStateException("Previous result can't be null here");
            }
            if (previousResults.equals(Result.SUCCESS)
                    || previousResults.equals(Result.FAILURE)
                    || previousResults.equals(Result.UNSTABLE)) {
                return previousResults;
            }
            previousRun = previousRun.getPreviousCompletedBuild();
        }
        return null;
    }

    public void handle(Run run, TaskListener listener, long timestamp) {
        handle(run, listener, timestamp, false, null, 0, this);
    }

    /**
     * Determines if input value for URL is valid. Valid values are not blank, and variables resolve/expand into valid URLs.
     * Unresolved variables remain as strings prefixed with $, so those are not valid.
     * @param urlInputValue Value user provided in input box for URL
     * @param expandedUrl Value the urlInputValue  'expands' into.
     * @param logger PrintStream used for logging.
     * @return True if URL is populated with a non-blank value, or a variable that expands into a URL.
     */
    private boolean isURLValid(String urlInputValue, String expandedUrl, PrintStream logger) {
        boolean isValid = false;
        // If Jenkins variable was used for URL, and it was unresolvable, log warning and return.
        if (expandedUrl.contains("$")) {
            logger.printf("Ignoring sending notification due to unresolved variable: %s%n", urlInputValue);
        } else if (StringUtils.isBlank(expandedUrl)) {
            logger.println("URL is not set, ignoring call to send notification.");
        } else {
            isValid = true;
        }
        return isValid;
    }

    /**
     * Determines if the endpoint specified should be notified at the current job phase.
     */
    private boolean isRun(Endpoint endpoint, Result result, Result previousRunResult) {
        String event = endpoint.getEvent();

        if (event == null) {
            return true;
        }

        switch (event) {
            case "all":
                return true;
            case "failed":
                if (result == null) {
                    return false;
                }
                return this.equals(FINALIZED) && result.equals(Result.FAILURE);
            case "failedAndFirstSuccess":
                if (result == null || !this.equals(FINALIZED)) {
                    return false;
                }
                if (result.equals(Result.FAILURE)) {
                    return true;
                }
                return previousRunResult != null
                        && result.equals(Result.SUCCESS)
                        && previousRunResult.equals(Result.FAILURE);
            case "manual":
                return false;
            default:
                return event.equals(this.toString().toLowerCase());
        }
    }

    private JobState buildJobState(
            Job job, Run run, TaskListener listener, long timestamp, Endpoint target, Phase phase)
            throws IOException, InterruptedException {
        Jenkins jenkins = Jenkins.getInstanceOrNull();
        assert jenkins != null;

        String rootUrl = jenkins.getRootUrl();
        JobState jobState = new JobState();
        BuildState buildState = new BuildState();
        ScmState scmState = new ScmState();
        Result result = run.getResult();
        ParametersAction paramsAction = run.getAction(ParametersAction.class);
        EnvVars environment = run.getEnvironment(listener);
        StringBuilder log = this.getLog(run, target);

        jobState.setName(job.getName());
        jobState.setDisplayName(job.getDisplayName());
        jobState.setUrl(job.getUrl());
        jobState.setBuild(buildState);

        buildState.setNumber(run.number);
        buildState.setQueueId(run.getQueueId());
        buildState.setUrl(run.getUrl());
        buildState.setPhase(phase);
        buildState.setTimestamp(timestamp);
        buildState.setDuration(run.getDuration());
        buildState.setScm(scmState);
        buildState.setLog(log);
        buildState.setNotes(resolveMacros(run, listener, target.getBuildNotes()));
        buildState.setTestSummary(getTestResults(run));

        if (result != null) {
            buildState.setStatus(result.toString());
        }

        if (rootUrl != null) {
            buildState.setFullUrl(rootUrl + run.getUrl());
        }

        buildState.updateArtifacts(job, run);

        // TODO: Make this optional to reduce chat overload.
        if (paramsAction != null) {
            EnvVars env = new EnvVars();
            for (ParameterValue value : paramsAction.getParameters()) {
                if (!value.isSensitive()) {
                    value.buildEnvironment(run, env);
                }
            }
            buildState.setParameters(env);
        }

        BuildData build = job.getAction(BuildData.class);

        if (build != null) {
            if (!build.remoteUrls.isEmpty()) {
                String url = build.remoteUrls.iterator().next();
                if (url != null) {
                    scmState.setUrl(url);
                }
            }
            for (Map.Entry<String, Build> entry : build.buildsByBranchName.entrySet()) {
                if (entry.getValue().hudsonBuildNumber == run.number) {
                    scmState.setBranch(entry.getKey());
                    scmState.setCommit(entry.getValue().revision.getSha1String());
                }
            }
        }

        if (environment.get("GIT_URL") != null) {
            scmState.setUrl(environment.get("GIT_URL"));
        }

        if (environment.get("GIT_BRANCH") != null) {
            scmState.setBranch(environment.get("GIT_BRANCH"));
        }

        if (environment.get("GIT_COMMIT") != null) {
            scmState.setCommit(environment.get("GIT_COMMIT"));
        }

        scmState.setChanges(getChangedFiles(run));
        scmState.setCulprits(getCulprits(run));

        return jobState;
    }

    private String resolveMacros(Run build, TaskListener listener, String text) {

        String result = text;
        try {
            Executor executor = build.getExecutor();
            if (executor != null) {
                FilePath workspace = executor.getCurrentWorkspace();
                if (workspace != null) {
                    result = TokenMacro.expandAll(build, workspace, listener, text);
                }
            }
        } catch (Throwable e) {
            // Catching Throwable here because the TokenMacro plugin is optional
            // so will throw a ClassDefNotFoundError if the plugin is not installed or disabled.
            e.printStackTrace(listener.error(String.format("Failed to evaluate macro '%s'", text)));
        }

        return result;
    }

    private TestState getTestResults(Run build) {
        TestState resultSummary = null;

        AbstractTestResultAction testAction = build.getAction(AbstractTestResultAction.class);
        if (testAction != null) {
            int total = testAction.getTotalCount();
            int failCount = testAction.getFailCount();
            int skipCount = testAction.getSkipCount();

            resultSummary = new TestState();
            resultSummary.setTotal(total);
            resultSummary.setFailed(failCount);
            resultSummary.setSkipped(skipCount);
            resultSummary.setPassed(total - failCount - skipCount);
            resultSummary.setFailedTests(getFailedTestNames(testAction));
        }

        return resultSummary;
    }

    private List<String> getFailedTestNames(AbstractTestResultAction testResultAction) {
        List<String> failedTests = new ArrayList<>();

        List<? extends TestResult> results = testResultAction.getFailedTests();

        for (TestResult t : results) {
            failedTests.add(t.getFullName());
        }

        return failedTests;
    }

    private List<String> getChangedFiles(Run run) {
        List<String> affectedPaths = new ArrayList<>();

        if (run instanceof AbstractBuild) {
            AbstractBuild build = (AbstractBuild) run;

            Object[] items = build.getChangeSet().getItems();

            if (items != null && items.length > 0) {
                for (Object o : items) {
                    if (o instanceof ChangeLogSet.Entry) {
                        affectedPaths.addAll(((ChangeLogSet.Entry) o).getAffectedPaths());
                    }
                }
            }
        }

        return affectedPaths;
    }

    private List<String> getCulprits(Run run) {
        List<String> culprits = new ArrayList<>();

        if (run instanceof AbstractBuild) {
            AbstractBuild build = (AbstractBuild) run;
            Set<User> buildCulprits = build.getCulprits();
            for (User user : buildCulprits) {
                culprits.add(user.getId());
            }
        }

        return culprits;
    }

    private StringBuilder getLog(Run run, Endpoint target) {
        StringBuilder log = new StringBuilder();
        Integer loglines = target.getLoglines();

        if (loglines == null || loglines == 0) {
            return log;
        }

        try {
            // The full log
            if (loglines == -1) {
                log.append(run.getLog());
            } else {
                List<String> logEntries = run.getLog(loglines);
                for (String entry : logEntries) {
                    log.append(entry);
                    log.append("\n");
                }
            }
        } catch (IOException e) {
            log.append("Unable to retrieve log");
        }
        return log;
    }

    public void handle(
            Run run,
            TaskListener listener,
            long timestamp,
            boolean manual,
            final String buildNotes,
            final Integer logLines,
            Phase phase) {
        final Job job = run.getParent();
        final HudsonNotificationProperty property =
                (HudsonNotificationProperty) job.getProperty(HudsonNotificationProperty.class);
        if (property == null) {
            return;
        }

        Result previousCompletedRunResults = findLastBuildThatFinished(run);

        for (Endpoint target : property.getEndpoints()) {
            if ((!manual && !isRun(target, run.getResult(), previousCompletedRunResults))
                    || Utils.isEmpty(target.getUrlInfo().getUrlOrId())) {
                continue;
            }

            if (Objects.nonNull(buildNotes)) {
                target.setBuildNotes(buildNotes);
            }

            if (Objects.nonNull(logLines) && logLines != 0) {
                target.setLoglines(logLines);
            }

            int triesRemaining = target.getRetries();
            boolean failed = false;
            do {
                // Represents a string that will be put into the log
                // if there is an error contacting the target.
                String urlIdString = "url 'unknown'";
                try {
                    EnvVars environment = run.getEnvironment(listener);
                    // Expand out the URL from environment + url.
                    String expandedUrl;
                    UrlInfo urlInfo = target.getUrlInfo();
                    switch (urlInfo.getUrlType()) {
                        case PUBLIC:
                            expandedUrl = environment.expand(urlInfo.getUrlOrId());
                            urlIdString = String.format("url '%s'", expandedUrl);
                            break;
                        case SECRET:
                            String urlSecretId = urlInfo.getUrlOrId();
                            String actualUrl = Utils.getSecretUrl(urlSecretId, job.getParent());
                            expandedUrl = environment.expand(actualUrl);
                            urlIdString = String.format("credentials id '%s'", urlSecretId);
                            break;
                        default:
                            throw new UnsupportedOperationException("Unknown URL type");
                    }

                    if (!isURLValid(urlIdString, expandedUrl, listener.getLogger())) {
                        continue;
                    }

                    final String branch = target.getBranch();
                    if (!manual
                            && environment.containsKey("BRANCH_NAME")
                            && !environment.get("BRANCH_NAME").matches(branch)) {
                        listener.getLogger()
                                .printf(
                                        "Environment variable %s with value %s does not match configured branch filter %s%n",
                                        "BRANCH_NAME", environment.get("BRANCH_NAME"), branch);
                        continue;
                    } else if (!manual && !environment.containsKey("BRANCH_NAME") && !".*".equals(branch)) {
                        listener.getLogger().printf("Environment does not contain %s variable%n", "BRANCH_NAME");
                        continue;
                    }

                    listener.getLogger().printf("Notifying endpoint with %s%n", urlIdString);
                    JobState jobState = buildJobState(job, run, listener, timestamp, target, phase);
                    target.getProtocol()
                            .send(
                                    expandedUrl,
                                    target.getFormat().serialize(jobState),
                                    target.getTimeout(),
                                    target.isJson());
                } catch (Throwable error) {
                    failed = true;
                    error.printStackTrace(
                            listener.error(String.format("Failed to notify endpoint with %s", urlIdString)));
                    listener.getLogger()
                            .printf(
                                    "Failed to notify endpoint with %s - %s: %s%n",
                                    urlIdString, error.getClass().getName(), error.getMessage());
                    if (triesRemaining > 0) {
                        listener.getLogger()
                                .printf(
                                        "Reattempting to notify endpoint with %s (%d tries remaining)%n",
                                        urlIdString, triesRemaining);
                    }
                }
            } while (failed && --triesRemaining >= 0);
        }
    }
}
