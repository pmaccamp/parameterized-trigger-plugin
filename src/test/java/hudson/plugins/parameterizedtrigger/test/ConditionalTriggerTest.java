package hudson.plugins.parameterizedtrigger.test;

import hudson.model.BooleanParameterValue;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.Project;
import hudson.model.Run;
import hudson.model.StringParameterValue;
import hudson.plugins.parameterizedtrigger.AbstractBuildParameters;
import hudson.plugins.parameterizedtrigger.BlockableBuildTriggerConfig;
import hudson.plugins.parameterizedtrigger.BlockingBehaviour;
import hudson.plugins.parameterizedtrigger.ConditionalTriggerConfig;
import hudson.plugins.parameterizedtrigger.TriggerBuilder;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import static junit.framework.Assert.assertFalse;
import org.jvnet.hudson.test.HudsonTestCase;

/**
 *
 * @author Patrick McKeown
 */
public class ConditionalTriggerTest extends HudsonTestCase {

    private void assertLines(Run<?, ?> build, String... lines) throws IOException {
        List<String> log = build.getLog(Integer.MAX_VALUE);
        List<String> rest = log;
        for (String line : lines) {
            int where = rest.indexOf(line);
            assertFalse("Could not find line '" + line + "' among remaining log lines " + rest, where == -1);
            rest = rest.subList(where + 1, rest.size());
        }
    }

    public void testConditionalTriggerWithConditionMet() throws Exception {
        Project<?, ?> triggerProject = createFreeStyleProject("projectA");
        createFreeStyleProject("projectB");

        BlockableBuildTriggerConfig config = new BlockableBuildTriggerConfig("projectB", new ConditionalTriggerConfig("true"), new BlockingBehaviour("never", "never", "never"), Collections.<AbstractBuildParameters>emptyList());
        TriggerBuilder triggerBuilder = new TriggerBuilder(config);

        triggerProject.getBuildersList().add(triggerBuilder);

        triggerProject.scheduleBuild2(0).get();

        assertLines(triggerProject.getLastBuild(),
                "Waiting for the completion of projectB");

    }

    public void testConditionalTriggerWithConditionFailed() throws Exception {
        Project<?, ?> triggerProject = createFreeStyleProject("projectA");
        createFreeStyleProject("projectB");

        BlockableBuildTriggerConfig config = new BlockableBuildTriggerConfig("projectB", new ConditionalTriggerConfig("false"), new BlockingBehaviour("never", "never", "never"), Collections.<AbstractBuildParameters>emptyList());
        TriggerBuilder triggerBuilder = new TriggerBuilder(config);

        triggerProject.getBuildersList().add(triggerBuilder);

        triggerProject.scheduleBuild2(0).get();

        assertLines(triggerProject.getLastBuild(),
                "Skipping projectB as script condition was not met.");

    }

    public void testConditionalTriggerWithBooleanParameterSubstitution() throws Exception {
        Project<?, ?> triggerProject = createFreeStyleProject("projectA");
        createFreeStyleProject("projectB");

        BlockableBuildTriggerConfig config = new BlockableBuildTriggerConfig("projectB", new ConditionalTriggerConfig("${parameter1}"), new BlockingBehaviour("never", "never", "never"), Collections.<AbstractBuildParameters>emptyList());
        TriggerBuilder triggerBuilder = new TriggerBuilder(config);
        triggerProject.getBuildersList().add(triggerBuilder);

        triggerProject.scheduleBuild2(0, null, new ParametersAction(new BooleanParameterValue("parameter1", true))).get();

        assertLines(triggerProject.getLastBuild(),
                "Waiting for the completion of projectB");

    }

    public void testConditionalTriggerWithStringParameterSubstitution() throws Exception {
        Project<?, ?> triggerProject = createFreeStyleProject("projectA");
        createFreeStyleProject("projectB");

        BlockableBuildTriggerConfig config = new BlockableBuildTriggerConfig("projectB", new ConditionalTriggerConfig("${param 1}"), new BlockingBehaviour("never", "never", "never"), Collections.<AbstractBuildParameters>emptyList());
        TriggerBuilder triggerBuilder = new TriggerBuilder(config);
        triggerProject.getBuildersList().add(triggerBuilder);

        triggerProject.scheduleBuild2(0, null, new ParametersAction(new StringParameterValue("param 1", "true && true || false"))).get();

        assertLines(triggerProject.getLastBuild(),
                "Waiting for the completion of projectB");

    }

    public void testConditionalTriggerWithMultipleStringParameterSubstitution() throws Exception {
        Project<?, ?> triggerProject = createFreeStyleProject("projectA");
        createFreeStyleProject("projectB");

        BlockableBuildTriggerConfig config = new BlockableBuildTriggerConfig("projectB", new ConditionalTriggerConfig("$param1 || $param2"), new BlockingBehaviour("never", "never", "never"), Collections.<AbstractBuildParameters>emptyList());
        TriggerBuilder triggerBuilder = new TriggerBuilder(config);
        triggerProject.getBuildersList().add(triggerBuilder);

        List<ParameterValue> params = new ArrayList<ParameterValue>();
        params.add(new StringParameterValue("param1", "true && true || false"));
        params.add(new StringParameterValue("param2", "true && true || false"));

        triggerProject.scheduleBuild2(0, null, new ParametersAction(params)).get();

        assertLines(triggerProject.getLastBuild(),
                "Waiting for the completion of projectB");

    }

    public void testConditionalTriggerWithMultipleBracketedStringParameterSubstitution() throws Exception {
        Project<?, ?> triggerProject = createFreeStyleProject("projectA");
        createFreeStyleProject("projectB");

        BlockableBuildTriggerConfig config = new BlockableBuildTriggerConfig("projectB", new ConditionalTriggerConfig("${param 1} && ${param 2}"), new BlockingBehaviour("never", "never", "never"), Collections.<AbstractBuildParameters>emptyList());
        TriggerBuilder triggerBuilder = new TriggerBuilder(config);
        triggerProject.getBuildersList().add(triggerBuilder);

        List<ParameterValue> params = new ArrayList<ParameterValue>();
        params.add(new StringParameterValue("param 1", "true && true || false"));
        params.add(new StringParameterValue("param 2", "true && true || false"));

        triggerProject.scheduleBuild2(0, null, new ParametersAction(params)).get();

        assertLines(triggerProject.getLastBuild(),
                "Waiting for the completion of projectB");

    }

    public void testConditionalTriggerWithInvalidSubstitution() throws Exception {
        Project<?, ?> triggerProject = createFreeStyleProject("projectA");
        createFreeStyleProject("projectB");

        BlockableBuildTriggerConfig config = new BlockableBuildTriggerConfig("projectB", new ConditionalTriggerConfig("${param 1}"), new BlockingBehaviour("never", "never", "never"), Collections.<AbstractBuildParameters>emptyList());
        TriggerBuilder triggerBuilder = new TriggerBuilder(config);
        triggerProject.getBuildersList().add(triggerBuilder);

        triggerProject.scheduleBuild2(0).get();

        assertLines(triggerProject.getLastBuild(),
                "java.lang.RuntimeException: Couldn't evaluate script ${param 1} due to unsubstituted variables - param 1");

    }

    public void testConditionalTriggerWithSomeInvalidSubstitution() throws Exception {
        Project<?, ?> triggerProject = createFreeStyleProject("projectA");
        createFreeStyleProject("projectB");

        BlockableBuildTriggerConfig config = new BlockableBuildTriggerConfig("projectB", new ConditionalTriggerConfig("$param1 || $param2"), new BlockingBehaviour("never", "never", "never"), Collections.<AbstractBuildParameters>emptyList());
        TriggerBuilder triggerBuilder = new TriggerBuilder(config);
        triggerProject.getBuildersList().add(triggerBuilder);

        List<ParameterValue> params = new ArrayList<ParameterValue>();
        params.add(new StringParameterValue("param1", "false"));

        triggerProject.scheduleBuild2(0, null, new ParametersAction(params)).get();

        assertLines(triggerProject.getLastBuild(),
                "java.lang.RuntimeException: Couldn't evaluate script $param1 || $param2 due to unsubstituted variables - param2");

    }

    public void testConditionalTriggerWithNullSubstitution() throws Exception {
        Project<?, ?> triggerProject = createFreeStyleProject("projectA");
        createFreeStyleProject("projectB");

        BlockableBuildTriggerConfig config = new BlockableBuildTriggerConfig("projectB", new ConditionalTriggerConfig("${param1}.contains(\"test\")"), new BlockingBehaviour("never", "never", "never"), Collections.<AbstractBuildParameters>emptyList());
        TriggerBuilder triggerBuilder = new TriggerBuilder(config);
        triggerProject.getBuildersList().add(triggerBuilder);

        List<ParameterValue> params = new ArrayList<ParameterValue>();
        params.add(new StringParameterValue("param1", ""));

        triggerProject.scheduleBuild2(0, null, new ParametersAction(params)).get();

        assertLines(triggerProject.getLastBuild(),
                "java.lang.RuntimeException: Couldn't evaluate script ${param1}.contains(\"test\") due to unsubstituted variables - param1");
    }

    public void testConditionalTriggerWithScriptException() throws Exception {
        Project<?, ?> triggerProject = createFreeStyleProject("projectA");
        createFreeStyleProject("projectB");

        BlockableBuildTriggerConfig config = new BlockableBuildTriggerConfig("projectB", new ConditionalTriggerConfig("${param1}.sdfgsfdg(\"test\")"), new BlockingBehaviour("never", "never", "never"), Collections.<AbstractBuildParameters>emptyList());
        TriggerBuilder triggerBuilder = new TriggerBuilder(config);
        triggerProject.getBuildersList().add(triggerBuilder);

        List<ParameterValue> params = new ArrayList<ParameterValue>();
        params.add(new StringParameterValue("param1", "test"));

        triggerProject.scheduleBuild2(0, null, new ParametersAction(params)).get();

        assertLines(triggerProject.getLastBuild(),
                "java.lang.RuntimeException: Couldn't evaluate script def check (build, listener) "
                + "{ test.sdfgsfdg(\"test\") } : javax.script.ScriptException: "
                + "org.codehaus.groovy.runtime.InvokerInvocationException: groovy.lang.MissingPropertyException: "
                + "No such property: test for class: Script1");
    }
}
