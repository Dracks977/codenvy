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
package com.codenvy.analytics.services.reports;

import com.codenvy.analytics.metrics.Context;
import com.codenvy.analytics.services.configuration.ParameterConfiguration;

import java.io.IOException;
import java.util.List;
import java.util.Set;

/**
 * All emails are placed into configuration under parameters with 'e-mail' key.
 *
 * @author <a href="mailto:abazko@codenvy.com">Anatoliy Bazko</a>
 */
public class ItemizedRecipientGroup extends AbstractRecipientGroup {

    private static final String E_MAIL = "e-mail";

    public ItemizedRecipientGroup(List<ParameterConfiguration> parameters) {
        super(parameters);
    }

    @Override
    public Set<String> getEmails(Context context) throws IOException {
        return getParameters(E_MAIL);
    }
}
