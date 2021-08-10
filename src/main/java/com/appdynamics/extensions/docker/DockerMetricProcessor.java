package com.appdynamics.extensions.docker;

import com.appdynamics.extensions.logging.ExtensionsLoggerFactory;
import com.appdynamics.extensions.metrics.Metric;
import com.appdynamics.extensions.util.JsonUtils;
import com.appdynamics.extensions.util.StringUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.common.collect.Lists;
import org.slf4j.Logger;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static utility.Constants.*;

public class DockerMetricProcessor {

    private static final Logger logger = ExtensionsLoggerFactory.getLogger(DockerMetricProcessor.class);
    private DataFetcher dataFetcher;
    private Map<String, ?> configYml;
    private String metricPrefix;

    public DockerMetricProcessor(DataFetcher dataFetcher, Map<String, ?> configYml, String metricPrefix) {
        this.dataFetcher = dataFetcher;
        this.configYml = configYml;
        this.metricPrefix = metricPrefix;
    }

    protected List<Metric> processMetrics() {
        List<Metric> metricList = Lists.newArrayList();
        Map metrics = (Map) configYml.get(METRICS);
        if (metrics.get(CONTAINERS_METRICS) != null || metrics.get(CONTAINER_RESOURCE_METRICS) != null) {
            getContainerStats((List<Map<String, ?>>) metrics.get(CONTAINERS_METRICS), (Map) metrics.get(CONTAINER_RESOURCE_METRICS), metricList);
        }
        if (metrics.get(INFO_METRICS) != null) {
            getInfoStats((List<Map<String, ?>>) metrics.get(INFO_METRICS), metricList);
        }
        return metricList;
    }

    protected void getContainerStats(List<Map<String, ?>> containersMetricsFromConfig, Map containersResourceMetricsFromConfig, List<Metric> metricList) {
        ArrayNode containers = dataFetcher.fetchData(CONTAINERS_URI, ArrayNode.class, true);
        if (containers != null && !containers.isEmpty()) {
            for (JsonNode container : containers) {
                String containerId = container.findValue("Id").asText();
                String containerName = deriveContainerName(container, containerId);
                String prefix = metricPrefix + SEPARATOR + containerName;
                if (containersMetricsFromConfig != null && !containersMetricsFromConfig.isEmpty()) {
                    collectMetrics(container, containersMetricsFromConfig, prefix, metricList);
                }
                if (containersResourceMetricsFromConfig != null && !containersResourceMetricsFromConfig.isEmpty()) {
                    String resourcePath = String.format(CONTAINER_RESOURCE_STATS_URI, containerId);
                    JsonNode resourceStatNode = dataFetcher.fetchData(resourcePath, JsonNode.class, false);
                    if (resourceStatNode != null) {
                        getNetworkStats(resourceStatNode, containersResourceMetricsFromConfig, metricList, containerName);
                        getMemoryStats(resourceStatNode, containersResourceMetricsFromConfig, metricList, containerName);
                        getCPUStats(resourceStatNode, containersResourceMetricsFromConfig, metricList, containerName);
                    }
                }
            }
        } else {
            logger.debug("No containers exist as response is either null or empty. Not fetching containers metric and containerStats metric");
        }
    }

    protected void getNetworkStats(JsonNode resourceStatNode, Map containersResourceMetricsFromConfig, List<Metric> metricList, String containerName) {
        List<Map<String, ?>> networkStats = (List<Map<String, ?>>) containersResourceMetricsFromConfig.get(CONTAINER_RESOURCE_NETWORK_METRICS);
        if (networkStats != null && !networkStats.isEmpty()) {
            JsonNode networkNode = resourceStatNode.get(CONTAINER_RESOURCE_NETWORK_METRICS);
            if (networkNode != null) {
                Iterator<Map.Entry<String, JsonNode>> networkNodeItr = networkNode.fields();
                while (networkNodeItr.hasNext()) {
                    Map.Entry<String, JsonNode> networkEntity = networkNodeItr.next();
                    String networkEntityName = networkEntity.getKey();
                    JsonNode networkEntityNode = networkEntity.getValue();
                    String prefix = metricPrefix + SEPARATOR + containerName + SEPARATOR + CONTAINER_RESOURCE_NETWORK_METRICS + SEPARATOR + networkEntityName;
                    collectMetrics(networkEntityNode, networkStats, prefix, metricList);
                }
            } else {
                String prefix = metricPrefix + SEPARATOR + containerName + SEPARATOR + CONTAINER_RESOURCE_NETWORK_METRICS + SEPARATOR + "all";
                collectMetrics(resourceStatNode,networkStats,prefix,metricList);
            }
        }
    }

    protected void getMemoryStats(JsonNode resourceStatNode, Map containersResourceMetricsFromConfig, List<Metric> metricList, String containerName) {
        List<Map<String, ?>> memoryStats = (List<Map<String, ?>>) containersResourceMetricsFromConfig.get(CONTAINER_RESOURCE_MEMORY_METRICS);
        if (memoryStats != null && !memoryStats.isEmpty()) {
            JsonNode memoryNode = resourceStatNode.get(CONTAINER_RESOURCE_MEMORY_METRICS);
            if (memoryNode != null && !memoryNode.isEmpty()) {
                String prefix = metricPrefix + SEPARATOR + containerName;
                collectMetrics(memoryNode, memoryStats, prefix, metricList);
            }
        }
    }

    protected void getCPUStats(JsonNode resourceStatNode, Map containersResourceMetricsFromConfig, List<Metric> metricList, String containerName){
        List<Map<String,?>> cpuStats = (List<Map<String, ?>>) containersResourceMetricsFromConfig.get(CONTAINER_RESOURCE_CPU_METRICS);
        if(cpuStats != null && !cpuStats.isEmpty()){
            JsonNode cpuNode = resourceStatNode.get(CONTAINER_RESOURCE_CPU_METRICS);
            if(cpuNode != null && !cpuNode.isEmpty()){
                String prefix = metricPrefix + SEPARATOR + containerName;
                collectMetrics(cpuNode,cpuStats,prefix,metricList);
            }
        }
    }

    protected void getInfoStats(List<Map<String, ?>> infoMetricsFromConfig, List<Metric> metricList) {
        JsonNode info = dataFetcher.fetchData(INFO_URI, JsonNode.class, true);
        if (info != null) {
            String prefix = metricPrefix + SEPARATOR + "summary";
            collectMetrics(info, infoMetricsFromConfig, prefix, metricList);
        } else {
            logger.debug("Cannot fetch summary stats as [{}] response is either null or empty.",INFO_URI);
        }
    }

    protected void collectMetrics(JsonNode node, List<Map<String, ?>> metricsFromConfig, String metricPrefix, List<Metric> metricList) {
        for (Map<String, ?> metricFromConfig : metricsFromConfig) {
            String metricName = (String) metricFromConfig.get("name");
            String metricPath = metricPrefix + SEPARATOR + metricFromConfig.get("alias");
            if (node.findValue(metricName) != null) {
                String value = node.findValue(metricName).asText();
                metricList.add(new Metric(metricPath, value, metricPath, metricFromConfig));
            }
        }
    }

    protected String deriveContainerName(JsonNode container, String containerId) {
        String containerNaming = (String) configYml.get(CONTAINER_NAMING);
        String containerName = getContainerName(container);
        if (StringUtils.hasText(containerNaming)) {
            logger.debug("Resolving Container name as container naming is enabled...");
            if (containerNaming.contains(CONTAINER_NAMING_HOSTNAME)) {
                String containerHostName = getContainerHostName(containerId);
                if (containerHostName != null) {
                    containerNaming = containerNaming.replace(CONTAINER_NAMING_HOSTNAME, containerHostName);
                } else {
                    logger.warn("Couldn't find container hostname for container {}. Returning container name or container ID", containerId);
                    return StringUtils.hasText(containerName) ? containerName : containerId;
                }
            }
            containerNaming = containerNaming.replace(CONTAINER_NAMING_CONTAINERID, containerId).replace(CONTAINER_NAMING_CONTAINERNAME, containerName);
            if (StringUtils.hasText(containerNaming) && (!containerNaming.contains("$") || !containerNaming.contains("{") || !containerNaming.contains("}"))) {
                logger.debug("The container name for container ID {} is resolved to {}", containerId, containerNaming);
                return containerNaming;
            } else {
                logger.warn("Cannot Resolve container naming for {} so defaulting to container name/id", containerId);
                return StringUtils.hasText(containerName) ? containerName : containerId;
            }
        } else {
            logger.debug("The container naming is not set in config so defaulting to container name/id");
            return StringUtils.hasText(containerName) ? containerName : containerId;
        }
    }

    protected String getContainerHostName(String containerId) {
        String resourcePath = String.format(CONTAINERS_HOSTNAME_PATH, containerId);
        JsonNode node = dataFetcher.fetchData(resourcePath, JsonNode.class, true);
        if (node != null) {
            JsonNode nestedNode = JsonUtils.getNestedObject(node, "Config", "Hostname");
            if (nestedNode != null) {
                return nestedNode.asText();
            }
        }
        return null;
    }

    protected String getContainerName(JsonNode container) {
        ArrayNode node = (ArrayNode) container.get("Names");
        if (node != null && !node.isEmpty()) {
            return node.get(0).asText();
        }
        return null;
    }
}
