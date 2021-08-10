package utility;

public class Constants {
    public static final String DEFAULT_METRIC_PREFIX = "Custom Metrics|Docker";
    public static final String MONITOR_NAME = "Docker Monitor";
    public static final String UNIX_SOCKET = "unixSocket";
    public static final String TCP_SOCKETS = "servers";
    public static final String SEPARATOR = "|";

    //config file constants
    public static final String NAME = "name";
    public static final String COMMAND_FILE = "commandFile";

    //config container naming
    public static final String CONTAINER_NAMING = "containerNaming";
    public static final String CONTAINER_NAMING_HOSTNAME = "${HOSTNAME}";
    public static final String CONTAINER_NAMING_CONTAINERID = "${CONTAINER_ID}";
    public static final String CONTAINER_NAMING_CONTAINERNAME = "${CONTAINER_NAME}";

    //URIs
    public static final String CONTAINERS_HOSTNAME_PATH = "/containers/%s/json";
    public static final String CONTAINERS_URI = "/containers/json";
    public static final String INFO_URI = "/info";
    public static final String CONTAINER_RESOURCE_STATS_URI = "/containers/%s/stats";

    //metrics
    public static final String METRICS = "metrics";
    public static final String CONTAINERS_METRICS = "containers";
    public static final String INFO_METRICS = "info";
    public static final String CONTAINER_RESOURCE_METRICS = "containerStats";
    public static final String CONTAINER_RESOURCE_NETWORK_METRICS = "networks";
    public static final String CONTAINER_RESOURCE_MEMORY_METRICS = "memory_stats";
    public static final String CONTAINER_RESOURCE_CPU_METRICS = "cpu_stats";
}
