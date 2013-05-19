package hudson.plugins.parameterizedtrigger;

import hudson.model.Hudson;
import hudson.slaves.EnvironmentVariablesNodeProperty;
import hudson.slaves.NodeProperty;

import java.util.HashMap;
import java.util.Map;

public class GlobalNodeProperties {

    private static final Map<String, String> NODE_PROPERTIES = new HashMap<String, String>();

    private static void updateGlobalNodeProperties() {
        //clear current properties
        NODE_PROPERTIES.clear();
        //get latest global properties
        for (NodeProperty<?> nodeProperty : Hudson.getInstance()
                .getGlobalNodeProperties()) {
            if (nodeProperty instanceof EnvironmentVariablesNodeProperty) {
                NODE_PROPERTIES.putAll(((EnvironmentVariablesNodeProperty) nodeProperty).getEnvVars());
            }
        }
    }

    public static Map<String, String> getProperties() {
        updateGlobalNodeProperties();
        return NODE_PROPERTIES;
    }

    public static String getValue(String key) {
        updateGlobalNodeProperties();
        if (key == null) {
            return null;
        } else {
            return NODE_PROPERTIES.get(key);
        }
    }
}