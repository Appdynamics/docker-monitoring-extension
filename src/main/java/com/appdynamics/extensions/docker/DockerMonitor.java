package com.appdynamics.extensions.docker;

import com.appdynamics.TaskInputArgs;
import com.appdynamics.extensions.PathResolver;
import com.appdynamics.extensions.StringUtils;
import com.appdynamics.extensions.http.SimpleHttpClient;
import com.appdynamics.extensions.http.SimpleHttpClientBuilder;
import com.appdynamics.extensions.util.FileWatcher;
import com.singularity.ee.agent.systemagent.api.AManagedMonitor;
import com.singularity.ee.agent.systemagent.api.MetricWriter;
import com.singularity.ee.agent.systemagent.api.TaskExecutionContext;
import com.singularity.ee.agent.systemagent.api.TaskOutput;
import com.singularity.ee.agent.systemagent.api.exception.TaskExecutionException;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.node.ArrayNode;
import org.codehaus.jackson.node.BooleanNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Created by abey.tom on 3/30/15.
 */
public class DockerMonitor extends AManagedMonitor {
    public static final Logger logger = LoggerFactory.getLogger(DockerMonitor.class);
    public static final String METRIC_PREFIX = "Custom Metrics|Docker|";

    protected boolean initialized;
    protected Map config;
    private String metricPrefix;

    public DockerMonitor() {
        String msg = "Using Monitor Version [" + getImplementationVersion() + "]";
        logger.info(msg);
        System.out.println(msg);
    }

    protected void initialize(Map<String, String> argsMap) {
        if (!initialized) {
            final File file = PathResolver.getFile(argsMap.get("config-file"), AManagedMonitor.class);
            if (file != null && file.exists()) {
                config = readYml(file);
                setMetricPrefix();
                initialized = true;
            } else {
                logger.error("Config file is not found.The config file path {} is resolved to {}",
                        argsMap.get("config-file"), file != null ? file.getAbsolutePath() : null);
            }
            if (file != null) {
                //Create a File watcher to auto reload the config
                FileWatcher.watch(file, new FileWatcher.FileChangeListener() {
                    public void fileChanged() {
                        logger.info("The file " + file.getAbsolutePath() + " has changed, reloading the config");
                        config = readYml(file);
                        setMetricPrefix();
                    }
                });
            }
        }
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
        if (initialized) {
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
        return null;
    }

    private void getDataFromUnixSocket() {
        try {
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
    }

    protected void getDataFromTcpSockets() {
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

    private void printMetrics(JsonNode node, List metricConfigs, String metricPrefix) {
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
                    printCollectiveObservedCurrent(metricPath, valueStr);
                } else if (confPropValue instanceof List) {
                    List subMetricConfigs = (List) confPropValue;
                    JsonNode subNode = node.get(confPropName);
                    printMetrics(subNode, subMetricConfigs, metricPrefix);
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

    private List getMetricConf(String name) {
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

    public void printMetric(String metricName, String metricValue, String aggregation, String timeRollup, String cluster) {
        MetricWriter metricWriter = getMetricWriter(metricName,
                aggregation,
                timeRollup,
                cluster
        );
        String value;
        if (metricValue != null) {
            value = metricValue;
        } else {
            value = "0";
        }
        if (logger.isDebugEnabled()) {
            logger.debug("Sending [" + aggregation + "/" + timeRollup + "/" + cluster
                    + "] metric = " + metricName + " = " + value);
        }
        metricWriter.printMetric(value);
    }

    protected void printCollectiveObservedCurrent(String metricName, String metricValue) {
        printMetric(metricPrefix + metricName, metricValue,
                MetricWriter.METRIC_AGGREGATION_TYPE_OBSERVATION,
                MetricWriter.METRIC_TIME_ROLLUP_TYPE_CURRENT,
                MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_COLLECTIVE
        );
    }

    protected void setMetricPrefix() {
        if (config != null) {
            String prefix = (String) config.get("metricPrefix");
            logger.debug("The metric prefix from the config file is {}", prefix);
            if (StringUtils.hasText(prefix)) {
                prefix = StringUtils.trim(prefix, "|");
                metricPrefix = prefix + "|";
            } else {
                metricPrefix = METRIC_PREFIX;
            }
            logger.info("The metric prefix is initialized as {}", metricPrefix);
        }
    }

    public static String getImplementationVersion() {
        return DockerMonitor.class.getPackage().getImplementationTitle();
    }
}
