package com.appdynamics.extensions.docker;

import com.appdynamics.TaskInputArgs;
import com.appdynamics.extensions.PathResolver;
import com.appdynamics.extensions.StringUtils;
import com.appdynamics.extensions.conf.MonitorConfiguration;
import com.appdynamics.extensions.conf.MonitorConfiguration.ConfItem;
import com.appdynamics.extensions.dashboard.CustomDashboardTask;
import com.appdynamics.extensions.http.SimpleHttpClient;
import com.appdynamics.extensions.http.SimpleHttpClientBuilder;
import com.appdynamics.extensions.util.MetricWriteHelperFactory;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.singularity.ee.agent.systemagent.api.AManagedMonitor;
import com.singularity.ee.agent.systemagent.api.MetricWriter;
import com.singularity.ee.agent.systemagent.api.TaskExecutionContext;
import com.singularity.ee.agent.systemagent.api.TaskOutput;
import com.singularity.ee.agent.systemagent.api.exception.TaskExecutionException;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.node.ArrayNode;
import org.codehaus.jackson.node.BooleanNode;
import org.codehaus.jackson.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Created by abey.tom on 3/30/15.
 */
public class DockerMonitor extends AManagedMonitor {
    public static final Logger logger = LoggerFactory.getLogger(DockerMonitor.class);
    public static final String METRIC_PREFIX = "Custom Metrics|Docker|";
    public static final BigDecimal BIG_DECIMAL_100 = new BigDecimal("100");

    private Cache<String, BigInteger> previousMetricsMap;
    private Cache<String, String> metricMap;

    private CustomDashboardTask dashboardTask;
    protected MonitorConfiguration configuration;

    public DockerMonitor() {
        String msg = "Using Monitor Version [" + getImplementationVersion() + "]";
        logger.info(msg);
        System.out.println(msg);
        previousMetricsMap = CacheBuilder.newBuilder()
                .expireAfterWrite(10, TimeUnit.MINUTES).build();

        metricMap = CacheBuilder.newBuilder()
                .expireAfterWrite(1, TimeUnit.HOURS).build();

        dashboardTask = new CustomDashboardTask();
    }

    protected void initialize(Map<String, String> argsMap) {
        if (configuration == null) {
            MonitorConfiguration conf = new MonitorConfiguration(METRIC_PREFIX);
            conf.setConfigYml(argsMap.get("config-file"), new MonitorConfiguration.FileWatchListener() {
                public void onFileChange(File file) {
                    postConfigReload();
                }
            });
            conf.setMetricWriter(MetricWriteHelperFactory.create(this));
            conf.checkIfInitialized(ConfItem.METRIC_PREFIX, ConfItem.METRIC_WRITE_HELPER, ConfItem.CONFIG_YML);
            this.configuration = conf;
            postConfigReload();
        }
    }

    private void postConfigReload() {
        if (configuration != null && configuration.getConfigYml() != null) {
            Map<String, ?> config = configuration.getConfigYml();
            Set<String> instanceNames = getInstanceNames(config);
            String metricPrefix = configuration.getMetricPrefix();
            dashboardTask.updateConfig(instanceNames, metricPrefix, (Map) config.get("customDashboard"));
        }
    }

    private static Set<String> getInstanceNames(Map<String, ?> config) {
        Map unixSocket = (Map) config.get("unixSocket");
        Set<String> names = new HashSet<String>();
        if (unixSocket != null) {
            String name = (String) unixSocket.get("name");
            if (name != null) {
                names.add(name);
            } else {
                names.add("");
            }
        }

        List<Map> tcpSockets = (List) config.get("tcpSockets");
        if (tcpSockets != null) {
            for (Map tcpSocket : tcpSockets) {
                String name = (String) tcpSocket.get("name");
                if (name != null) {
                    names.add(name);
                } else {
                    names.add("");
                }
            }
        }
        return names;
    }

    private Map readYml(File file) {
        Yaml yaml = new Yaml();
        try {
            return (Map) yaml.load(new FileReader(file));
        } catch (FileNotFoundException e) {
            logger.error("Unable to read the configuration from " + file.getAbsolutePath(), e);
        }
        return null;
    }


    public TaskOutput execute(Map<String, String> argsMap, TaskExecutionContext taskExecutionContext) throws TaskExecutionException {
        initialize(argsMap);
        if (configuration != null && configuration.getConfigYml() != null) {
            Map<String, ?> config = configuration.getConfigYml();
            if (config != null) {
                //Check if the UNIX Socket is configured
                if (config.get("unixSocket") != null) {
                    getDataFromUnixSocket();
                } else {
                    logger.debug("Unix Sockets are not configured");
                }

                //Check the TCP sockets
                List tcpSockets = (List) config.get("tcpSockets");
                if (tcpSockets != null && tcpSockets.size() > 0) {
                    getDataFromTcpSockets();
                } else {
                    logger.debug("NO TCP sockets are configured");
                }
            } else {
                logger.error("The docker config is null, please check the file {}", argsMap.get("config-file"));
            }
        } else {
            logger.error("Not running the Docker Monitor since there was an error during configuration");
        }
        dashboardTask.run(metricMap.asMap().keySet());
        return null;
    }

    private void getDataFromUnixSocket() {
        try {
            Map<String, ?> config = configuration.getConfigYml();
            Map unixSocket = (Map) config.get("unixSocket");
            String commandFile = (String) unixSocket.get("commandFile");
            if (StringUtils.hasText(commandFile)) {
                File file = PathResolver.getFile(commandFile, AManagedMonitor.class);
                if (file != null && file.exists()) {
                    String serverNamePrefix = getServerNamePrefix(unixSocket);
                    DataFetcher dataFetcher = getUnixSocketDataFetcher(file);
                    getContainerStats(dataFetcher, serverNamePrefix);
                    getInfoStats(dataFetcher, serverNamePrefix);
                } else {
                    logger.error("NOT reading Unix Socket.The command file to read the data from unix socket is missing from {}"
                            , file != null ? file.getAbsolutePath() : file);
                }
            }
        } catch (Exception e) {
            logger.error("Unexpected error while getting the data from unix socket", e);
        }
    }

    private void getContainerStats(DataFetcher dataFetcher, String metricPrefix) {
        ArrayNode containers = dataFetcher.fetchData("/containers/json", ArrayNode.class, true);
        if (containers != null) {
            for (JsonNode container : containers) {
                String containerId = getStringValue("Id", container);
                String containerName = getContainerName(container);
                //Send the container Metrics
                String prefix = metricPrefix + containerName;
                printMetrics(container, getMetricConf("containers"), prefix);
                getContainerResourceStats(containerId, prefix, dataFetcher);
            }
            printCollectiveObservedCurrent(metricPrefix + "Summary|Running Container Count"
                    , String.valueOf(containers.size()));
        }
    }

    private void getInfoStats(DataFetcher dataFetcher, String metricPrefix) {
        JsonNode infoNode = dataFetcher.fetchData("/info", JsonNode.class, true);
        printMetrics(infoNode, getMetricConf("info"), metricPrefix + "Summary");
    }

    private void getContainerResourceStats(String containerId, String containerName, DataFetcher dataFetcher) {
        String resourcePath = "/containers/" + containerId + "/stats";
        JsonNode node = dataFetcher.fetchData(resourcePath, JsonNode.class, false);
        printMetrics(node, getMetricConf("containerStats"), containerName);

        if (node != null) {
            reportCPUPercentage(containerId, containerName, node);
            reportMemoryPercentage(containerId, containerName, node);
        }
    }

    private void reportMemoryPercentage(String containerId, String containerName, JsonNode node) {
        JsonNode stats = node.get("memory_stats");
        if (stats != null) {
            BigInteger usage = getBigIntegerValue("usage", stats);
            BigInteger limit = getBigIntegerValue("limit", stats);
            if (usage != null && limit != null) {
                printCollectiveObservedAverage(containerName + "|Memory|Current %", percentage(usage, limit));
            } else {
                logger.debug("Cannot calculate Memory %, usage={}, limit={}", usage, limit);
            }
        } else {
            logger.warn("The memory stats for container {} is not reported", containerId);
        }
    }

    private void reportCPUPercentage(String containerId, String containerName, JsonNode node) {
        JsonNode stats = node.get("cpu_stats");
        JsonNode cpuUsage;
        if (stats != null && (cpuUsage = stats.get("cpu_usage")) != null) {
            BigInteger totalUsage = getBigIntegerValue("total_usage", cpuUsage);
            BigInteger systemUsage = getBigIntegerValue("system_cpu_usage", stats);
            String totalUsageCacheKey = containerId + "|total_usage";
            String systemUsageCacheKey = containerId + "|system_cpu_usage";
            BigInteger prevTotalUsage = previousMetricsMap.getIfPresent(totalUsageCacheKey);
            BigInteger prevSystemUsage = previousMetricsMap.getIfPresent(systemUsageCacheKey);
            if (prevSystemUsage != null && prevTotalUsage != null && totalUsage != null && systemUsage != null) {
                logger.debug("Calculating the CPU usage % with, totalUsage={}, systemUsage={}, prevTotalUsage={}, prevSystemUsage={}"
                        , totalUsage, systemUsage, prevTotalUsage, prevSystemUsage);
                BigInteger totalCpuDiff = totalUsage.subtract(prevTotalUsage);
                BigInteger sysUsageDiff = systemUsage.subtract(prevSystemUsage);
                printCollectiveObservedAverage(containerName + "|CPU|Total %", percentage(totalCpuDiff, sysUsageDiff));
            } else {
                logger.warn("Cannot Calculate CPU %, some values are null, totalUsage={}, systemUsage={}, prevTotalUsage={}, prevSystemUsage={}"
                        , totalUsage, systemUsage, prevTotalUsage, prevSystemUsage);
            }
            if (totalUsage != null && systemUsage != null) {
                logger.debug("Adding data to the cache, totalUsage={}, systemUsage={}"
                        , totalUsage, systemUsage);
                previousMetricsMap.put(totalUsageCacheKey, totalUsage);
                previousMetricsMap.put(systemUsageCacheKey, systemUsage);
            }
        }
    }

    private static String percentage(BigInteger d1, BigInteger d2) {
        BigDecimal bigDecimal = new BigDecimal(d1).multiply(BIG_DECIMAL_100);
        return bigDecimal.divide(new BigDecimal(d2), 2, RoundingMode.HALF_UP).setScale(0, BigDecimal.ROUND_HALF_UP).toString();
    }

    protected void getDataFromTcpSockets() {
        Map<String, ?> config = configuration.getConfigYml();
        List tcpSockets = (List) config.get("tcpSockets");
        for (Object tcpSocket : tcpSockets) {
            try {
                getDataFromTcpSocket((Map) tcpSocket);
            } catch (Exception e) {
                logger.error("Unexpected error while fetching data from TCP socket ", e);
            }
        }
    }

    protected void getDataFromTcpSocket(Map tcpSocket) {
        String baseUrl = (String) tcpSocket.get("baseUrl");
        Map<String, String> argsMap = Collections.singletonMap(TaskInputArgs.URI, baseUrl);
        SimpleHttpClient client = buildSimpleHttpClient(argsMap);
        DataFetcher dataFetcher = getTcpSocketDataFetcher(argsMap, client);
        String serverName = getServerNamePrefix(tcpSocket);
        logger.info("Gathering the stats from the url [{}]. Server name is [{}]", baseUrl, serverName);
        try {
            getContainerStats(dataFetcher, serverName);
            getInfoStats(dataFetcher, serverName);
        } finally {
            client.close();
        }
    }


    private String getServerNamePrefix(Map socket) {
        String serverName = (String) socket.get("name");
        if (StringUtils.hasText(serverName)) {
            serverName = serverName + "|";
        } else {
            serverName = "";
        }
        return serverName;
    }

    protected void printMetrics(JsonNode node, List metricConfigs, String metricPrefix) {
        if (metricConfigs != null && node != null) {
            for (Object o : metricConfigs) {
                Map metric = (Map) o;
                //Get the First Entry which is the metric
                Map.Entry firstEntry = (Map.Entry) metric.entrySet().iterator().next();
                String confPropName = firstEntry.getKey().toString();
                Object confPropValue = firstEntry.getValue();
                if (confPropValue instanceof String) {
                    BigInteger value = getBigIntegerValue(confPropName, node);
                    Double multiplier = (Double) metric.get("multiplier");
                    String valueStr = multiply(value, multiplier);
                    String metricPath = appendMetricPath(metricPrefix, confPropValue.toString());
                    if (metricPath.contains("$$name")) {
                        metricPath = metricPath.replace("$$name", getStringValue("$$name", node));
                    }
                    printCollectiveObservedCurrent(metricPath, valueStr);
                } else if (confPropValue instanceof List) {
                    List subMetricConfigs = (List) confPropValue;
                    if ("$$name".equals(confPropName)) {
                        Iterator<Map.Entry<String, JsonNode>> fields = node.getFields();
                        while (fields.hasNext()) {
                            Map.Entry<String, JsonNode> field = fields.next();
                            String name = field.getKey();
                            ObjectNode subNode = (ObjectNode) field.getValue();
                            subNode.put(confPropName, name);
                            printMetrics(subNode, subMetricConfigs, metricPrefix);
                        }
                    } else {
                        JsonNode subNode = node.get(confPropName);
                        printMetrics(subNode, subMetricConfigs, metricPrefix);
                    }
                }
            }
        } else {
            if (metricConfigs == null) {
                logger.debug("The metrics are not configured for the response {}", node);
            }
            if (node == null) {
                logger.debug("The rest api returned no data for the config {}", metricConfigs);
            }
        }
    }

    private String appendMetricPath(String prefix, String... paths) {
        StringBuilder sb = new StringBuilder();
        sb.append(StringUtils.trim(prefix, "|")).append("|");
        if (paths != null) {
            for (int i = 0; i < paths.length; i++) {
                String path = paths[i];
                sb.append(StringUtils.trim(path, "|"));
                if (i != paths.length - 1) {
                    sb.append("|");
                }
            }
        }
        return sb.toString();
    }

    private String multiply(BigInteger value, Double multiplier) {
        if (value != null) {
            if (multiplier != null) {
                BigDecimal multiply = new BigDecimal(value).multiply(new BigDecimal(multiplier));
                return multiply.setScale(0, RoundingMode.HALF_UP).toString();
            } else {
                return new BigDecimal(value).toString();
            }
        }
        return null;
    }

    protected List getMetricConf(String name) {
        Map<String, ?> config = configuration.getConfigYml();
        Map metricsRoot = (Map) config.get("metrics");
        Object conf = metricsRoot.get(name);
        if (conf != null) {
            return (List) ((Map) conf).get("metrics");
        }
        return null;
    }

    protected String getContainerName(JsonNode container) {
        ArrayNode names = (ArrayNode) container.get("Names");
        if (names != null && names.size() > 0) {
            return names.get(0).getTextValue();
        } else {
            logger.debug("The names attribute of the container is empty {}. Using the Id instead", names);
            return getStringValue("Id", container);
        }
    }

    protected UnixSocketDataFetcher getUnixSocketDataFetcher(File file) {
        return new UnixSocketDataFetcher(file.getAbsolutePath());
    }

    protected TcpSocketDataFetcher getTcpSocketDataFetcher(Map<String, String> argsMap, SimpleHttpClient client) {
        return new TcpSocketDataFetcher(client, argsMap);
    }

    protected SimpleHttpClient buildSimpleHttpClient(Map<String, String> argsMap) {
        return new SimpleHttpClientBuilder(argsMap).build();
    }

    private String getStringValue(String propName, JsonNode node) {
        JsonNode jsonNode = node.get(propName);
        if (jsonNode != null) {
            return jsonNode.getTextValue();
        }
        return null;
    }

    private BigInteger getBigIntegerValue(String propName, JsonNode node) {
        JsonNode jsonNode = node.get(propName);
        if (jsonNode != null) {
            if (jsonNode instanceof BooleanNode) {
                BooleanNode boolNode = (BooleanNode) jsonNode;
                return boolNode.getBooleanValue() ? BigInteger.ONE : BigInteger.ZERO;
            } else {
                return jsonNode.getBigIntegerValue();
            }
        }
        return null;
    }

    public void printMetric(String metricName, String metricValue, String aggregationType, String timeRollup, String clusterRollup) {
        metricMap.put(metricName, "");
        String value;
        if (metricValue != null) {
            value = metricValue;
        } else {
            value = "0";
        }
        if (logger.isDebugEnabled()) {
            logger.debug("Sending [" + aggregationType + "/" + timeRollup + "/" + clusterRollup
                    + "] metric = " + metricName + " = " + value);
        }
        configuration.getMetricWriter()
                .printMetric(metricName, metricValue, aggregationType, timeRollup, clusterRollup);
    }

    protected void printCollectiveObservedCurrent(String metricName, String metricValue) {
        String metricPrefix = configuration.getMetricPrefix();
        printMetric(metricPrefix + "|" + metricName, metricValue,
                MetricWriter.METRIC_AGGREGATION_TYPE_OBSERVATION,
                MetricWriter.METRIC_TIME_ROLLUP_TYPE_CURRENT,
                MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_COLLECTIVE
        );
        List<String> perMinuteMetricSuffixes = (List<String>) configuration.getConfigYml().get("perMinuteMetricSuffixes");
        if (StringUtils.hasText(metricValue) && perMinuteMetricSuffixes != null) {
            for (String suffix : perMinuteMetricSuffixes) {
                if (metricName.endsWith(suffix)) {
                    BigInteger value = previousMetricsMap.getIfPresent(metricName);
                    if (value != null) {
                        BigInteger diff = new BigInteger(metricValue).subtract(value);
                        printCollectiveObservedAverage(metricName + " Per Minute", diff.toString());
                    }
                    previousMetricsMap.put(metricName, new BigInteger(metricValue));
                }
            }
        }
    }

    protected void printCollectiveObservedAverage(String metricName, String metricValue) {
        String metricPrefix = configuration.getMetricPrefix();
        printMetric(metricPrefix + "|" + metricName, metricValue,
                MetricWriter.METRIC_AGGREGATION_TYPE_OBSERVATION,
                MetricWriter.METRIC_TIME_ROLLUP_TYPE_AVERAGE,
                MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_COLLECTIVE
        );
    }

    public static String getImplementationVersion() {
        return DockerMonitor.class.getPackage().getImplementationTitle();
    }


}
