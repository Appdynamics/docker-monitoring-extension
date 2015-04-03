package com.appdynamics.extensions.docker;

/**
 * Created by abey.tom on 4/1/15.
 */
public interface DataFetcher {
    <T> T fetchData(String resourcePath, Class<T> clazz, boolean readFully);
}
