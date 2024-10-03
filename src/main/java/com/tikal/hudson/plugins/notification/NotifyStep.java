package com.tikal.hudson.plugins.notification;

import hudson.Extension;
import hudson.FilePath;
import hudson.Util;
import hudson.model.Run;
import hudson.model.TaskListener;
import java.io.Serializable;
import java.util.Objects;
import java.util.Set;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

public class NotifyStep extends Step implements Serializable {
    private static final long serialVersionUID = -2818860651754465006L;

    @CheckForNull
    private String notes;

    @CheckForNull
    public String getNotes() {
        return notes;
    }

    @DataBoundSetter
    public void setNotes(@CheckForNull String notes) {
        this.notes = Util.fixEmpty(notes);
    }

    @CheckForNull
    private String phase = Phase.STARTED.name();

    @CheckForNull
    public String getPhase() {
        return phase;
    }

    @DataBoundSetter
    public void setPhase(@CheckForNull String phase) {
        this.phase = Util.fixEmpty(phase);
    }

    @CheckForNull
    private String loglines = "0";

    @CheckForNull
    public String getLoglines() {
        return loglines;
    }

    @DataBoundSetter
    public void setLoglines(@CheckForNull String loglines) {
        this.loglines = Util.fixEmpty(loglines);
    }

    /**
     * Creates a new instance of {@link NotifyStep}.
     */
    @DataBoundConstructor
    public NotifyStep() {
        super();

        // empty constructor required for Stapler
    }

    @Override
    public StepExecution start(final StepContext context) {
        return new Execution(context, this);
    }

    /**
     * Actually performs the execution of the associated step.
     */
    static class Execution extends StepExecution {
        private static final long serialVersionUID = -2840020502160375407L;

        private final NotifyStep notifyStep;

        Execution(@Nonnull final StepContext context, final NotifyStep step) {
            super(context);
            notifyStep = step;
        }

        @Override
        public boolean start() throws Exception {
            String logLines = notifyStep.getLoglines();

            Phase.NONE.handle(
                    Objects.requireNonNull(getContext().get(Run.class)),
                    getContext().get(TaskListener.class),
                    System.currentTimeMillis(),
                    true,
                    notifyStep.getNotes(),
                    Integer.parseInt(logLines != null ? logLines : "0"),
                    Phase.valueOf(notifyStep.getPhase()));

            getContext().onSuccess(null);

            return true;
        }
    }

    /**
     * Descriptor for this step: defines the context and the UI labels.
     */
    @Extension
    @SuppressWarnings("unused") // most methods are used by the corresponding jelly view
    public static class Descriptor extends StepDescriptor {
        @Override
        public String getFunctionName() {
            return "notifyEndpoints";
        }

        @Nonnull
        @Override
        public String getDisplayName() {
            return Messages.Notify_DisplayName();
        }

        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return Set.of(FilePath.class, FlowNode.class, Run.class, TaskListener.class);
        }
    }
}
