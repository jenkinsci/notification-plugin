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
import hudson.EnvVars;
import hudson.model.*;
import jenkins.model.Jenkins;

import java.io.IOException;
import java.util.List;


@SuppressWarnings({ "unchecked", "rawtypes" })
public enum Phase {
    STARTED, COMPLETED, FINALIZED;

    @SuppressWarnings( "CastToConcreteClass" )
    public void handle(Run run, TaskListener listener) {

        HudsonNotificationProperty property = (HudsonNotificationProperty) run.getParent().getProperty(HudsonNotificationProperty.class);
        if ( property == null ){ return; }
        
        for ( Endpoint target : property.getEndpoints()) {
            if ( isRun( target )) {
                listener.getLogger().println( String.format( "Notifying endpoint '%s'", target ));

                try {
                    JobState jobState = buildJobState(run.getParent(), run, listener, target);
                    EnvVars environment = run.getEnvironment(listener);
                    String expandedUrl = environment.expand(target.getUrl());
                    target.getProtocol().send(expandedUrl,
                                              target.getFormat().serialize(jobState),
                                              target.getTimeout(),
                                              target.isJson());
                } catch (Throwable error) {
                    error.printStackTrace( listener.error( String.format( "Failed to notify endpoint '%s'", target )));
                    listener.getLogger().println( String.format( "Failed to notify endpoint '%s' - %s: %s",
                                                                 target, error.getClass().getName(), error.getMessage()));
                }
            }
        }
    }


    /**
     * Determines if the endpoint specified should be notified at the current job phase.
     */
    private boolean isRun( Endpoint endpoint ) {
        String event = endpoint.getEvent();
        return (( event == null ) || event.equals( "all" ) || event.equals( this.toString().toLowerCase()));
    }

    private JobState buildJobState(Job job, Run run, TaskListener listener, Endpoint target)
        throws IOException, InterruptedException
    {

        Jenkins            jenkins      = Jenkins.getInstance();
        String             rootUrl      = jenkins.getRootUrl();
        JobState           jobState     = new JobState();
        BuildState         buildState   = new BuildState();
        ScmState           scmState     = new ScmState();
        Result             result       = run.getResult();
        ParametersAction   paramsAction = run.getAction(ParametersAction.class);
        EnvVars            environment  = run.getEnvironment( listener );
        StringBuilder      log          = this.getLog(run, target);

        jobState.setName( job.getName());
        jobState.setUrl( job.getUrl());
        jobState.setBuild( buildState );

        buildState.setNumber( run.number );
        buildState.setQueueId( run.getQueueId() );
        buildState.setUrl( run.getUrl());
        buildState.setPhase( this );
        buildState.setScm( scmState );
        buildState.setLog( log );
        buildState.setTimestamp( run.getTimestamp() );

        if ( result != null ) {
            buildState.setStatus(result.toString());
            buildState.setDuration( run.getDuration() );
        }

        if ( rootUrl != null ) {
            buildState.setFullUrl(rootUrl + run.getUrl());
        }

        buildState.updateArtifacts( job, run );

        if ( paramsAction != null ) {
            EnvVars env = new EnvVars();
            for (ParameterValue value : paramsAction.getParameters()){
                if ( ! value.isSensitive()) {
                    value.buildEnvironment( run, env );
                }
            }
            buildState.setParameters(env);
        }

        if ( environment.get( "GIT_URL" ) != null ) {
            scmState.setUrl( environment.get( "GIT_URL" ));
        }

        if ( environment.get( "GIT_BRANCH" ) != null ) {
            scmState.setBranch( environment.get( "GIT_BRANCH" ));
        }

        if ( environment.get( "GIT_COMMIT" ) != null ) {
            scmState.setCommit( environment.get( "GIT_COMMIT" ));
        }

        return jobState;
    }

    private StringBuilder getLog(Run run, Endpoint target) {
        StringBuilder log = new StringBuilder("");
        Integer loglines = target.getLoglines();

        if (null == loglines) {
                return log;
        }

        try {
            switch (loglines) {
                // The full log
                case -1:
                    log.append(run.getLog());
                    break;
                default:
                    List<String> logEntries = run.getLog(loglines);
                    for (String entry: logEntries) {
                        log.append(entry);
                        log.append("\n");
                    }
            }
        } catch (IOException e) {
            log.append("Unable to retrieve log");
        }
        return log;
    }
}
