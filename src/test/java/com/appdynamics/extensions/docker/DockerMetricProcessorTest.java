package com.appdynamics.extensions.docker;

import com.appdynamics.extensions.AMonitorJob;
import com.appdynamics.extensions.conf.MonitorContextConfiguration;
import com.appdynamics.extensions.metrics.Metric;
import com.appdynamics.extensions.util.MetricPathUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.common.collect.Maps;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

@RunWith(PowerMockRunner.class)
@PowerMockIgnore("javax.net.ssl.*")
public class DockerMetricProcessorTest {

    DataFetcher dataFetcher;
    Map configYml;
    String metricPrefix;
    MonitorContextConfiguration contextConfiguration;
    DockerMetricProcessor dockerMetricProcessorSpy;

    @Before
    public void init() {
        contextConfiguration = new MonitorContextConfiguration("Docker Monitor", "Custom Metrics|Docker", Mockito.mock(File.class), Mockito.mock(AMonitorJob.class));
        contextConfiguration.setConfigYml("src/test/resources/conf/config.yml");
        configYml = contextConfiguration.getConfigYml();
        metricPrefix = contextConfiguration.getMetricPrefix();
        dataFetcher = Mockito.mock(TcpSocketDataFetcher.class);
        dockerMetricProcessorSpy = Mockito.spy(new DockerMetricProcessor(dataFetcher, configYml, metricPrefix));
    }

    @Test
    public void processMetricTest() {
        Map<String, String> expectedMetricMap = getExpectedMetricMap();
        PowerMockito.when(dataFetcher.fetchData(Mockito.anyString(), Mockito.any(Class.class), Mockito.anyBoolean())).thenAnswer(
                new Answer() {
                    @Override
                    public Object answer(InvocationOnMock invocationOnMock) throws Throwable {
                        ObjectMapper objectMapper = new ObjectMapper();
                        String path = (String) invocationOnMock.getArguments()[0];
                        if (path.contains("containers/json")) {
                            return objectMapper.readValue(getClass().getResourceAsStream("/json/containers.json"), ArrayNode.class);
                        } else if (path.contains("info")) {
                            return objectMapper.readValue(getClass().getResourceAsStream("/json/info.json"), JsonNode.class);
                        } else if (path.contains("1/stats")) {
                            return objectMapper.readValue(getClass().getResourceAsStream("/json/container-stats_1.json"), JsonNode.class);
                        } else if (path.contains("2/stats")) {
                            return objectMapper.readValue(getClass().getResourceAsStream("/json/container-stats_2.json"), JsonNode.class);
                        } else if (path.contains("3/stats")) {
                            return objectMapper.readValue(getClass().getResourceAsStream("/json/container-stats_3.json"), JsonNode.class);
                        } else if (path.contains("containers/1/json")) {
                            return objectMapper.readValue(getClass().getResourceAsStream("/json/container.json"), JsonNode.class);
                        }

                        return null;
                    }
                });

        List<Metric> metricList = dockerMetricProcessorSpy.processMetrics();
        Map<String, String> map = Maps.newHashMap();
        for (Metric m : metricList) {
            map.put(m.getMetricPath(), m.getMetricValue());
        }
        Assert.assertTrue(map.equals(expectedMetricMap));
    }

    private Map<String, String> getExpectedMetricMap() {
        Map<String, String> expectedMetricMap = Maps.newHashMap();
        expectedMetricMap.put(metricPrefix + "|/rabbit1|State", "running");
        expectedMetricMap.put(metricPrefix + "|/rabbit1|networks|all|Receive|Dropped", "11");
        expectedMetricMap.put(metricPrefix + "|/rabbit1|networks|all|Receive|Bytes", "1000000000");
        expectedMetricMap.put(metricPrefix + "|/rabbit1|networks|all|Receive|Errors", "10");
        expectedMetricMap.put(metricPrefix + "|/rabbit1|networks|all|Receive|Packets", "110");
        expectedMetricMap.put(metricPrefix + "|/rabbit1|networks|all|Transmit|Dropped", "16");
        expectedMetricMap.put(metricPrefix + "|/rabbit1|networks|all|Transmit|Bytes", "1100000000");
        expectedMetricMap.put(metricPrefix + "|/rabbit1|networks|all|Transmit|Errors", "15");
        expectedMetricMap.put(metricPrefix + "|/rabbit1|networks|all|Transmit|Packets", "120");
        expectedMetricMap.put(metricPrefix + "|/rabbit1|memory|Max Usage", "50000000");
        expectedMetricMap.put(metricPrefix + "|/rabbit1|memory|Current Usage", "11000000");
        expectedMetricMap.put(metricPrefix + "|/rabbit1|memory|Limit", "4145242112");
        expectedMetricMap.put(metricPrefix + "|/rabbit1|cpu|System CPU Usage", "10");
        expectedMetricMap.put(metricPrefix + "|/rabbit1|cpu|Usermode Usage", "5");
        expectedMetricMap.put(metricPrefix + "|/rabbit1|cpu|Kernelmode Usage", "5");
        expectedMetricMap.put(metricPrefix + "|/rabbit1|cpu|Total Usage", "20");

        expectedMetricMap.put(metricPrefix + "|/rabbit2|State", "running");
        expectedMetricMap.put(metricPrefix + "|/rabbit2|networks|all|Receive|Dropped", "21");
        expectedMetricMap.put(metricPrefix + "|/rabbit2|networks|all|Receive|Bytes", "21000000");
        expectedMetricMap.put(metricPrefix + "|/rabbit2|networks|all|Receive|Errors", "20");
        expectedMetricMap.put(metricPrefix + "|/rabbit2|networks|all|Receive|Packets", "250");
        expectedMetricMap.put(metricPrefix + "|/rabbit2|networks|all|Transmit|Dropped", "27");
        expectedMetricMap.put(metricPrefix + "|/rabbit2|networks|all|Transmit|Bytes", "20000000");
        expectedMetricMap.put(metricPrefix + "|/rabbit2|networks|all|Transmit|Errors", "25");
        expectedMetricMap.put(metricPrefix + "|/rabbit2|networks|all|Transmit|Packets", "275");
        expectedMetricMap.put(metricPrefix + "|/rabbit2|memory|Max Usage", "45000000");
        expectedMetricMap.put(metricPrefix + "|/rabbit2|memory|Current Usage", "414524211");
        expectedMetricMap.put(metricPrefix + "|/rabbit2|memory|Limit", "4145242112");
        expectedMetricMap.put(metricPrefix + "|/rabbit2|cpu|System CPU Usage", "12");
        expectedMetricMap.put(metricPrefix + "|/rabbit2|cpu|Usermode Usage", "7");
        expectedMetricMap.put(metricPrefix + "|/rabbit2|cpu|Kernelmode Usage", "6");
        expectedMetricMap.put(metricPrefix + "|/rabbit2|cpu|Total Usage", "10");

        expectedMetricMap.put(metricPrefix + "|/rabbit3|State", "running");
        expectedMetricMap.put(metricPrefix + "|/rabbit3|networks|eth0|Receive|Dropped", "22");
        expectedMetricMap.put(metricPrefix + "|/rabbit3|networks|eth0|Receive|Bytes", "21000000");
        expectedMetricMap.put(metricPrefix + "|/rabbit3|networks|eth0|Receive|Errors", "20");
        expectedMetricMap.put(metricPrefix + "|/rabbit3|networks|eth0|Receive|Packets", "250");
        expectedMetricMap.put(metricPrefix + "|/rabbit3|networks|eth0|Transmit|Dropped", "27");
        expectedMetricMap.put(metricPrefix + "|/rabbit3|networks|eth0|Transmit|Bytes", "23000004");
        expectedMetricMap.put(metricPrefix + "|/rabbit3|networks|eth0|Transmit|Errors", "25");
        expectedMetricMap.put(metricPrefix + "|/rabbit3|networks|eth0|Transmit|Packets", "275");
        expectedMetricMap.put(metricPrefix + "|/rabbit3|networks|eth1|Receive|Dropped", "27");
        expectedMetricMap.put(metricPrefix + "|/rabbit3|networks|eth1|Receive|Bytes", "21000000");
        expectedMetricMap.put(metricPrefix + "|/rabbit3|networks|eth1|Receive|Errors", "20");
        expectedMetricMap.put(metricPrefix + "|/rabbit3|networks|eth1|Receive|Packets", "250");
        expectedMetricMap.put(metricPrefix + "|/rabbit3|networks|eth1|Transmit|Dropped", "27");
        expectedMetricMap.put(metricPrefix + "|/rabbit3|networks|eth1|Transmit|Bytes", "28000000");
        expectedMetricMap.put(metricPrefix + "|/rabbit3|networks|eth1|Transmit|Errors", "25");
        expectedMetricMap.put(metricPrefix + "|/rabbit3|networks|eth1|Transmit|Packets", "275");
        expectedMetricMap.put(metricPrefix + "|/rabbit3|memory|Max Usage", "639651840");
        expectedMetricMap.put(metricPrefix + "|/rabbit3|memory|Current Usage", "626225152");
        expectedMetricMap.put(metricPrefix + "|/rabbit3|memory|Limit", "15770537984");
        expectedMetricMap.put(metricPrefix + "|/rabbit3|cpu|System CPU Usage", "41047820000000");
        expectedMetricMap.put(metricPrefix + "|/rabbit3|cpu|Usermode Usage", "41280000000");
        expectedMetricMap.put(metricPrefix + "|/rabbit3|cpu|Kernelmode Usage", "3320000000");
        expectedMetricMap.put(metricPrefix + "|/rabbit3|cpu|Total Usage", "44314766639");

        expectedMetricMap.put(metricPrefix + "|summary|Container Count", "4");
        expectedMetricMap.put(metricPrefix + "|summary|Image Count", "26");
        expectedMetricMap.put(metricPrefix + "|summary|Total Memory", "4145242112");
        expectedMetricMap.put(metricPrefix + "|summary|MemoryLimit", "true");
        expectedMetricMap.put(metricPrefix + "|summary|SwapLimit", "false");

        return expectedMetricMap;
    }

    @Test
    public void deriveContainerNameTest() throws IOException {
        PowerMockito.when(dataFetcher.fetchData(Mockito.anyString(), Mockito.any(Class.class), Mockito.anyBoolean())).thenAnswer(
                new Answer() {
                    @Override
                    public Object answer(InvocationOnMock invocationOnMock) throws Throwable {
                        ObjectMapper objectMapper = new ObjectMapper();
                        String path = (String) invocationOnMock.getArguments()[0];
                        if (path.contains("containers/1/json")) {
                            return objectMapper.readValue(getClass().getResourceAsStream("/json/container.json"), JsonNode.class);
                        }
                        return null;
                    }
                });

        ArrayNode container = (new ObjectMapper()).readValue(getClass().getResourceAsStream("/json/containers.json"),ArrayNode.class);
        String containerName = dockerMetricProcessorSpy.deriveContainerName(container.get(0),"1");
        Assert.assertEquals("/rabbit1",containerName);

        configYml.put("containerNaming","${HOSTNAME}_${CONTAINER_NAME}");
        containerName = dockerMetricProcessorSpy.deriveContainerName(container.get(0),"1");
        Assert.assertEquals("rabbit.host_/rabbit1",containerName);

        configYml.put("containerNaming","${HOSTNAME}_${CONTAINER_ID}");
        containerName = dockerMetricProcessorSpy.deriveContainerName(container.get(0),"1");
        Assert.assertEquals("rabbit.host_1",containerName);

        configYml.put("containerNaming","${CONTAINER_ID}_${CONTAINER_NAME}");
        containerName = dockerMetricProcessorSpy.deriveContainerName(container.get(0),"1");
        Assert.assertEquals("1_/rabbit1",containerName);

        configYml.put("containerNaming","${HOSTNAME}_${CONTAINER_ID}_${CONTAINER_NAME}");
        containerName = dockerMetricProcessorSpy.deriveContainerName(container.get(0),"1");
        Assert.assertEquals("rabbit.host_1_/rabbit1",containerName);

        configYml.put("containerNaming","${HOSTNAME}+${CONTAINER_NAME}");
        containerName = dockerMetricProcessorSpy.deriveContainerName(container.get(0),"1");
        Assert.assertEquals("rabbit.host+/rabbit1",containerName);

        configYml.put("containerNaming","${HOSTNAME}");
        containerName = dockerMetricProcessorSpy.deriveContainerName(container.get(0),"1");
        Assert.assertEquals("rabbit.host",containerName);

        configYml.put("containerNaming","${CONTAINER_ID}");
        containerName = dockerMetricProcessorSpy.deriveContainerName(container.get(0),"1");
        Assert.assertEquals("1",containerName);

        configYml.put("containerNaming","${CONTAINER_NAME}");
        containerName = dockerMetricProcessorSpy.deriveContainerName(container.get(0),"1");
        Assert.assertEquals("/rabbit1",containerName);

        configYml.put("containerNaming","${HOSTNAME1}_${CONTAINER_NAME}");
        containerName = dockerMetricProcessorSpy.deriveContainerName(container.get(0),"1");
        Assert.assertEquals("/rabbit1",containerName);

        configYml.put("containerNaming","PREFIX-${HOSTNAME}");
        containerName = dockerMetricProcessorSpy.deriveContainerName(container.get(0),"1");
        Assert.assertEquals("PREFIX-rabbit.host",containerName);

    }

}
