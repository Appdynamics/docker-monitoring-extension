/*
 *  Copyright 2015. AppDynamics LLC and its affiliates.
 *  All Rights Reserved.
 *  This is unpublished proprietary source code of AppDynamics LLC and its affiliates.
 *  The copyright notice above does not evidence any actual or intended publication of such source code.
 *
 */

package com.appdynamics.extensions.docker;

import com.appdynamics.extensions.logging.ExtensionsLoggerFactory;
import com.appdynamics.extensions.util.StringUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.Arrays;

/**
 * Created by abey.tom on 4/1/15.
 */
public class UnixSocketDataFetcher implements DataFetcher {
    public static final Logger logger = ExtensionsLoggerFactory.getLogger(UnixSocketDataFetcher.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final ProcessExecutor processExecutor = new ProcessExecutor();

    private String commandFile;

    public UnixSocketDataFetcher(String commandFile) {
        this.commandFile = commandFile;
    }

    public <T> T fetchData(String resourcePath, Class<T> clazz, boolean readFully) {
        String[] commands = {commandFile, resourcePath};
        String json = processExecutor.execute(commands, readFully);
        if (StringUtils.hasText(json)) {
            try {
                return objectMapper.readValue(json, clazz);
            } catch (IOException e) {
                logger.error("Error while mapping the response of " + resourcePath + " is " + json, e);
                return null;
            }
        } else {
            logger.error("The command {} returned [{}]", Arrays.toString(commands), json);
            return null;
        }
    }
}
