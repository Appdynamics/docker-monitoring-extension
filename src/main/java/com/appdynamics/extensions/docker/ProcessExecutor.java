/*
 *  Copyright 2015. AppDynamics LLC and its affiliates.
 *  All Rights Reserved.
 *  This is unpublished proprietary source code of AppDynamics LLC and its affiliates.
 *  The copyright notice above does not evidence any actual or intended publication of such source code.
 *
 */

package com.appdynamics.extensions.docker;

import com.appdynamics.extensions.logging.ExtensionsLoggerFactory;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.impl.io.DefaultHttpResponseParser;
import org.apache.hc.core5.http.impl.io.SessionInputBufferImpl;
import org.apache.hc.core5.http.io.SessionInputBuffer;
import org.apache.hc.core5.http.message.BasicLineParser;
import org.apache.hc.core5.http.message.LineParser;
import org.apache.hc.core5.http.message.StatusLine;
import org.apache.hc.core5.util.CharArrayBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Executes the Command, reads the data from the OutputStream and returns it.
 * <p/>
 * Created by abey.tom on 3/31/15.
 */
public class ProcessExecutor {
    public static final Logger logger = ExtensionsLoggerFactory.getLogger(ProcessExecutor.class);

    public String execute(String[] commands, boolean readFully) {
        try {
            logger.info("Executing the command {}", Arrays.toString(commands));
            Process process = Runtime.getRuntime().exec(commands);
            new ErrorReader(process.getErrorStream()).start();
            ResponseParser responseParser = new ResponseParser(process, commands, readFully);
            responseParser.start();
            process.waitFor();
            responseParser.join();
            return responseParser.getData();
        } catch (Exception e) {
            logger.error("Error while executing the process " + Arrays.toString(commands), e);
            return null;
        }
    }

    /**
     * Reads the data from the output stream.
     */
    public static class ResponseParser extends Thread {

        private Process process;
        private String[] commands;
        private boolean readFully;
        private String data;

        public ResponseParser(Process process, String[] commands, boolean readFully) {
            this.process = process;
            this.commands = commands;
            this.readFully = readFully;
        }

        public void run() {
            InputStream inputStream = process.getInputStream();
            CharsetDecoder utf8Decoder = StandardCharsets.UTF_8.newDecoder();
            SessionInputBuffer sessionInputBuffer = new SessionInputBufferImpl(256, utf8Decoder);
            LineParser lineParser = new BasicLineParser();
            CharArrayBuffer buffer = new CharArrayBuffer(256);

            try {
                sessionInputBuffer.readLine(buffer, inputStream);
                StatusLine statusLine = lineParser.parseStatusLine(buffer);
                if (statusLine.getStatusCode() == 200) {
                    Header[] headers = DefaultHttpResponseParser.parseHeaders(sessionInputBuffer,
                            inputStream, 0, 0, lineParser);
                    if (logger.isDebugEnabled()) {
                        logger.debug("The response headers are {}", Arrays.toString(headers));
                    }
                    if (readFully) {
                        StringBuilder sb = new StringBuilder();
                        while (true) {
                            buffer.clear();
                            int count = sessionInputBuffer.readLine(buffer, inputStream);
                            if (count == -1) {
                                break;
                            }
                            sb.append(buffer);
                        }
                        data = sb.toString();
                    } else {
                        data = null;
                        buffer.clear();
                        int count = sessionInputBuffer.readLine(buffer, inputStream);
                        if (count != -1) {
                            data = buffer.toString();
                        }
                        logger.debug("Read Single line of response data is {}", data);
                    }

                } else {
                    StringBuilder sb = new StringBuilder(statusLine.toString()).append("\n");
                    Header[] headers = DefaultHttpResponseParser.parseHeaders(sessionInputBuffer,
                            inputStream, 0, 0, lineParser);
                    for (Header header : headers) {
                        sb.append(header.getName()).append(": ").append(header.getValue()).append("\n");
                    }

                    int lines = 0;
                    while (true) {
                        buffer.clear();
                        int count = sessionInputBuffer.readLine(buffer, inputStream);
                        if (count == -1) {
                            break;
                        }
                        sb.append(buffer).append("\n");

                        if (++lines > 20) {
                            logger.warn("Truncating the response body to 20 lines");
                            break;
                        }
                    }
                    logger.error("The command {} returned error response {}", Arrays.toString(commands), sb);
                }
            } catch (Exception e) {
                logger.error("Error while executing the command " + Arrays.toString(commands), e);
            } finally {
                try {
                    inputStream.close();
                } catch (IOException e) {
                }
                if (logger.isDebugEnabled()) {
                    logger.debug("Destroying the process {}", Arrays.toString(commands));
                }
                process.destroy();
            }

        }

        public String getData() {
            return data;
        }
    }


    /**
     * Listens to the Error Stream and logs the response.
     */
    public static class ErrorReader extends Thread {
        public static final Logger logger = LoggerFactory.getLogger(ErrorReader.class);


        private final InputStream in;

        public ErrorReader(InputStream in) {
            this.in = in;
        }

        @Override
        public void run() {
            BufferedReader reader = new BufferedReader(new InputStreamReader(in));
            String temp;
            try {
                while ((temp = reader.readLine()) != null) {
                    logger.error("Process Error - {}", temp);
                }
            } catch (IOException e) {
                logger.debug("Error while reading the contents of the the error stream", e);
            } finally {
                try {
                    reader.close();
                } catch (IOException e) {
                }
            }
            logger.trace("Closing the Error Reader {}", Thread.currentThread().getName());
        }
    }

    public interface DataListener {
        void onData(String data);
    }
}



