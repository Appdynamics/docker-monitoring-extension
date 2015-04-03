package com.appdynamics.extensions.docker;

import com.appdynamics.extensions.http.SimpleHttpClient;
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

        DockerMonitor monitor = createMonitor(config, Collections.<String, String>emptyMap());
        monitor.execute(Collections.<String, String>emptyMap(), null);
    }

    @Test
    public void unixSocketsWithServerNameTest() throws TaskExecutionException, IOException {
        Map config = loadYaml();
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
        monitor.initialized = true;

        Mockito.doAnswer(new Answer() {
            public Object answer(InvocationOnMock invocation) throws Throwable {
                Object[] args = invocation.getArguments();
                String key = (String) args[0];
                if (expectedValueMap.containsKey(key)) {
                    Assert.assertEquals(expectedValueMap.get(key), args[1]);
                    logger.debug("Metric is {} value is {}", key, args[1]);
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

        monitor.config = config;
        monitor.setMetricPrefix();
        return monitor;
    }

    private Map<String, String> getExpectedValueMap(String prefix) {
        Map<String, String> map = new HashMap<String, String>();
        map.put(prefix + "|/rabbitmq_rabbit3_1|SizeRw", null);
        map.put(prefix + "|/rabbitmq_rabbit3_1|SizeRootFs", null);
        map.put(prefix + "|/rabbitmq_rabbit3_1|Network|Receive|Dropped", "11");
        map.put(prefix + "|/rabbitmq_rabbit3_1|Network|Receive|MB", "954");
        map.put(prefix + "|/rabbitmq_rabbit3_1|Network|Receive|Errors", "10");
        map.put(prefix + "|/rabbitmq_rabbit3_1|Network|Receive|Packets", "110");
        map.put(prefix + "|/rabbitmq_rabbit3_1|Network|Transmit|Dropped", "16");
        map.put(prefix + "|/rabbitmq_rabbit3_1|Network|Transmit|MB", "1049");
        map.put(prefix + "|/rabbitmq_rabbit3_1|Network|Transmit|Errors", "15");
        map.put(prefix + "|/rabbitmq_rabbit3_1|Network|Transmit|Packets", "120");
        map.put(prefix + "|/rabbitmq_rabbit3_1|Memory|Max Usage (MB)", "48");
        map.put(prefix + "|/rabbitmq_rabbit3_1|Memory|Current (MB)", "10");
        map.put(prefix + "|/rabbitmq_rabbit3_1|Memory|Limit (MB)", "3953");
        map.put(prefix + "|/rabbitmq_rabbit3_1|Memory|Fail Count", "0");
        map.put(prefix + "|/rabbitmq_rabbit3_1|CPU|System (Ticks)", "10");
        map.put(prefix + "|/rabbitmq_rabbit3_1|CPU|User Mode (Ticks)", "5");
        map.put(prefix + "|/rabbitmq_rabbit3_1|CPU|Total (Ticks)", "20");
        map.put(prefix + "|/rabbitmq_rabbit3_1|CPU|Kernel (Ticks)", "5");
        map.put(prefix + "|/rabbitmq_rabbit2_1|SizeRw", null);
        map.put(prefix + "|/rabbitmq_rabbit2_1|SizeRootFs", null);
        map.put(prefix + "|/rabbitmq_rabbit2_1|Network|Receive|Dropped", "21");
        map.put(prefix + "|/rabbitmq_rabbit2_1|Network|Receive|MB", "20");
        map.put(prefix + "|/rabbitmq_rabbit2_1|Network|Receive|Errors", "20");
        map.put(prefix + "|/rabbitmq_rabbit2_1|Network|Receive|Packets", "250");
        map.put(prefix + "|/rabbitmq_rabbit2_1|Network|Transmit|Dropped", "27");
        map.put(prefix + "|/rabbitmq_rabbit2_1|Network|Transmit|MB", "19");
        map.put(prefix + "|/rabbitmq_rabbit2_1|Network|Transmit|Errors", "25");
        map.put(prefix + "|/rabbitmq_rabbit2_1|Network|Transmit|Packets", "275");
        map.put(prefix + "|/rabbitmq_rabbit2_1|Memory|Max Usage (MB)", "43");
        map.put(prefix + "|/rabbitmq_rabbit2_1|Memory|Current (MB)", "19");
        map.put(prefix + "|/rabbitmq_rabbit2_1|Memory|Limit (MB)", "3953");
        map.put(prefix + "|/rabbitmq_rabbit2_1|Memory|Fail Count", "0");
        map.put(prefix + "|/rabbitmq_rabbit2_1|CPU|System (Ticks)", "12");
        map.put(prefix + "|/rabbitmq_rabbit2_1|CPU|User Mode (Ticks)", "7");
        map.put(prefix + "|/rabbitmq_rabbit2_1|CPU|Total (Ticks)", "10");
        map.put(prefix + "|/rabbitmq_rabbit2_1|CPU|Kernel (Ticks)", "6");
        map.put(prefix + "|Summary|Container Count", "4");
        map.put(prefix + "|Summary|Image Count", "26");
        map.put(prefix + "|Summary|Total Memory (MB)", "3953");
        map.put(prefix + "|Summary|MemoryLimit", "1");
        map.put(prefix + "|Summary|SwapLimit", "0");
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


}