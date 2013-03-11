package hudson.plugins.parameterizedtrigger;

import hudson.EnvVars;
import hudson.Extension;
import hudson.model.AbstractBuild;
import hudson.model.Descriptor;
import hudson.model.Describable;
import hudson.model.Hudson;
import hudson.model.TaskListener;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;

public class ConditionalTriggerConfig implements
		Describable<ConditionalTriggerConfig> {
	private String script;
	// search for variables of the form ${VARNAME} or $NoWhiteSpace
	private static final Pattern pattern = Pattern
			.compile("\\$\\{(.+)\\}|\\$(.+)\\s?");

	@DataBoundConstructor
	public ConditionalTriggerConfig(String script) {
		this.script = script;
	}

	public boolean isConditionMet(AbstractBuild build, TaskListener listener,
			EnvVars env) {
		// Checks for the script
		if (StringUtils.isNotBlank(script)) {
			// Engine manager
			ScriptEngineManager scriptEngineManager = new ScriptEngineManager();
			// Gets the Groovy engine
			ScriptEngine engine = scriptEngineManager.getEngineByName("groovy");
			// Creates the script and the function entry point, with all
			// variables replaced
			String function = String.format(
					"def check (build, listener) { %s }",
					replaceEnvVars(env, script));

			try {
				// Evaluates the script
				engine.eval(function);
				// Gets the function
				Invocable invocable = (Invocable) engine;
				// Invokes the function
				Boolean result = (Boolean) invocable.invokeFunction("check",
						new Object[] { build, listener });
				// Returns the result
				return result;
			} catch (Exception ex) {
				Matcher m = pattern.matcher(script);
				ArrayList<String> unsubstitutedVars = new ArrayList<String>();
				while (m.find()) {
					// If ${VARNAME} match found, return that group, else return
					// $NoWhiteSpace group
					String envVar = (m.group(1) != null) ? m.group(1) : m
							.group(2);
					unsubstitutedVars.add(envVar);
				}

				if (unsubstitutedVars.isEmpty()) {
					String message = String
							.format("Couldn't evaluate script %s.",
									script);
					throw new RuntimeException(message, ex);
				} else {
					String message = String
							.format("Couldn't evaluate script %s due to unsubstituted variables - %s",
									script, StringUtils.join(unsubstitutedVars, ","));
					throw new RuntimeException(message, ex);
				}
			}
		}
		// No script, OK
		else {
			return true;
		}
	}

	public static String replaceEnvVars(EnvVars env, String str) {
		Matcher m = pattern.matcher(str);
		while (m.find()) {
			// If ${VARNAME} match found, return that group, else return
			// $NoWhiteSpace group
			String envVar = (m.group(1) != null) ? m.group(1) : m.group(2);
			String value = env.get(envVar);
			if (value != null) {
				// Replace the full match (group 0) to remove any $ and {}
				str = str.replace(m.group(0), value);
			}
		}
		return str;
	}

	public String getScript() {
		return script;
	}

	public Descriptor<ConditionalTriggerConfig> getDescriptor() {
		return Hudson.getInstance().getDescriptorOrDie(getClass());
	}

	@Extension
	public static class DescriptorImpl extends
			Descriptor<ConditionalTriggerConfig> {
		public String getDisplayName() {
			return ""; // unused
		}
	}
}
