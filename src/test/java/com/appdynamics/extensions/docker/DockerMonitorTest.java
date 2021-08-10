package com.appdynamics.extensions.docker;

import com.google.common.collect.Maps;
import com.singularity.ee.agent.systemagent.api.exception.TaskExecutionException;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class DockerMonitorTest {

    private static final String CONFIG_ARGS = "config-file";
    private static final String CONFIG_FILE_PATH = "src/test/resources/conf/config.yml";

    @Test
    public void testDockerMonitor() throws TaskExecutionException {
        DockerMonitor dockerMonitor = new DockerMonitor();
        final Map<String, String> taskArgs = new HashMap<>();
        taskArgs.put(CONFIG_ARGS,CONFIG_FILE_PATH);
        dockerMonitor.execute(taskArgs,null);
    }

}
