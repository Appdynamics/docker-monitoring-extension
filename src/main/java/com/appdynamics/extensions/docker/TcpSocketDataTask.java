package com.appdynamics.extensions.docker;

import com.appdynamics.extensions.AMonitorTaskRunnable;
import com.appdynamics.extensions.MetricWriteHelper;
import com.appdynamics.extensions.conf.MonitorContextConfiguration;
import com.appdynamics.extensions.logging.ExtensionsLoggerFactory;
import com.appdynamics.extensions.metrics.Metric;
import com.google.common.collect.Lists;
import org.slf4j.Logger;

import java.util.List;
import java.util.Map;

import static utility.Constants.*;

public class TcpSocketDataTask implements AMonitorTaskRunnable {

    private static final Logger logger = ExtensionsLoggerFactory.getLogger(TcpSocketDataTask.class);
    private MonitorContextConfiguration contextConfiguration;
    private MetricWriteHelper metricWriteHelper;
    private Map<String,?> configYml;
    private Map tcpSocket;
    private String metricPrefix;



    public TcpSocketDataTask(MonitorContextConfiguration contextConfiguration, MetricWriteHelper metricWriteHelper, Map<String, ?> configYml, Map tcpSocket){
        this.contextConfiguration=contextConfiguration;
        this.metricWriteHelper=metricWriteHelper;
        this.configYml=configYml;
        this.tcpSocket=tcpSocket;
        this.metricPrefix=contextConfiguration.getMetricPrefix() + SEPARATOR + tcpSocket.get(NAME);

    }

    @Override
    public void run() {
        List<Metric> metricList = Lists.newArrayList();
        try{
            TcpSocketDataFetcher dataFetcher = new TcpSocketDataFetcher(contextConfiguration.getContext().getHttpClient(),tcpSocket);
            DockerMetricProcessor processor = new DockerMetricProcessor(dataFetcher, configYml, metricPrefix);
            metricList.addAll(processor.processMetrics());
        } catch(Exception e){
            logger.error("Error occurred while processing task for tcp socket server {}",tcpSocket.get(NAME),e);
        } finally {
            metricWriteHelper.transformAndPrintMetrics(metricList);
        }
    }

    @Override
    public void onTaskComplete() {
        logger.info("Completed task for tcp socket server {}",tcpSocket.get(NAME));
    }
}
