/*
 * Copyright (C) 2013 Codenvy.
 */
package com.codenvy.analytics.metrics;

/** @author <a href="mailto:abazko@codenvy.com">Anatoliy Bazko</a> */
public class UsersDeployedOnceMetric extends ToDateValueReadBasedMetric {

    public UsersDeployedOnceMetric() {
        super(MetricType.USERS_DEPLOYED_ONCE);
    }
}
