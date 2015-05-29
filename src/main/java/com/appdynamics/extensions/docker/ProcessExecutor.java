package com.appdynamics.extensions.docker;

import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;

/**
 * Executes the Command, reads the data from the OutputStream and returns it.
 * <p/>
 * Created by abey.tom on 3/31/15.
 */
public class ProcessExecutor {
    public static final Logger logger = LoggerFactory.getLogger(ProcessExecutor.class);

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
            try {
                String statusLine = HttpParser.readLine(inputStream, "UTF-8");
                logger.debug("The status line is {}", statusLine);
                if (statusLine.contains("200 OK")) {
                    Header[] headers = HttpParser.parseHeaders(inputStream, "UTF-8");
                    if (logger.isDebugEnabled()) {
                        logger.debug("The response headers are {}", Arrays.toString(headers));
                    }
                    if (readFully) {
                        String temp;
                        StringBuilder sb = new StringBuilder();
                        while ((temp = HttpParser.readLine(inputStream, "UTF-8")) != null) {
                            sb.append(temp);
                            logger.debug("Reading chunks of data {}", temp);
                        }
                        data = sb.toString();
                    } else {
                        data = HttpParser.readLine(inputStream, "UTF-8");
                        logger.debug("Read Single line of response data is {}", data);
                    }

                } else {
                    printErrorDetails(inputStream, statusLine);
                }
            } catch (IOException e) {
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

        private void printErrorDetails(InputStream inputStream, String statusLine) throws IOException {
            StringBuilder sb = new StringBuilder(statusLine).append("\n");
            Header[] headers = HttpParser.parseHeaders(inputStream, "UTF-8");
            for (Header header : headers) {
                sb.append(header.getName()).append(": ").append(header.getValue()).append("\n");
            }
            String temp;
            int count = 0;
            while ((temp = HttpParser.readLine(inputStream, "UTF-8")) != null) {
                sb.append(temp).append("\n");
                if (++count > 20) {
                    logger.warn("Truncating the response body to 20 lines");
                    break;
                }
            }
            logger.error("The command {} returned error response {}", Arrays.toString(commands), sb);
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



