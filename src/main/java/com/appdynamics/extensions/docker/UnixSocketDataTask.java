package com.appdynamics.extensions.docker;

import com.appdynamics.extensions.AMonitorTaskRunnable;
import com.appdynamics.extensions.MetricWriteHelper;
import com.appdynamics.extensions.conf.MonitorContextConfiguration;
import com.appdynamics.extensions.logging.ExtensionsLoggerFactory;
import com.appdynamics.extensions.metrics.Metric;
import com.appdynamics.extensions.util.PathResolver;
import com.appdynamics.extensions.util.StringUtils;
import com.google.common.collect.Lists;
import com.singularity.ee.agent.systemagent.api.AManagedMonitor;
import org.slf4j.Logger;

import java.io.File;
import java.util.List;
import java.util.Map;

import static utility.Constants.*;

public class UnixSocketDataTask implements AMonitorTaskRunnable {

    private static final Logger logger = ExtensionsLoggerFactory.getLogger(UnixSocketDataTask.class);

    private MonitorContextConfiguration contextConfiguration;
    private MetricWriteHelper metricWriteHelper;
    private Map<String,?> configYml;
    private Map unixSocket;
    private String metricPrefix;

    public UnixSocketDataTask(MonitorContextConfiguration contextConfiguration, MetricWriteHelper metricWriteHelper, Map<String, ?> configYml, Map unixSocket){
        this.contextConfiguration=contextConfiguration;
        this.metricWriteHelper=metricWriteHelper;
        this.configYml=configYml;
        this.unixSocket=unixSocket;
        this.metricPrefix=contextConfiguration.getMetricPrefix() + SEPARATOR + unixSocket.get(NAME);
    }

    @Override
    public void run() {
        List<Metric> metricList = Lists.newArrayList();
        try{
            String path = (String) unixSocket.get(COMMAND_FILE);
            if(StringUtils.hasText(path)){
                File commandFile = PathResolver.getFile(path, AManagedMonitor.class);
                if(commandFile != null && commandFile.isFile() && commandFile.exists()){
                    UnixSocketDataFetcher dataFetcher = new UnixSocketDataFetcher(commandFile.getAbsolutePath());
                    DockerMetricProcessor processor = new DockerMetricProcessor(dataFetcher, configYml, metricPrefix);
                    metricList.addAll(processor.processMetrics());
                }
            }
        } catch(Exception e){
            logger.error("Error occurred while processing task for unix socket {}",unixSocket.get(NAME),e);
        } finally {
            metricWriteHelper.transformAndPrintMetrics(metricList);
        }
    }

    @Override
    public void onTaskComplete() {
        logger.info("Completed task for unix socket {}",unixSocket.get(NAME));
    }
}
