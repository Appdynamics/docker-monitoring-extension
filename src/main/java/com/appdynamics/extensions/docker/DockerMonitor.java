package com.appdynamics.extensions.docker;

import com.appdynamics.extensions.ABaseMonitor;
import com.appdynamics.extensions.TasksExecutionServiceProvider;
import com.appdynamics.extensions.conf.MonitorContextConfiguration;
import com.appdynamics.extensions.logging.ExtensionsLoggerFactory;
import com.appdynamics.extensions.util.AssertUtils;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.slf4j.Logger;

import java.util.List;
import java.util.Map;

import static utility.Constants.*;

public class DockerMonitor extends ABaseMonitor {

    private static final Logger logger = ExtensionsLoggerFactory.getLogger(DockerMonitor.class);
    private MonitorContextConfiguration contextConfiguration;
    private Map<String, ?> configYml = Maps.newHashMap();

    @Override
    protected String getDefaultMetricPrefix() {
        return DEFAULT_METRIC_PREFIX;
    }

    @Override
    public String getMonitorName() {
        return MONITOR_NAME;
    }

    @Override
    protected void initializeMoreStuff(Map<String, String> args) {
        contextConfiguration = getContextConfiguration();
        configYml = contextConfiguration.getConfigYml();
        AssertUtils.assertNotNull(configYml, "Config file cannot be empty");
    }

    @Override
    protected void doRun(TasksExecutionServiceProvider tasksExecutionServiceProvider) {
        if (configYml.get(TCP_SOCKETS) != null) {
            List<Map<String, ?>> tcpSockets = (List<Map<String, ?>>) configYml.get(TCP_SOCKETS);
            for (Map<String, ?> tcpSocket : tcpSockets) {
                AssertUtils.assertNotNull(tcpSocket,"uri and name cannot be empty in config file for any configured tcpSockets");
                TcpSocketDataTask tcpSocketDataTask = new TcpSocketDataTask(contextConfiguration, tasksExecutionServiceProvider.getMetricWriteHelper(),configYml,tcpSocket);
                tasksExecutionServiceProvider.submit((String) tcpSocket.get(NAME), tcpSocketDataTask);
            }
        } else if (configYml.get(UNIX_SOCKET) != null) {
            Map<String,?> unixSocket = (Map<String, ?>) configYml.get(UNIX_SOCKET);
            AssertUtils.assertNotNull(unixSocket,"unixSocket section cannot be empty in config file");
            UnixSocketDataTask unixSocketDataTask = new UnixSocketDataTask(contextConfiguration, tasksExecutionServiceProvider.getMetricWriteHelper(), configYml, unixSocket);
            tasksExecutionServiceProvider.submit((String) unixSocket.get(NAME),unixSocketDataTask);
        } else {
            logger.info("Neither {} nor {} are configured in config.yml file. Exiting...", TCP_SOCKETS, UNIX_SOCKET);
        }
    }

    @Override
    protected List<Map<String, ?>> getServers() {
        if (configYml.get(TCP_SOCKETS) != null) {
            return (List<Map<String, ?>>) configYml.get(TCP_SOCKETS);
        } else if (configYml.get(UNIX_SOCKET) != null) {
            List<Map<String, ?>> unixSocketList = Lists.newArrayList();
            Map<String, ?> unixSocket = (Map<String, ?>) configYml.get(UNIX_SOCKET);
            unixSocketList.add(unixSocket);
            return unixSocketList;
        } else {
            logger.info("Neither {} nor {} are configured in config.yml file. Exiting...", TCP_SOCKETS, UNIX_SOCKET);
            return null;
        }
    }
}
