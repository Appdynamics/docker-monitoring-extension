/*
 *  Copyright 2015. AppDynamics LLC and its affiliates.
 *  All Rights Reserved.
 *  This is unpublished proprietary source code of AppDynamics LLC and its affiliates.
 *  The copyright notice above does not evidence any actual or intended publication of such source code.
 *
 */

package com.appdynamics.extensions.docker;

import com.appdynamics.extensions.http.HttpClientUtils;
import com.appdynamics.extensions.http.UrlBuilder;
import com.appdynamics.extensions.logging.ExtensionsLoggerFactory;
import com.appdynamics.extensions.util.StringUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpEntity;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Map;

/**
 * Created by abey.tom on 4/1/15.
 */
public class TcpSocketDataFetcher implements DataFetcher {
    public static final Logger logger = ExtensionsLoggerFactory.getLogger(TcpSocketDataFetcher.class);
    ObjectMapper objectMapper = new ObjectMapper();

    private CloseableHttpClient client;
    private Map argsMap;

    public TcpSocketDataFetcher(CloseableHttpClient client, Map argsMap) {
        this.client = client;
        this.argsMap = argsMap;
    }

    public <T> T fetchData(String resourcePath, Class<T> clazz, boolean readFully) {
        String path = UrlBuilder.builder(argsMap).path(resourcePath).build();
        if (readFully) {
            return HttpClientUtils.getResponseAsJson(client, path, clazz);
        } else {
            return getJsonPartial(path, clazz);
        }
    }

    private <T> T getJsonPartial(String url, Class<T> clazz) {
        logger.info("Invoking the url [{}]", url);
        HttpGet get = new HttpGet(url);
        CloseableHttpResponse response = null;
        try {
            response = client.execute(get);
            StatusLine statusLine;
            if (response != null && (statusLine = response.getStatusLine()) != null && statusLine.getStatusCode() == 200) {
                HttpEntity entity = response.getEntity();
                if (entity != null && entity.isStreaming() && entity.getContent() != null) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(entity.getContent()));
                    int count = 0;
                    while (++count <= 3) {
                        String jsonStr = br.readLine();
                        logger.debug("The url {} returned the streaming data {}",url,jsonStr);
                        if(StringUtils.hasText(jsonStr)){
                            return objectMapper.readValue(jsonStr,clazz);
                        } else {
                            logger.warn("No streaming data is returned from url {}",url);
                        }
                    }
                } else {
                    logger.warn("Entity content is null. Entity {}, content {}",entity,entity.getContent());
                }
            } else {
                printError(response,url);
            }
        } catch (Exception e) {
            logger.error("Error while fetching streaming response from url {}",url,e);
        } finally {
            try {
                response.close();
            } catch (IOException e) {
                logger.error("Error closing response for streaming url {}",url);
            }
        }
        return null;
    }

    public static void printError(CloseableHttpResponse response, String url) {
        if (response != null) {
            logger.error("The status line for the url [{}] is [{}] and the headers are [{}]"
                    , url, response.getStatusLine(), response.getAllHeaders());
            HttpEntity entity = response.getEntity();
            if (entity != null) {
                try {
                    logger.error("The contents are {}", EntityUtils.toString(response.getEntity()));
                } catch (Exception e) {
                    logger.error("", e);
                }
            } else {
                logger.error("The response content is null");
            }
        } else {
            logger.error("The response is null for the URL {}", url);
        }
    }
}
