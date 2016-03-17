/*
 * CODENVY CONFIDENTIAL
 * __________________
 *
 *  [2012] - [2015] Codenvy, S.A.
 *  All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains
 * the property of Codenvy S.A. and its suppliers,
 * if any.  The intellectual and technical concepts contained
 * herein are proprietary to Codenvy S.A.
 * and its suppliers and may be covered by U.S. and Foreign Patents,
 * patents in process, and are protected by trade secret or copyright law.
 * Dissemination of this information or reproduction of this material
 * is strictly forbidden unless prior written permission is obtained
 * from Codenvy S.A..
 */
package com.codenvy.analytics.metrics.tasks.runs;

import com.codenvy.analytics.metrics.AbstractLongValueResulted;
import com.codenvy.analytics.metrics.MetricType;

/**
 * @author Alexander Reshetnyak
 */
public class RunQueueTerminations extends AbstractLongValueResulted {

    public RunQueueTerminations() {
        super(MetricType.RUN_QUEUE_TERMINATIONS, TASK_ID);
    }

    /** {@inheritDoc} */
    @Override
    public String getDescription() {
        return "The number of run terminations due to threshold timeout";
    }
}
