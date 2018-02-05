/*
 *  Copyright 2015. AppDynamics LLC and its affiliates.
 *  All Rights Reserved.
 *  This is unpublished proprietary source code of AppDynamics LLC and its affiliates.
 *  The copyright notice above does not evidence any actual or intended publication of such source code.
 *
 */

package com.appdynamics.extensions.docker;

import com.appdynamics.extensions.conf.MonitorConfiguration;
import com.appdynamics.extensions.http.SimpleHttpClient;
import com.appdynamics.extensions.util.MetricWriteHelper;
import com.singularity.ee.agent.systemagent.api.exception.TaskExecutionException;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ArrayNode;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class DockerMonitorTest {
    public static final Logger logger = LoggerFactory.getLogger(DockerMonitorTest.class);

    @Test
    public void tcpSocketsTest() throws TaskExecutionException {
        Map config = loadYaml();
        config.put("metricPrefix", "Test|Docker|||");
        config.remove("unixSocket");
        List<Map> tcpSockets = new ArrayList<Map>();
        tcpSockets.add(Collections.singletonMap("baseUrl", ""));
        config.put("tcpSockets", tcpSockets);

        Map<String, String> expectedValueMap = getExpectedValueMap("Test|Docker");
        DockerMonitor monitor = createMonitor(config, expectedValueMap);
        monitor.execute(Collections.<String, String>emptyMap(), null);
        Assert.assertTrue("All the expected metrics are not reported.Missing ones are " + expectedValueMap
                , expectedValueMap.isEmpty());
    }

    @Test
    public void tcpSocketsWithServerNameTest() throws TaskExecutionException {
        Map config = loadYaml();
        config.put("metricPrefix", "Test|Docker");
        config.remove("unixSocket");
        List<Map> tcpSockets = new ArrayList<Map>();
        Map<String, String> map = new HashMap<String, String>();
        map.put("baseUrl", "");
        map.put("name", "Server33");
        tcpSockets.add(map);
        config.put("tcpSockets", tcpSockets);

        Map<String, String> expectedValueMap = getExpectedValueMap("Test|Docker|Server33");
        DockerMonitor monitor = createMonitor(config, expectedValueMap);
        monitor.execute(Collections.<String, String>emptyMap(), null);
        Assert.assertTrue("All the expected metrics are not reported.Missing ones are " + expectedValueMap
                , expectedValueMap.isEmpty());
    }

    @Test
    public void unixSocketsNoFileTest() throws TaskExecutionException {
        Map config = loadYaml();
        config.put("metricPrefix", "Test2|Docker2||||");
        //The file doesnt exit case
        config.put("unixSocket", Collections.singletonMap("commandFile", "file.sh"));
        config.remove("tcpSockets");
        DockerMonitor monitor = createMonitor(config, Collections.<String, String>emptyMap());
        monitor.execute(Collections.<String, String>emptyMap(), null);
    }

    @Test
    public void unixSocketsWithServerNameTest() throws TaskExecutionException, IOException {
        Map config = loadYaml();
        config.remove("tcpSockets");
        config.put("metricPrefix", "Test2|Docker2||||");
        File file = new File(System.getProperty("java.io.tmpdir"), "docketmonitor.file");
        if (!file.exists()) {
            file.createNewFile();
        }
        Map<String, String> map = new HashMap<String, String>();
        map.put("commandFile", file.getAbsolutePath());
        map.put("name", "Unix1");
        config.put("unixSocket", map);
        Map<String, String> expectedValueMap = getExpectedValueMap("Test2|Docker2|Unix1");
        DockerMonitor monitor = createMonitor(config, expectedValueMap);
        monitor.execute(Collections.<String, String>emptyMap(), null);
        Assert.assertTrue("All the expected metrics are not reported.Missing ones are " + expectedValueMap
                , expectedValueMap.isEmpty());
    }

    @Test
    public void unixSocketTest() throws TaskExecutionException, IOException {
        Map config = loadYaml();
        config.remove("tcpSockets");
        config.put("metricPrefix", "Test2|Docker2||||");
        File file = new File(System.getProperty("java.io.tmpdir"), "docketmonitor.file");
        if (!file.exists()) {
            file.createNewFile();
        }
        config.put("unixSocket", Collections.singletonMap("commandFile", file.getAbsolutePath()));
        Map<String, String> expectedValueMap = getExpectedValueMap("Test2|Docker2");
        DockerMonitor monitor = createMonitor(config, expectedValueMap);
        monitor.execute(Collections.<String, String>emptyMap(), null);
        Assert.assertTrue("All the expected metrics are not reported.Missing ones are " + expectedValueMap
                , expectedValueMap.isEmpty());
    }

    private DockerMonitor createMonitor(Map config, final Map<String, String> expectedValueMap) {
        DockerMonitor monitor = Mockito.spy(new DockerMonitor());

        Mockito.doAnswer(new Answer() {
            public Object answer(InvocationOnMock invocation) throws Throwable {
                Object[] args = invocation.getArguments();
                String key = (String) args[0];
                if (expectedValueMap.containsKey(key)) {
                    logger.debug("Metric is {} value is {}", key, args[1]);
                    Assert.assertEquals(expectedValueMap.get(key), args[1]);
                    expectedValueMap.remove(key);
                } else {
                    Assert.fail("The metric " + key + " is Unexpected");
                }
                return null;
            }
        }).when(monitor).printMetric(Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString());

        Mockito.doNothing().when(monitor).initialize(Mockito.anyMap());

        Mockito.doReturn(createDataFetcher(UnixSocketDataFetcher.class)).when(monitor)
                .getUnixSocketDataFetcher(Mockito.any(File.class));

        Mockito.doReturn(createDataFetcher(TcpSocketDataFetcher.class)).when(monitor)
                .getTcpSocketDataFetcher(Mockito.anyMap(), Mockito.any(SimpleHttpClient.class));

        Mockito.doReturn(Mockito.mock(SimpleHttpClient.class))
                .when(monitor).buildSimpleHttpClient(Mockito.anyMap());
        MetricWriteHelper writer = Mockito.mock(MetricWriteHelper.class);
        DockerMonitor.TaskRunner taskRunner = monitor.new TaskRunner();
        MonitorConfiguration mc = new MonitorConfiguration((String) config.get("metricPrefix"), taskRunner, writer);
        mc = Mockito.spy(mc);
        Mockito.doReturn(config).when(mc).getConfigYml();
        monitor.configuration = mc;
        return monitor;
    }

    private Map<String, String> getExpectedValueMap(String prefix) {
        Map<String, String> map = new HashMap<String, String>();
        map.put(prefix + "|/rabbit1|SizeRw", null);
        map.put(prefix + "|/rabbit1|SizeRootFs", null);
        map.put(prefix + "|/rabbit1|Networks|all|Receive|Dropped", "11");
        map.put(prefix + "|/rabbit1|Networks|all|Receive|MB", "954");
        map.put(prefix + "|/rabbit1|Networks|all|Receive|Errors", "10");
        map.put(prefix + "|/rabbit1|Networks|all|Receive|Packets", "110");
        map.put(prefix + "|/rabbit1|Networks|all|Transmit|Dropped", "16");
        map.put(prefix + "|/rabbit1|Networks|all|Transmit|MB", "1049");
        map.put(prefix + "|/rabbit1|Networks|all|Transmit|Errors", "15");
        map.put(prefix + "|/rabbit1|Networks|all|Transmit|Packets", "120");
        map.put(prefix + "|/rabbit1|Memory|Max Usage (MB)", "48");
        map.put(prefix + "|/rabbit1|Memory|Current (MB)", "10");
        map.put(prefix + "|/rabbit1|Memory|Current %", "0");
        map.put(prefix + "|/rabbit1|Memory|Limit (MB)", "3953");
        map.put(prefix + "|/rabbit1|Memory|Fail Count", "0");
        map.put(prefix + "|/rabbit1|CPU|System (Ticks)", "10");
        map.put(prefix + "|/rabbit1|CPU|User Mode (Ticks)", "5");
        map.put(prefix + "|/rabbit1|CPU|Total (Ticks)", "20");
        map.put(prefix + "|/rabbit1|CPU|Kernel (Ticks)", "5");
        map.put(prefix + "|/rabbit2|SizeRw", null);
        map.put(prefix + "|/rabbit2|SizeRootFs", null);
        map.put(prefix + "|/rabbit2|Networks|all|Receive|Dropped", "21");
        map.put(prefix + "|/rabbit2|Networks|all|Receive|MB", "20");
        map.put(prefix + "|/rabbit2|Networks|all|Receive|Errors", "20");
        map.put(prefix + "|/rabbit2|Networks|all|Receive|Packets", "250");
        map.put(prefix + "|/rabbit2|Networks|all|Transmit|Dropped", "27");
        map.put(prefix + "|/rabbit2|Networks|all|Transmit|MB", "19");
        map.put(prefix + "|/rabbit2|Networks|all|Transmit|Errors", "25");
        map.put(prefix + "|/rabbit2|Networks|all|Transmit|Packets", "275");
        map.put(prefix + "|/rabbit2|Memory|Max Usage (MB)", "43");
        map.put(prefix + "|/rabbit2|Memory|Current (MB)", "395");
        map.put(prefix + "|/rabbit2|Memory|Current %", "10");
        map.put(prefix + "|/rabbit2|Memory|Limit (MB)", "3953");
        map.put(prefix + "|/rabbit2|Memory|Fail Count", "0");
        map.put(prefix + "|/rabbit2|CPU|System (Ticks)", "12");
        map.put(prefix + "|/rabbit2|CPU|User Mode (Ticks)", "7");
        map.put(prefix + "|/rabbit2|CPU|Total (Ticks)", "10");
        map.put(prefix + "|/rabbit2|CPU|Kernel (Ticks)", "6");
        map.put(prefix + "|/rabbit3|SizeRw", null);
        map.put(prefix + "|/rabbit3|SizeRootFs", null);
        map.put(prefix + "|/rabbit3|Networks|eth0|Receive|Dropped", "22");
        map.put(prefix + "|/rabbit3|Networks|eth0|Receive|MB", "20");
        map.put(prefix + "|/rabbit3|Networks|eth0|Receive|Errors", "20");
        map.put(prefix + "|/rabbit3|Networks|eth0|Receive|Packets", "250");
        map.put(prefix + "|/rabbit3|Networks|eth0|Transmit|Dropped", "27");
        map.put(prefix + "|/rabbit3|Networks|eth0|Transmit|MB", "22");
        map.put(prefix + "|/rabbit3|Networks|eth0|Transmit|Errors", "25");
        map.put(prefix + "|/rabbit3|Networks|eth0|Transmit|Packets", "275");
        map.put(prefix + "|/rabbit3|Networks|eth1|Receive|Dropped", "27");
        map.put(prefix + "|/rabbit3|Networks|eth1|Receive|MB", "20");
        map.put(prefix + "|/rabbit3|Networks|eth1|Receive|Errors", "20");
        map.put(prefix + "|/rabbit3|Networks|eth1|Receive|Packets", "250");
        map.put(prefix + "|/rabbit3|Networks|eth1|Transmit|Dropped", "27");
        map.put(prefix + "|/rabbit3|Networks|eth1|Transmit|MB", "27");
        map.put(prefix + "|/rabbit3|Networks|eth1|Transmit|Errors", "25");
        map.put(prefix + "|/rabbit3|Networks|eth1|Transmit|Packets", "275");
        map.put(prefix + "|/rabbit3|Memory|Max Usage (MB)", "610");
        map.put(prefix + "|/rabbit3|Memory|Current (MB)", "597");
        map.put(prefix + "|/rabbit3|Memory|Current %", "4");
        map.put(prefix + "|/rabbit3|Memory|Limit (MB)", "15040");
        map.put(prefix + "|/rabbit3|Memory|Fail Count", "0");
        map.put(prefix + "|/rabbit3|CPU|System (Ticks)", "41047820000000");
        map.put(prefix + "|/rabbit3|CPU|User Mode (Ticks)", "41280000000");
        map.put(prefix + "|/rabbit3|CPU|Total (Ticks)", "44314766639");
        map.put(prefix + "|/rabbit3|CPU|Kernel (Ticks)", "3320000000");
        map.put(prefix + "|Summary|Container Count", "4");
        map.put(prefix + "|Summary|Image Count", "26");
        map.put(prefix + "|Summary|Total Memory (MB)", "3953");
        map.put(prefix + "|Summary|MemoryLimit", "1");
        map.put(prefix + "|Summary|SwapLimit", "0");
        map.put(prefix + "|Summary|Running Container Count", "3");
        return map;
    }

    private Object createDataFetcher(Class<? extends DataFetcher> clazz) {
        DataFetcher mock = Mockito.mock(clazz);
        Mockito.doAnswer(new Answer() {
            public Object answer(InvocationOnMock invocationOnMock) throws Throwable {
                String path = (String) invocationOnMock.getArguments()[0];
                if (path.contains("containers/json")) {
                    return loadJson("/json/containers.json", ArrayNode.class);
                } else if (path.contains("info")) {
                    return loadJson("/json/info.json", JsonNode.class);
                } else if (path.contains("1/stats")) {
                    return loadJson("/json/container-stats_1.json", JsonNode.class);
                } else if (path.contains("2/stats")) {
                    return loadJson("/json/container-stats_2.json", JsonNode.class);
                } else if (path.contains("3/stats")) {
                    return loadJson("/json/container-stats_3.json", JsonNode.class);
                } else if (path.contains("/containers/1/json")) {
                    return loadJson("/json/container.json", JsonNode.class);
                }

                return null;
            }
        }).when(mock).fetchData(Mockito.anyString(), Mockito.any(Class.class), Mockito.anyBoolean());
        return mock;
    }

    private Object loadJson(String s, Class clazz) throws IOException {
        return new ObjectMapper().readValue(getClass().getResourceAsStream(s), clazz);
    }

    private Map loadYaml() {
        Yaml yaml = new Yaml();
        return (Map) yaml.load(getClass().getResourceAsStream("/conf/config.yml"));
    }

    @Test
    public void deriveContainerNameWith() throws TaskExecutionException, IOException {
        Map config = loadYaml();
        config.put("metricPrefix", "Test|Docker|||");

        config.remove("unixSocket");
        List<Map> tcpSockets = new ArrayList<Map>();
        tcpSockets.add(Collections.singletonMap("baseUrl", ""));
        config.put("tcpSockets", tcpSockets);

        Map<String, String> expectedValueMap = getExpectedValueMap("Test|Docker");
        DockerMonitor monitor = createMonitor(config, expectedValueMap);
        TcpSocketDataFetcher dataFetcher = monitor.getTcpSocketDataFetcher(Mockito.mock(Map.class),
                Mockito.mock(SimpleHttpClient.class));
        ArrayNode containers = (ArrayNode) loadJson("/json/containers.json", JsonNode.class);
        String containerName = monitor.deriveContainerName(containers.get(0), "1", dataFetcher);
        Assert.assertEquals("/rabbit1", containerName);


        config.put("containerNaming", "${HOSTNAME}_${CONTAINER_NAME}");
        containerName = monitor.deriveContainerName(containers.get(0), "1", dataFetcher);
        Assert.assertEquals("rabbit.host_/rabbit1", containerName);

        config.put("containerNaming", "${HOSTNAME}_${CONTAINER_ID}");
        containerName = monitor.deriveContainerName(containers.get(0), "1", dataFetcher);
        Assert.assertEquals("rabbit.host_1", containerName);

        config.put("containerNaming", "${HOSTNAME}+${CONTAINER_NAME}");
        containerName = monitor.deriveContainerName(containers.get(0), "1", dataFetcher);
        Assert.assertEquals("rabbit.host+/rabbit1", containerName);

        config.put("containerNaming", "${HOSTNAME}");
        containerName = monitor.deriveContainerName(containers.get(0), "1", dataFetcher);
        Assert.assertEquals("rabbit.host", containerName);

        config.put("containerNaming", "${CONTAINER_ID}");
        containerName = monitor.deriveContainerName(containers.get(0), "1", dataFetcher);
        Assert.assertEquals("1", containerName);

        config.put("containerNaming", "${HOSTNAME1}/${CONTAINER_NAME}");
        containerName = monitor.deriveContainerName(containers.get(0), "1", dataFetcher);
        Assert.assertEquals("/rabbit1", containerName);

        config.put("containerNaming", "PREFIX-${HOSTNAME}");
        containerName = monitor.deriveContainerName(containers.get(0), "1", dataFetcher);
        Assert.assertEquals("PREFIX-rabbit.host", containerName);
    }

}