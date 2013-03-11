package hudson.plugins.parameterizedtrigger;

import hudson.EnvVars;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.BuildListener;
import hudson.model.TaskListener;
import hudson.model.Cause.UpstreamCause;
import hudson.model.Hudson;
import hudson.model.Node;
import hudson.model.Run;
import org.kohsuke.stapler.DataBoundConstructor;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ListMultimap;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Future;

/**
 * {@link BuildTriggerConfig} that supports blocking of the execution.
 * @author Kohsuke Kawaguchi
 */
public class BlockableBuildTriggerConfig extends BuildTriggerConfig {
    private final BlockingBehaviour block;
    private final ConditionalTriggerConfig conditionalTrigger;
    public boolean buildAllNodesWithLabel;

    public BlockableBuildTriggerConfig(String projects, BlockingBehaviour block, List<AbstractBuildParameters> configs) {
        super(projects, ResultCondition.ALWAYS, false, configs);
        this.block = block;
        this.conditionalTrigger = null;
    }
    
    public BlockableBuildTriggerConfig(String projects, ConditionalTriggerConfig conditionalTrigger, BlockingBehaviour block, List<AbstractBuildParameters> configs) {
        super(projects, ResultCondition.ALWAYS, false, configs);
        this.block = block;
        this.conditionalTrigger = conditionalTrigger;
    }

    @DataBoundConstructor
    public BlockableBuildTriggerConfig(String projects, ConditionalTriggerConfig conditionalTrigger, BlockingBehaviour block, List<AbstractBuildParameterFactory> configFactories,List<AbstractBuildParameters> configs) {
        super(projects, ResultCondition.ALWAYS, false, configFactories, configs);
        this.block = block;
        this.conditionalTrigger = conditionalTrigger;
    }

	public BlockingBehaviour getBlock() {
        return block;
    }
    
    public ConditionalTriggerConfig getConditionalTrigger() {
        return conditionalTrigger;
    }
    
    public boolean isConditionMet(AbstractBuild build, TaskListener listener,
			EnvVars env) {
    	// If no conditional trigger was specified, always return true.
    	if(conditionalTrigger == null)
    			return true;
    	
        return conditionalTrigger.isConditionMet(build, listener, env);
    }

	@Override
    public List<Future<AbstractBuild>> perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        List<Future<AbstractBuild>> r = super.perform(build, launcher, listener);
        if (block==null) return Collections.emptyList();
        return r;
    }

    @Override
    public ListMultimap<AbstractProject, Future<AbstractBuild>> perform2(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        ListMultimap<AbstractProject, Future<AbstractBuild>> futures = super.perform2(build, launcher, listener);
        if(block==null) return ArrayListMultimap.create();
        return futures;
    }

    @Override
    protected Future schedule(AbstractBuild<?, ?> build, AbstractProject project, List<Action> list) throws InterruptedException, IOException {
        if (block!=null) {
            while (true) {
                // if we fail to add the item to the queue, wait and retry.
                // it also means we have to force quiet period = 0, or else it'll never leave the queue
                Future f = project.scheduleBuild2(0, new UpstreamCause((Run) build), list.toArray(new Action[list.size()]));
                //when a project is disabled or the configuration is not yet saved f will always be null and we'ure caught in a loop, therefore we need to check for it
                if (f!=null || (f==null && !project.isBuildable())){
                    return f;
                }
                Thread.sleep(1000);
            }
        } else {
            return super.schedule(build,project,list);
        }
    }

    public Collection<Node> getNodes() {
        return Hudson.getInstance().getLabel("asrt").getNodes();
    }

    @Extension
    public static class DescriptorImpl extends BuildTriggerConfig.DescriptorImpl {
    }
}
