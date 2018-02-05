/*
 *  Copyright 2015. AppDynamics LLC and its affiliates.
 *  All Rights Reserved.
 *  This is unpublished proprietary source code of AppDynamics LLC and its affiliates.
 *  The copyright notice above does not evidence any actual or intended publication of such source code.
 *
 */

package com.appdynamics.extensions.docker;

/**
 * Created by abey.tom on 4/1/15.
 */
public interface DataFetcher {
    <T> T fetchData(String resourcePath, Class<T> clazz, boolean readFully);
}
