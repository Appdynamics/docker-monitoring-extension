# Docker Monitoring Extension

## Use Case
Docker is an open platform for developers and sysadmins to build, ship, and run distributed applications.Docker Monitoring extension gathers metrics from the Docker Remote API, either using Unix Socket or TCP.

## Prerequisites
1. Before the extension is installed, the prerequisites mentioned [here](https://community.appdynamics.com/t5/Knowledge-Base/Extensions-Prerequisites-Guide/ta-p/35213) need to be met. Please do not proceed with the extension installation if the specified prerequisites are not met
2. Download and install [Apache Maven](https://maven.apache.org/) which is configured with `Java 8` to build the extension artifact from source. You can check the java version used in maven using command `mvn -v` or `mvn --version`. If your maven is using some other java version then please download java 8 for your platform and set JAVA_HOME parameter before starting maven.
3. Please go through the 1st section of the doc and review the configuration.
   https://docs.docker.com/reference/api/docker_remote_api/
3. The Stats API `GET /containers/(id)/stats` is available only from Docker API version 1.17 (Docker version ~1.8) onwards. If you are using an older version, the CPU Stats, Memory Stats and Network Stats will not be available.
4. *TCP Socket*: The docker daemon should be bound to the tcp socket. Please refer to this [document](https://docs.docker.com/engine/reference/commandline/dockerd/) for details. This is the command to bind the docker daemon to both TCP Socket and Unix Socket
   `sudo /path/to/docker daemon -H tcp://127.0.0.1:2375 -H unix:///var/run/docker.sock &`
5. *Unix Socket*: The extension will be able to fetch the data over the Unix Sockets if the CURL v 7.40+ is installed. Fetching data through the Unix Socket with older versions of CURL is not supported. To use this mode to collect the data, the machine agent should be run as the root user. If this is not possible, then the current user should have password-less sudo access or he should have access to the docker socket

## Installation
1. Clone the "docker-monitoring-extension" repo using `git clone <repoUrl>` command.
2. Run "mvn clean install" from "docker-monitoring-extension"
3. Unzip the contents of DockerMonitor-\<version\>.zip file (&lt;DockerMonitor&gt; / targets) and copy the directory to `<your-machine-agent-dir>/monitors`.
4. Edit config.yml file and provide the required configuration (see Configuration section)
5. Restart the Machine Agent.

Please place the extension in the **"monitors"** directory of your **Machine Agent** installation directory. Do not place the extension in the **"extensions"** directory of your **Machine Agent** installation directory.

## Configuration

### Config.yml
#### Configure metric prefix
Please follow section 2.1 of the [Document](https://community.appdynamics.com/t5/Knowledge-Base/How-do-I-troubleshoot-missing-custom-metrics-or-extensions/ta-p/28695) to set up metric prefix.
```
#Metric prefix used when SIM is enabled for your machine agent
#metricPrefix: Custom Metrics|Docker

#This will publish metrics to specific tier
#Instructions on how to retrieve the Component ID can be found in the Metric Prefix section of https://community.appdynamics.com/t5/Knowledge-Base/How-do-I-troubleshoot-missing-custom-metrics-or-extensions/ta-p/28695
metricPrefix: Server|Component:<TIER_ID>|Custom Metrics|Docker
```

#### Tcp socker servers or Unix socket configurations
Comment out properties which are not used

`Unix socket`
```
# This needs curl 7.40 or higher. If not, comment this and use TCP sockets.
unixSocket:
    commandFile: monitors/DockerMonitor/socket-command.sh
    name: UnixSocket
```
`Tcp socket servers`
```
servers:
  - uri: "http://127.0.0.1:2375"
    name: "Server1"
```
Multiple tcp socket servers can be configured like below
```
servers:
  - uri: "http://127.0.0.1:2375"
    name: "Server1"
  - uri: "http://127.0.0.1:2378"
    name: "Server2"    
```

#### Number of threads
Always include 1 thread per server + 1 to run main task

#### Configure metric section
The metrics shown in the file are customizable. You can choose to remove metrics or an entire section (containers, info etc) and they won't be reported. You can also add properties to individual metrics. The following properties can be added:
1. alias: The actual name of the metric as you would see it in the metric browser
2. multiplier: Used to transform the metric value, particularly for cases where memory is reported in bytes. 1.0 by default.
3. delta: Used to display a 'delta' or a difference between metrics that have an increasing value every minute. False by default.
4. clusterRollUpType: The cluster-rollup qualifier specifies how the Controller aggregates metric values in a tier (a cluster of nodes). The value is an enumerated type. Valid values are **INDIVIDUAL** (default) or **COLLECTIVE**.
5. aggregationType: The aggregator qualifier specifies how the Machine Agent aggregates the values reported during a one-minute period. Valid values are **AVERAGE** (default) or **SUM** or **OBSERVATION**.
6. timeRollUpType: The time-rollup qualifier specifies how the Controller rolls up the values when it converts from one-minute granularity tables to 10-minute granularity and 60-minute granularity tables over time. Valid values are **AVERAGE** (default) or **SUM** or **CURRENT**.
7. convert: Used to report a metric that is reporting text value by converting the value to its mapped integer

More details around this can be found [here](https://community.appdynamics.com/t5/Knowledge-Base/Extensions-Commons-Library-Metric-Transformers/ta-p/35413)

```
# List of metrics that needs to be fetched
metrics:
  containers:
    - name: SizeRw
      alias: SizeRw
      multiplier: "1"
      aggregationType: "AVERAGE"
      timeRollUpType: "AVERAGE"
      clusterRollUpType: "INDIVIDUAL"
      delta: "false"

    - name: SizeRootFs
      alias: SizeRootFs
      multiplier: "1"
      aggregationType: "AVERAGE"
      timeRollUpType: "AVERAGE"
      clusterRollUpType: "INDIVIDUAL"
      delta: "false"

    - name: State
      alias: State
      multiplier: "1"
      aggregationType: "AVERAGE"
      timeRollUpType: "AVERAGE"
      clusterRollUpType: "INDIVIDUAL"
      delta: "false"
      convert:
        "running": 1
        "restarting": 2
        "paused": 0

  info:
    - name: Containers
      alias: Container Count
      multiplier: "1"
      aggregationType: "AVERAGE"
      timeRollUpType: "AVERAGE"
      clusterRollUpType: "INDIVIDUAL"
      delta: "false"

    - name: Images
      alias: Image Count
      multiplier: "1"
      aggregationType: "AVERAGE"
      timeRollUpType: "AVERAGE"
      clusterRollUpType: "INDIVIDUAL"
      delta: "false"

    - name: MemTotal
      alias: Total Memory
      multiplier: "1"
      aggregationType: "AVERAGE"
      timeRollUpType: "AVERAGE"
      clusterRollUpType: "INDIVIDUAL"
      delta: "false"

    - name: MemoryLimit
      alias: MemoryLimit
      multiplier: "1"
      aggregationType: "AVERAGE"
      timeRollUpType: "AVERAGE"
      clusterRollUpType: "INDIVIDUAL"
      delta: "false"
      convert:
        "true": 1
        "false": 0

    - name: SwapLimit
      alias: SwapLimit
      multiplier: "1"
      aggregationType: "AVERAGE"
      timeRollUpType: "AVERAGE"
      clusterRollUpType: "INDIVIDUAL"
      delta: "false"
      convert:
        "true": 1
        "false": 0

  containerStats:
    networks:
      - name: rx_dropped
        alias: Receive|Dropped
        multiplier: "1"
        aggregationType: "AVERAGE"
        timeRollUpType: "AVERAGE"
        clusterRollUpType: "INDIVIDUAL"
        delta: "false"

      - name: rx_bytes
        alias: Receive|Bytes
        multiplier: "1"
        aggregationType: "AVERAGE"
        timeRollUpType: "AVERAGE"
        clusterRollUpType: "INDIVIDUAL"
        delta: "false"

      - name: rx_errors
        alias: Receive|Errors
        multiplier: "1"
        aggregationType: "AVERAGE"
        timeRollUpType: "AVERAGE"
        clusterRollUpType: "INDIVIDUAL"
        delta: "false"

      - name: rx_packets
        alias: Receive|Packets
        multiplier: "1"
        aggregationType: "AVERAGE"
        timeRollUpType: "AVERAGE"
        clusterRollUpType: "INDIVIDUAL"
        delta: "false"

      - name: tx_dropped
        alias: Transmit|Dropped
        multiplier: "1"
        aggregationType: "AVERAGE"
        timeRollUpType: "AVERAGE"
        clusterRollUpType: "INDIVIDUAL"
        delta: "false"

      - name: tx_bytes
        alias: Transmit|Bytes
        multiplier: "1"
        aggregationType: "AVERAGE"
        timeRollUpType: "AVERAGE"
        clusterRollUpType: "INDIVIDUAL"
        delta: "false"

      - name: tx_errors
        alias: Transmit|Errors
        multiplier: "1"
        aggregationType: "AVERAGE"
        timeRollUpType: "AVERAGE"
        clusterRollUpType: "INDIVIDUAL"
        delta: "false"

      - name: tx_packets
        alias: Transmit|Packets
        multiplier: "1"
        aggregationType: "AVERAGE"
        timeRollUpType: "AVERAGE"
        clusterRollUpType: "INDIVIDUAL"
        delta: "false"

    memory_stats:
      - name: max_usage
        alias: memory|Max Usage
        multiplier: "1"
        aggregationType: "AVERAGE"
        timeRollUpType: "AVERAGE"
        clusterRollUpType: "INDIVIDUAL"
        delta: "false"

      - name: usage
        alias: memory|Current Usage
        multiplier: "1"
        aggregationType: "AVERAGE"
        timeRollUpType: "AVERAGE"
        clusterRollUpType: "INDIVIDUAL"
        delta: "false"

      - name: limit
        alias: memory|Limit
        multiplier: "1"
        aggregationType: "AVERAGE"
        timeRollUpType: "AVERAGE"
        clusterRollUpType: "INDIVIDUAL"
        delta: "false"

    cpu_stats:
      - name: system_cpu_usage
        alias: cpu|System CPU Usage
        multiplier: "1"
        aggregationType: "AVERAGE"
        timeRollUpType: "AVERAGE"
        clusterRollUpType: "INDIVIDUAL"
        delta: "false"

      - name: usage_in_usermode
        alias: cpu|Usermode Usage
        multiplier: "1"
        aggregationType: "AVERAGE"
        timeRollUpType: "AVERAGE"
        clusterRollUpType: "INDIVIDUAL"
        delta: "false"

      - name: usage_in_kernelmode
        alias: cpu|Kernelmode Usage
        multiplier: "1"
        aggregationType: "AVERAGE"
        timeRollUpType: "AVERAGE"
        clusterRollUpType: "INDIVIDUAL"
        delta: "false"

      - name: total_usage
        alias: cpu|Total Usage
        multiplier: "1"
        aggregationType: "AVERAGE"
        timeRollUpType: "AVERAGE"
        clusterRollUpType: "INDIVIDUAL"
        delta: "false"
```

##### Yml Validation
Please copy all the contents of the config.yml file and go [here](https://jsonformatter.org/yaml-validator) . On reaching the website, paste the contents and press the “Go” button on the bottom left.

## Workbench
Workbench is an inbuilt feature provided with each extension in order to assist you to fine tune the extension setup before you actually deploy it on the controller. Please review the following document on [How to use the Extensions WorkBench](https://community.appdynamics.com/t5/Knowledge-Base/How-to-use-the-Extensions-WorkBench/ta-p/30130)

## Troubleshooting
1. Please follow the steps listed in this [troubleshooting-document](https://community.appdynamics.com/t5/Knowledge-Base/How-to-troubleshoot-missing-custom-metrics-or-extensions-metrics/ta-p/28695) in order to troubleshoot your issue. These are a set of common issues that customers might have faced during the installation of the extension.
2. Verify Machine Agent Data: Please start the Machine Agent without the extension and make sure that it reports data. Verify that the machine agent status is UP and it is reporting Hardware Metrics
3. *Unix Socket*: This needs curl v7.40+. To troubleshoot unix socket, please try to run this command and see if it returns a valid JSON data in addition to the http headers. If needed, change the API from `/containers/id/stats` to whichever one you want to test.
 
   `sudo curl -s -S -i --unix-socket /var/run/docker.sock  http:/containers/$CONTAINER_ID/stats`

   Please update the `$CONTAINER_ID`. If this API doesn't return any JSON data in the response in addition to http headers or if any other issues are observed while fetching  data via Unix socket, then you will have to enable TCP sockets. Please refer to Prerequisites #3 on how to enable TCP Sockets. Then modify the config.yml and uncomment the servers section accordingly. Comment out the unixSocket section in config.yml
4. *TCP Socket*: To troubleshoot TCP, make sure that the following commands returns a valid JSON output
```
curl -v http://<host>:<port>/containers/json
curl -v http://<host>:<port>/info
```
For e.g.,
```
>curl -v http://localhost:2375/info

{"ID":"V5K5:64C4:CVUG:RHV2:SMM6:BJXZ:MFRM:25XF:U7F7:IQAX:TH63:OQPW","Containers":5,"ContainersRunning":2,"ContainersPaused":0,
"ContainersStopped":3,"Images":4,"Driver":"overlay2","DriverStatus":[["Backing Filesystem","extfs"],["Supports d_type","true"],
["Native Overlay Diff","true"],["userxattr","false"]],
---Response is truncated---
```

## Known Issues
The extension doesn't work with the newer versions Docker Unix Socket. Please enable the TCP socket. For more details on how to bind TCP socket to docker daemon, please refer to the Prerequisites #4

## Contributing
Always feel free to fork and contribute any changes directly via [GitHub](https://github.com/Appdynamics/docker-monitoring-extension)

## Version
|          Name            |  Version   |
|--------------------------|------------|
|Extension Version         |2.0.0       |
|Product Tested on         |Server: Docker Engine - Community: Version: 20.10.8 and API version: 1.41|
|Last Update               |04/08/2021  |
|Change List               |[ChangeLog](https://github.com/Appdynamics/docker-monitoring-extension/blob/master/CHANGELOG.md)|

**Note**: While extensions are maintained and supported by customers under the open-source licensing model, they interact with agents and Controllers that are subject to [AppDynamics’ maintenance and support policy](https://docs.appdynamics.com/latest/en/product-and-release-announcements/maintenance-support-for-software-versions). Some extensions have been tested with AppDynamics 4.5.13+ artifacts, but you are strongly recommended against using versions that are no longer supported.
