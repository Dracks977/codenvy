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
package com.codenvy.api.subscription.saas.server.limit;

import com.codenvy.api.subscription.saas.server.AccountLocker;
import com.codenvy.api.subscription.saas.server.WorkspaceLocker;
import com.codenvy.api.metrics.server.period.MetricPeriod;
import com.codenvy.api.subscription.saas.server.billing.BillingService;
import com.codenvy.api.metrics.server.dao.MeterBasedStorage;
import com.codenvy.api.subscription.server.dao.Subscription;
import com.codenvy.api.subscription.server.dao.SubscriptionDao;

import org.eclipse.che.api.core.NotFoundException;
import org.eclipse.che.api.core.ServerException;
import org.eclipse.che.api.workspace.server.dao.Workspace;
import org.eclipse.che.api.workspace.server.dao.WorkspaceDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Map;

import static com.codenvy.api.subscription.saas.server.SaasSubscriptionService.SAAS_SUBSCRIPTION_ID;
import static org.eclipse.che.api.workspace.server.Constants.RESOURCES_USAGE_LIMIT_PROPERTY;

/**
 * @author Sergii Leschenko
 */
@Singleton
public class ResourcesWatchdogFactory {
    private final SubscriptionDao   subscriptionDao;
    private final MetricPeriod      metricPeriod;
    private final BillingService    billingService;
    private final WorkspaceDao      workspaceDao;
    private final MeterBasedStorage meterBasedStorage;
    private final AccountLocker     accountLocker;
    private final WorkspaceLocker   workspaceLocker;

    @Inject
    public ResourcesWatchdogFactory(SubscriptionDao subscriptionDao,
                                    MetricPeriod metricPeriod,
                                    BillingService billingService,
                                    WorkspaceDao workspaceDao,
                                    MeterBasedStorage meterBasedStorage,
                                    AccountLocker accountLocker,
                                    WorkspaceLocker workspaceLocker) {
        this.subscriptionDao = subscriptionDao;
        this.metricPeriod = metricPeriod;
        this.billingService = billingService;
        this.workspaceDao = workspaceDao;
        this.meterBasedStorage = meterBasedStorage;
        this.accountLocker = accountLocker;
        this.workspaceLocker = workspaceLocker;
    }

    public ResourcesWatchdog createAccountWatchdog(final String accountId) {
        AccountResourcesWatchdog accountResourcesWatchdog = new AccountResourcesWatchdog(accountId);
        accountResourcesWatchdog.checkLimit();
        return accountResourcesWatchdog;
    }

    public ResourcesWatchdog createWorkspaceWatchdog(final String workspaceId) {
        WorkspaceResourcesWatchdog workspaceResourcesWatchdog = new WorkspaceResourcesWatchdog(workspaceId);
        workspaceResourcesWatchdog.checkLimit();
        return workspaceResourcesWatchdog;
    }

    class AccountResourcesWatchdog implements ResourcesWatchdog {
        private final Logger LOG = LoggerFactory.getLogger(AccountResourcesWatchdog.class);

        private final String  accountId;
        private       boolean hasLimit;

        public AccountResourcesWatchdog(String id) {
            this.accountId = id;
        }

        @Override
        public String getId() {
            return accountId;
        }

        @Override
        public boolean isLimitedReached() {
            if (!hasLimit) {
                return false;
            }

            try {
                return !billingService.hasAvailableResources(accountId,
                                                             metricPeriod
                                                                     .getCurrent()
                                                                     .getStartDate()
                                                                     .getTime(),
                                                             System.currentTimeMillis());
            } catch (ServerException e) {
                LOG.error("Can't check resources consuming in account " + accountId, e);
            }

            return false;
        }

        @Override
        public void checkLimit() {
            try {
                final Subscription activeSaasSubscription = subscriptionDao.getActiveByServiceId(accountId, SAAS_SUBSCRIPTION_ID);
                if (activeSaasSubscription == null) {
                    hasLimit = true;
                    return;
                }
            } catch (ServerException | NotFoundException e) {
                LOG.error("Can't check resources usage limit in account " + accountId, e);
            }

            hasLimit = false;
        }

        @Override
        public void lock() {
            accountLocker.setResourcesLock(accountId);
        }
    }

    class WorkspaceResourcesWatchdog implements ResourcesWatchdog {
        private final Logger LOG = LoggerFactory.getLogger(WorkspaceResourcesWatchdog.class);

        private final String  workspaceId;
        private       boolean hasLimit;
        private       double  resourcesUsageLimit;

        public WorkspaceResourcesWatchdog(String id) {
            this.workspaceId = id;
            checkLimit();
        }

        @Override
        public String getId() {
            return workspaceId;
        }

        @Override
        public boolean isLimitedReached() {
            if (!hasLimit) {
                return false;
            }

            try {
                long billingPeriodStart = metricPeriod.getCurrent().getStartDate().getTime();
                Double usedMemory = meterBasedStorage.getUsedMemoryByWorkspace(workspaceId, billingPeriodStart, System.currentTimeMillis());
                return usedMemory > resourcesUsageLimit;
            } catch (ServerException e) {
                LOG.error("Can't check resources consuming in workspace " + workspaceId, e);
            }

            return false;
        }

        @Override
        public void checkLimit() {
            try {
                Workspace workspace = workspaceDao.getById(workspaceId);
                final Map<String, String> attributes = workspace.getAttributes();
                if (attributes.containsKey(RESOURCES_USAGE_LIMIT_PROPERTY)) {
                    resourcesUsageLimit = Double.parseDouble(attributes.get(RESOURCES_USAGE_LIMIT_PROPERTY));
                    hasLimit = true;
                } else {
                    hasLimit = false;
                }
            } catch (NotFoundException | ServerException e) {
                hasLimit = false;
                LOG.error("Can't check resources usage limit in workspace " + workspaceId, e);
            }
        }

        @Override
        public void lock() {
            workspaceLocker.setResourcesLock(workspaceId);
        }
    }
}
