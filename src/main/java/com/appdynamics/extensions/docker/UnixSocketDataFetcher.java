package com.appdynamics.extensions.docker;

import com.appdynamics.extensions.StringUtils;
import org.codehaus.jackson.map.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Arrays;

/**
 * Created by abey.tom on 4/1/15.
 */
public class UnixSocketDataFetcher implements DataFetcher {
    public static final Logger logger = LoggerFactory.getLogger(UnixSocketDataFetcher.class);
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
