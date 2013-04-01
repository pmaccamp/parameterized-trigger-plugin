/*
 * The MIT License
 *
 * Copyright (c) 2010-2011, InfraDNA, Inc., Manufacture Francaise des Pneumatiques Michelin,
 * Romain Seguy
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

package hudson.plugins.parameterizedtrigger;

import hudson.EnvVars;
import hudson.Extension;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.DependecyDeclarer;
import hudson.model.DependencyGraph;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Builder;
import hudson.model.Action;
import hudson.model.TaskListener;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * {@link Builder} that triggers other projects and optionally waits for their
 * completion.
 * 
 * @author Kohsuke Kawaguchi
 */
public class TriggerBuilder extends Builder implements DependecyDeclarer {

	private final ArrayList<BlockableBuildTriggerConfig> configs;
	private final int NUM_THREADS = 10;

	@DataBoundConstructor
	public TriggerBuilder(List<BlockableBuildTriggerConfig> configs) {
		this.configs = new ArrayList<BlockableBuildTriggerConfig>(
				Util.fixNull(configs));
	}

	public TriggerBuilder(BlockableBuildTriggerConfig... configs) {
		this(Arrays.asList(configs));
	}

	public List<BlockableBuildTriggerConfig> getConfigs() {
		return configs;
	}

	@Override
	public BuildStepMonitor getRequiredMonitorService() {
		return BuildStepMonitor.NONE;
	}

	@Override
	public boolean perform(AbstractBuild<?, ?> build, Launcher launcher,
			BuildListener listener) throws InterruptedException, IOException {
		EnvVars env = build.getEnvironment(listener);
		env.overrideAll(build.getBuildVariables());

		boolean result = true;
		ExecutorService executor = Executors.newFixedThreadPool(NUM_THREADS);
		CompletionService<Boolean> ecs = new ExecutorCompletionService<Boolean>(
				executor);
		for (BlockableBuildTriggerConfig config : configs) {
			ecs.submit(new BlockableBuildTriggerCallable(config, build, env,
					launcher, listener));
		}

		for (int i = 0; i < configs.size(); i++) {
				Boolean configResult;
				try {
					configResult = ecs.take().get();
				} catch (ExecutionException e) {
					// TODO Auto-generated catch block
					listener.getLogger().println(e.getMessage());
					configResult = false;
				}
				// If any configs fail, set the overall result to false
				result = result && configResult;

		}

		return result;
	}

	public void buildDependencyGraph(AbstractProject owner,
			DependencyGraph graph) {
		for (BuildTriggerConfig config : configs)
			for (AbstractProject project : config.getProjectList(
					owner.getParent(), null))
				graph.addDependency(new ParameterizedDependency(owner, project,
						config) {
					@Override
					public boolean shouldTriggerBuild(AbstractBuild build,
							TaskListener listener, List<Action> actions) {
						// TriggerBuilders are inline already.
						return false;
					}
				});

	}

	@Extension
	public static class DescriptorImpl extends BuildStepDescriptor<Builder> {
		@Override
		public String getDisplayName() {
			return "Trigger/call builds on other projects";
		}

		@Override
		public boolean isApplicable(Class<? extends AbstractProject> jobType) {
			return true;
		}
	}
}
