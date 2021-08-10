/*
 *  Copyright 2015. AppDynamics LLC and its affiliates.
 *  All Rights Reserved.
 *  This is unpublished proprietary source code of AppDynamics LLC and its affiliates.
 *  The copyright notice above does not evidence any actual or intended publication of such source code.
 *
 */

package com.appdynamics.extensions.docker;

import com.appdynamics.extensions.logging.ExtensionsLoggerFactory;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/**
 * Created by abey.tom on 4/3/15.
 */
public class UnixSocketDataFetcherTest {
    public static final Logger logger = ExtensionsLoggerFactory.getLogger(UnixSocketDataFetcherTest.class);

    @Test
    public void multilineResponseTest() throws IOException {
        if (isUnix()) {
            File file = copyAndGetPath("/raw/multiline.sh");
            UnixSocketDataFetcher fetcher = new UnixSocketDataFetcher(file.getAbsolutePath());
            JsonNode jsonNode = fetcher.fetchData("/info", JsonNode.class, true);
            Assert.assertEquals(4,jsonNode.get("Containers").asInt());
        } else {
            logger.warn("Unsupported OS for this test case");
        }
    }
    @Test
    public void singleLineResponseTest() throws IOException {
        if (isUnix()) {
            File file = copyAndGetPath("/raw/singleline.sh");
            UnixSocketDataFetcher fetcher = new UnixSocketDataFetcher(file.getAbsolutePath());
            JsonNode jsonNode = fetcher.fetchData("/info", JsonNode.class, false);
            Assert.assertEquals(4,jsonNode.get("Containers").asInt());
        } else {
            logger.warn("Unsupported OS for this test case");
        }
    }

    @Test
    public void errorResponseTest() throws IOException {
        if (isUnix()) {
            File file = copyAndGetPath("/raw/error.sh");
            UnixSocketDataFetcher fetcher = new UnixSocketDataFetcher(file.getAbsolutePath());
            JsonNode jsonNode = fetcher.fetchData("/info", JsonNode.class, false);
            Assert.assertNull(jsonNode);
        } else {
            logger.warn("Unsupported OS for this test case");
        }
    }

    @Test
    public void invalidResponseTest() throws IOException {
        if (isUnix()) {
            File file = copyAndGetPath("/raw/invalid.sh");
            UnixSocketDataFetcher fetcher = new UnixSocketDataFetcher(file.getAbsolutePath());
            JsonNode jsonNode = fetcher.fetchData("/info", JsonNode.class, false);
            Assert.assertNull(jsonNode);
        } else {
            logger.warn("Unsupported OS for this test case");
        }
    }

    private File copyAndGetPath(String path) throws IOException {
        InputStream in = getClass().getResourceAsStream(path);
        File file = new File(System.getProperty("java.io.tmpdir"), path);
        if (!file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }
        if (file.exists()) {
            FileUtils.deleteQuietly(file);
        }
        FileUtils.copyInputStreamToFile(in, file);
        Runtime.getRuntime().exec(new String[]{"chmod", "+x", file.getAbsolutePath()});
        return file;
    }

    private boolean isUnix() {
        String property = System.getProperty("os.name");
        return property != null &&
                (property.toLowerCase().contains("mac")
                        || property.toLowerCase().contains("nix"));

    }


}
