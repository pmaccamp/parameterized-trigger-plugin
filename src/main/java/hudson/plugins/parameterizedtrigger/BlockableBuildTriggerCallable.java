package hudson.plugins.parameterizedtrigger;

import hudson.AbortException;
import hudson.EnvVars;
import hudson.Launcher;
import hudson.console.HyperlinkNote;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import com.google.common.collect.ListMultimap;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Future;

import org.acegisecurity.context.SecurityContext;
import org.acegisecurity.context.SecurityContextHolder;

/**
 * BlockableBuildTriggerCallable enables running of configurations in parallel.
 * 
 * @author Patrick McKeown
 */
public class BlockableBuildTriggerCallable implements Callable<Boolean> {
	private final BlockableBuildTriggerConfig config;
	private final AbstractBuild<?, ?> build;
	private final EnvVars env;
	private final Launcher launcher;
	private final BuildListener listener;
	private boolean buildStepResult;
	private final SecurityContext authorizedContext;

	public BlockableBuildTriggerCallable(BlockableBuildTriggerConfig config,
			AbstractBuild<?, ?> build, EnvVars env, Launcher launcher,
			BuildListener listener) {
		this.config = config;
		this.build = build;
		this.env = env;
		this.launcher = launcher;
		this.listener = listener;
		this.buildStepResult = true;
		this.authorizedContext = SecurityContextHolder.getContext(); 
	}

	public Boolean call() throws Exception {
		SecurityContext originalContent = SecurityContextHolder.getContext(); 
        SecurityContextHolder.setContext(authorizedContext);
        try {
			List<AbstractProject> projectList = config.getProjectList(build.getRootBuild().getProject().getParent(),env);
			if (config.isConditionMet(build, listener, env)) {
				ListMultimap<AbstractProject, Future<AbstractBuild>> futures = config
						.perform2(build, launcher, listener);
				if (!projectList.isEmpty()) {
					// handle non-blocking configs
					if (futures.isEmpty()) {
						listener.getLogger()
								.println(
										"Triggering projects: "
												+ config.getProjectListAsString(projectList));
					} else {
						// handle blocking configs
						for (AbstractProject p : projectList) {
							// handle non-buildable projects
							if (!p.isBuildable()) {
								listener.getLogger()
										.println(
												"Skipping "
														+ HyperlinkNote.encodeTo(
																'/' + p.getUrl(),
																p.getFullDisplayName())
														+ ". The project is either disabled or the configuration has not been saved yet.");
								continue;
							}
							for (Future<AbstractBuild> future : futures.get(p)) {
								try {
									listener.getLogger()
											.println(
													"Waiting for the completion of "
															+ HyperlinkNote.encodeTo(
																	'/' + p.getUrl(),
																	p.getFullDisplayName()));
									AbstractBuild b = future.get();
									listener.getLogger().println(
											HyperlinkNote.encodeTo(
													'/' + b.getUrl(),
													b.getFullDisplayName())
													+ " completed. Result was "
													+ b.getResult());
									build.getActions().add(
											new BuildInfoExporterAction(b
													.getProject().getFullName(), b
													.getNumber()));
	
									if (buildStepResult
											&& config.getBlock()
													.mapBuildStepResult(
															b.getResult())) {
										build.setResult(config.getBlock()
												.mapBuildResult(b.getResult()));
									} else {
										buildStepResult = false;
									}
								} catch (CancellationException x) {
									throw new AbortException(p.getFullDisplayName()
											+ " aborted.");
								}
							}
						}
					}
				} else {
					throw new AbortException(
							"Build aborted. No projects to trigger. Check your configuration!");
				}
			} else {
				listener.getLogger().println(
						"Skipping " + config.getProjectListAsString(projectList)
								+ " as script condition was not met.");
			}
        }
        finally { 
            SecurityContextHolder.setContext(originalContent); 
        } 
		return buildStepResult;
	}

}
