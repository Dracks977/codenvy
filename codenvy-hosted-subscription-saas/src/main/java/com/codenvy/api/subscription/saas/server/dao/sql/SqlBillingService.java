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
package com.codenvy.api.subscription.saas.server.dao.sql;


import com.codenvy.api.subscription.saas.server.billing.BillingService;
import com.codenvy.api.subscription.saas.server.billing.PaymentState;
import com.codenvy.api.subscription.saas.server.billing.ResourcesFilter;
import com.codenvy.api.subscription.saas.server.billing.invoice.InvoiceFilter;
import com.codenvy.api.subscription.saas.shared.dto.AccountResources;
import com.codenvy.api.subscription.saas.shared.dto.Charge;
import com.codenvy.api.subscription.saas.shared.dto.Invoice;
import com.codenvy.api.subscription.saas.shared.dto.Resources;
import com.codenvy.sql.ConnectionFactory;
import com.codenvy.sql.postgresql.Int8RangeType;

import org.eclipse.che.api.core.NotFoundException;
import org.eclipse.che.api.core.ServerException;
import org.eclipse.che.dto.server.DtoFactory;
import org.postgresql.util.PGobject;

import javax.inject.Inject;
import javax.inject.Named;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.codenvy.api.subscription.saas.server.SaasSubscriptionService.SAAS_SUBSCRIPTION_ID;
import static com.codenvy.sql.SqlQueryAppender.appendContainsRange;
import static com.codenvy.sql.SqlQueryAppender.appendEqual;
import static com.codenvy.sql.SqlQueryAppender.appendHavingGreater;
import static com.codenvy.sql.SqlQueryAppender.appendIn;
import static com.codenvy.sql.SqlQueryAppender.appendIsNull;
import static com.codenvy.sql.SqlQueryAppender.appendLimit;
import static com.codenvy.sql.SqlQueryAppender.appendOffset;
import static com.codenvy.sql.SqlQueryAppender.appendOverlapRange;


/**
 * Database driving BillingService.
 *
 * @author Sergii Kabashniuk
 */
public class SqlBillingService implements BillingService {
    private final ConnectionFactory connectionFactory;
    private final double            saasChargeableGbHPrice;
    private final double            saasFreeGbH;

    @Inject
    public SqlBillingService(ConnectionFactory connectionFactory,
                             @Named("subscription.saas.chargeable.gbh.price") Double saasChargeableGbHPrice,
                             @Named("subscription.saas.usage.free.gbh") Double saasFreeGbH) {
        this.connectionFactory = connectionFactory;
        this.saasChargeableGbHPrice = saasChargeableGbHPrice;
        this.saasFreeGbH = saasFreeGbH;
    }

    @Override
    public int generateInvoices(long from, long till) throws ServerException {
        String calculationId = UUID.randomUUID().toString();

        try (Connection connection = connectionFactory.getConnection()) {
            connection.setAutoCommit(false);
            try {
                Int8RangeType range = new Int8RangeType(from, till, true, true);
                //calculate memory usage statistic
                try (PreparedStatement memoryChargesStatement = connection.prepareStatement(SqlDaoQueries.MEMORY_CHARGES_INSERT)) {

                    memoryChargesStatement.setObject(1, range);
                    memoryChargesStatement.setObject(2, range);
                    memoryChargesStatement.setString(3, calculationId);
                    memoryChargesStatement.setObject(4, range);
                    memoryChargesStatement.execute();
                }

                try (PreparedStatement saasCharges = connection.prepareStatement(SqlDaoQueries.CHARGES_MEMORY_INSERT)) {
                    saasCharges.setString(1, SAAS_SUBSCRIPTION_ID);
                    saasCharges.setDouble(2, saasFreeGbH);
                    saasCharges.setDouble(3, saasFreeGbH);
                    saasCharges.setDouble(4, saasFreeGbH);
                    saasCharges.setDouble(5, saasFreeGbH);
                    saasCharges.setDouble(6, saasChargeableGbHPrice);
                    saasCharges.setString(7, calculationId);

                    saasCharges.setObject(8, range);
                    saasCharges.setObject(9, range);
                    saasCharges.setDouble(10, till - from);
                    saasCharges.setObject(11, range);
                    saasCharges.setObject(12, range);
                    saasCharges.setString(13, calculationId);

                    saasCharges.execute();
                }

                int generatedInvoices;
                try (PreparedStatement invoices = connection.prepareStatement(SqlDaoQueries.INVOICES_INSERT)) {
                    invoices.setLong(1, System.currentTimeMillis());
                    invoices.setObject(2, range);
                    invoices.setString(3, calculationId);
                    invoices.setString(4, calculationId);
                    invoices.execute();
                    generatedInvoices = invoices.getUpdateCount();
                }

                connection.commit();

                return generatedInvoices;
            } catch (SQLException e) {
                connection.rollback();
                if (e.getLocalizedMessage().contains("conflicts with existing key (faccount_id, fperiod)")) {
                    throw new ServerException("Not able to generate invoices. Result overlaps with existed invoices.");
                }
                throw new ServerException(e.getLocalizedMessage(), e);
            }
        } catch (SQLException e) {
            throw new ServerException(e.getLocalizedMessage(), e);
        }
    }


    @Override
    public void setPaymentState(long invoiceId, PaymentState state, String creditCard) throws ServerException {
        if ((state == PaymentState.PAYMENT_FAIL || state == PaymentState.PAID_SUCCESSFULLY)) {
            if (creditCard == null || creditCard.isEmpty()) {
                throw new ServerException("Credit card parameter is missing for states  PAYMENT_FAIL or PAID_SUCCESSFULLY");
            }
        } else {
            if (creditCard != null && !creditCard.isEmpty()) {
                throw new ServerException(
                        "Credit card parameter should be null for states different when PAYMENT_FAIL or PAID_SUCCESSFULLY");
            }
        }
        try (Connection connection = connectionFactory.getConnection()) {
            try {
                connection.setAutoCommit(false);
                if (state == PaymentState.PAYMENT_FAIL || state == PaymentState.PAID_SUCCESSFULLY) {
                    try (PreparedStatement statement = connection.prepareStatement(SqlDaoQueries.INVOICES_PAYMENT_STATE_AND_CC_UPDATE)) {
                        statement.setString(1, state.getState());
                        statement.setString(2, creditCard);
                        statement.setLong(3, invoiceId);
                        statement.execute();
                    }
                } else {
                    try (PreparedStatement statement = connection.prepareStatement(SqlDaoQueries.INVOICES_PAYMENT_STATE_UPDATE)) {
                        statement.setString(1, state.getState());
                        statement.setLong(2, invoiceId);
                        statement.execute();
                    }

                }

                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new ServerException(e.getLocalizedMessage(), e);
        }
    }

    @Override
    public List<Invoice> getInvoices(InvoiceFilter filter) throws ServerException {
        try (Connection connection = connectionFactory.getConnection()) {
            connection.setAutoCommit(false);

            StringBuilder invoiceSelect = new StringBuilder();
            invoiceSelect.append("SELECT ").append(SqlDaoQueries.INVOICES_FIELDS).append(" FROM INVOICES ");

            appendEqual(invoiceSelect, "FID", filter.getId());
            appendEqual(invoiceSelect, "FACCOUNT_ID", filter.getAccountId());
            appendIn(invoiceSelect, "FPAYMENT_STATE", filter.getStates());
            appendIsNull(invoiceSelect, "FMAILING_TIME", filter.getIsMailNotSend());
            appendContainsRange(invoiceSelect, "FPERIOD", filter.getFromDate(), filter.getTillDate());

            invoiceSelect.append(" ORDER BY FACCOUNT_ID, FCREATED_TIME DESC ");

            appendLimit(invoiceSelect, filter.getMaxItems());
            appendOffset(invoiceSelect, filter.getSkipCount());


            try (PreparedStatement statement = connection.prepareStatement(invoiceSelect.toString())) {

                statement.setFetchSize(filter.getMaxItems() != null ? filter.getMaxItems() : 0);
                try (ResultSet invoicesResultSet = statement.executeQuery()) {
                    List<Invoice> result =
                            filter.getMaxItems() != null ? new ArrayList<Invoice>(filter.getMaxItems())
                                                         : new ArrayList<Invoice>();
                    while (invoicesResultSet.next()) {
                        result.add(toInvoice(connection, invoicesResultSet));
                    }
                    return result;
                }
            }
        } catch (SQLException e) {
            throw new ServerException(e.getLocalizedMessage(), e);
        }
    }

    @Override
    public Invoice getInvoice(long id) throws ServerException, NotFoundException {
        List<Invoice> invoices = getInvoices(InvoiceFilter.builder().withId(id).build());
        if (invoices.size() < 1) {
            throw new NotFoundException("Invoice with id " + id + " is not found");
        }
        return invoices.get(0);
    }

    @Override
    public void markInvoiceAsSent(long invoiceId) throws ServerException {
        try (Connection connection = connectionFactory.getConnection()) {
            try {
                connection.setAutoCommit(false);
                try (PreparedStatement statement = connection.prepareStatement(SqlDaoQueries.INVOICES_MAILING_TIME_UPDATE)) {
                    statement.setLong(1, invoiceId);
                    statement.execute();
                }
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new ServerException(e.getLocalizedMessage(), e);
        }
    }

    @Override
    public void addSubscription(String accountId, double amount, long from, long till) throws ServerException {
        try (Connection connection = connectionFactory.getConnection()) {
            try {
                connection.setAutoCommit(false);
                Int8RangeType range = new Int8RangeType(from, till, true, true);
                try (PreparedStatement statement = connection.prepareStatement(SqlDaoQueries.PREPAID_INSERT)) {
                    statement.setString(1, accountId);
                    statement.setDouble(2, amount);
                    statement.setObject(3, range);
                    statement.execute();
                }
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                if (e.getLocalizedMessage().contains("conflicts with existing key (faccount_id, fperiod)")) {
                    throw new ServerException(
                            "Unable to add new prepaid time since it overlapping with existed period");
                }
                throw e;
            }
        } catch (SQLException e) {
            throw new ServerException(e.getLocalizedMessage(), e);
        }
    }

    @Override
    public void removeSubscription(String accountId, long till) throws ServerException {
        try (Connection connection = connectionFactory.getConnection()) {
            try {
                connection.setAutoCommit(false);
                try (PreparedStatement statement = connection.prepareStatement(SqlDaoQueries.PREPAID_CLOSE_PERIOD)) {
                    statement.setLong(1, till);
                    statement.setString(2, accountId);
                    statement.execute();
                }
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new ServerException(e.getLocalizedMessage(), e);
        }
    }

    @Override
    public Resources getEstimatedUsage(long from, long till) throws ServerException {
        try (Connection connection = connectionFactory.getConnection()) {
            connection.setAutoCommit(false);
            StringBuilder select = new StringBuilder("SELECT ")
                    .append(" SUM(A.FFREE_AMOUNT)    AS FFREE_AMOUNT, ")
                    .append(" SUM(A.FPAID_AMOUNT)    AS FPAID_AMOUNT, ")
                    .append(" SUM(A.FPREPAID_AMOUNT) AS FPREPAID_AMOUNT ")
                    .append(" FROM (")
                    .append(SqlDaoQueries.ACCOUNTS_USAGE_SELECT);
            appendOverlapRange(select, "M.FDURING", from, till);
            select.append(" GROUP BY M.FACCOUNT_ID, P.FAMOUNT, B.FAMOUNT ");
            select.append(" ) AS A ");

            try (PreparedStatement usageStatement = connection.prepareStatement(select.toString())) {

                usageStatement.setFetchSize(1);
                Int8RangeType range = new Int8RangeType(from, till, true, true);
                usageStatement.setObject(1, range);
                usageStatement.setObject(2, range);
                usageStatement.setDouble(3, saasFreeGbH);
                usageStatement.setObject(4, range);
                usageStatement.setObject(5, range);
                usageStatement.setDouble(6, saasFreeGbH);
                usageStatement.setObject(7, range);
                usageStatement.setObject(8, range);
                usageStatement.setDouble(9, saasFreeGbH);
                usageStatement.setObject(10, range);
                usageStatement.setObject(11, range);
                usageStatement.setLong(12, till - from);
                usageStatement.setObject(13, range);
                usageStatement.setObject(14, range);

                try (ResultSet usageResultSet = usageStatement.executeQuery()) {
                    if (usageResultSet.next()) {
                        return DtoFactory.getInstance().createDto(Resources.class)
                                         .withFreeAmount(usageResultSet.getDouble("FFREE_AMOUNT"))
                                         .withPaidAmount(usageResultSet.getDouble("FPAID_AMOUNT"))
                                         .withPrePaidAmount(usageResultSet.getDouble("FPREPAID_AMOUNT"));
                    }

                }
            }
        } catch (SQLException e) {
            throw new ServerException(e.getLocalizedMessage(), e);
        }
        return DtoFactory.getInstance().createDto(Resources.class)
                         .withFreeAmount(0D)
                         .withPaidAmount(0D)
                         .withPrePaidAmount(0D);
    }


    @Override
    public List<AccountResources> getEstimatedUsageByAccount(ResourcesFilter resourcesFilter) throws ServerException {
        try (Connection connection = connectionFactory.getConnection()) {
            connection.setAutoCommit(false);
            StringBuilder accountUsageSelect = new StringBuilder(SqlDaoQueries.ACCOUNTS_USAGE_SELECT);
            appendOverlapRange(accountUsageSelect, "M.FDURING", resourcesFilter.getFromDate(), resourcesFilter.getTillDate());
            appendEqual(accountUsageSelect, "M.FACCOUNT_ID", resourcesFilter.getAccountId());
            accountUsageSelect.append(" GROUP BY M.FACCOUNT_ID, P.FAMOUNT, B.FAMOUNT");

            int havingFields = 0;
            havingFields += appendHavingGreater(accountUsageSelect,
                                                SqlDaoQueries.FFREE_AMOUNT,
                                                resourcesFilter.getFreeGbHMoreThan()) ? 1 : 0;
            havingFields += appendHavingGreater(accountUsageSelect,
                                                SqlDaoQueries.FPREPAID_AMOUNT,
                                                resourcesFilter.getPrePaidGbHMoreThan()) ? 1 : 0;
            havingFields += appendHavingGreater(accountUsageSelect,
                                                SqlDaoQueries.FPAID_AMOUNT,
                                                resourcesFilter.getPaidGbHMoreThan()) ? 1 : 0;

            accountUsageSelect.append(" ORDER BY M.FACCOUNT_ID ");

            appendLimit(accountUsageSelect, resourcesFilter.getMaxItems());
            appendOffset(accountUsageSelect, resourcesFilter.getSkipCount());


            try (PreparedStatement usageStatement = connection.prepareStatement(accountUsageSelect.toString())) {

                usageStatement.setFetchSize(resourcesFilter.getMaxItems() != null ? resourcesFilter.getMaxItems() : 0);
                Int8RangeType range = new Int8RangeType(resourcesFilter.getFromDate(), resourcesFilter.getTillDate(), true, true);
                usageStatement.setObject(1, range);
                usageStatement.setObject(2, range);
                usageStatement.setDouble(3, saasFreeGbH);
                usageStatement.setObject(4, range);
                usageStatement.setObject(5, range);
                usageStatement.setDouble(6, saasFreeGbH);
                usageStatement.setObject(7, range);
                usageStatement.setObject(8, range);
                usageStatement.setDouble(9, saasFreeGbH);
                usageStatement.setObject(10, range);
                usageStatement.setObject(11, range);
                usageStatement.setLong(12, resourcesFilter.getTillDate() - resourcesFilter.getFromDate());
                usageStatement.setObject(13, range);
                usageStatement.setObject(14, range);

                //set variable for 'having' sql part.
                for (int i = 15; i < 15 + havingFields * 3; ) {
                    usageStatement.setObject(i++, range);
                    usageStatement.setObject(i++, range);
                    usageStatement.setDouble(i++, saasFreeGbH);
                }
                try (ResultSet usageResultSet = usageStatement.executeQuery()) {
                    List<AccountResources> usage =
                            resourcesFilter.getMaxItems() != null ? new ArrayList<AccountResources>(resourcesFilter.getMaxItems())
                                                                  : new ArrayList<AccountResources>();
                    while (usageResultSet.next()) {
                        usage.add(DtoFactory.getInstance().createDto(AccountResources.class)
                                            .withAccountId(usageResultSet.getString("FACCOUNT_ID"))
                                            .withFreeAmount(usageResultSet.getDouble("FFREE_AMOUNT"))
                                            .withPaidAmount(usageResultSet.getDouble("FPAID_AMOUNT"))
                                            .withPrePaidAmount(usageResultSet.getDouble("FPREPAID_AMOUNT"))
                                            .withPaidSum(usageResultSet.getDouble("FPAID_AMOUNT") * saasChargeableGbHPrice)
                                 );
                    }
                    return usage;
                }
            }
        } catch (SQLException e) {
            throw new ServerException(e.getLocalizedMessage(), e);
        }
    }

    @Override
    public boolean hasAvailableResources(String accountId, Long from, Long till) throws ServerException {
        try (Connection connection = connectionFactory.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement usageStatement = connection.prepareStatement(SqlDaoQueries.EXCESSIVE_ACCOUNT_USAGE_SELECT)) {
                Int8RangeType range = new Int8RangeType(from, till, true, true);
                usageStatement.setObject(1, range);
                usageStatement.setObject(2, range);
                usageStatement.setDouble(3, saasFreeGbH);
                usageStatement.setObject(4, range);
                usageStatement.setObject(5, range);
                usageStatement.setLong(6, till - from);
                usageStatement.setObject(7, range);
                usageStatement.setObject(8, till);
                usageStatement.setString(9, accountId);
                usageStatement.setString(10, accountId);
                usageStatement.setObject(11, range);
                usageStatement.setObject(12, range);
                usageStatement.setString(13, accountId);
                try (ResultSet usageResultSet = usageStatement.executeQuery()) {
                    if (usageResultSet.next()) {
                        return !(usageResultSet.getDouble("FPAID_AMOUNT") > 0.0);
                    } else {
                        // Account doesn't have any metrics records in given period.
                        // That means that available resources has not been used at all
                        return true;
                    }
                }
            }
        } catch (SQLException e) {
            throw new ServerException(e.getLocalizedMessage(), e);
        }
    }

    @Override
    public double getProvidedFreeResources(String accountId, Long from, Long till) throws ServerException {
        try (Connection connection = connectionFactory.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement usageStatement = connection.prepareStatement(SqlDaoQueries.ACCOUNT_BONUSES_SELECT)) {
                Int8RangeType range = new Int8RangeType(from, till, true, true);
                usageStatement.setString(1, accountId);
                usageStatement.setObject(2, range);
                try (ResultSet usageResultSet = usageStatement.executeQuery()) {
                    if (usageResultSet.next()) {
                        return saasFreeGbH + usageResultSet.getDouble("FAMOUNT");
                    } else {
                        // Account doesn't have any metrics records in given period.
                        // That means that available resources has not been used at all
                        return saasFreeGbH;
                    }
                }
            }
        } catch (SQLException e) {
            throw new ServerException(e.getLocalizedMessage(), e);
        }
    }

    private Map<String, String> getMemoryChargeDetails(Connection connection, String accountId, String calculationID)
            throws SQLException {

        Map<String, String> mCharges = new HashMap<>();
        try (PreparedStatement memoryCharges = connection.prepareStatement(SqlDaoQueries.MEMORY_CHARGES_SELECT)) {
            memoryCharges.setString(1, accountId);
            memoryCharges.setString(2, calculationID);


            try (ResultSet chargesResultSet = memoryCharges.executeQuery()) {
                while (chargesResultSet.next()) {
                    mCharges.put(chargesResultSet.getString("FWORKSPACE_ID"),
                                 Double.toString(chargesResultSet.getDouble("FAMOUNT")));
                }
            }
        }
        return mCharges;
    }

    private Invoice toInvoice(Connection connection, ResultSet invoicesResultSet) throws SQLException {
        Int8RangeType range = new Int8RangeType((PGobject)invoicesResultSet.getObject("FPERIOD"));
        Date fpayment_time = invoicesResultSet.getDate("FPAYMENT_TIME");
        Date fmailing_time = invoicesResultSet.getDate("FMAILING_TIME");
        return DtoFactory.getInstance().createDto(Invoice.class)
                         .withId(invoicesResultSet.getLong("FID"))
                         .withTotal(invoicesResultSet.getDouble("FTOTAL"))
                         .withAccountId(invoicesResultSet.getString("FACCOUNT_ID"))
                         .withCreditCardId(invoicesResultSet.getString("FCREDIT_CARD"))
                         .withPaymentDate(fpayment_time != null ? fpayment_time.getTime() : 0)
                         .withPaymentState(invoicesResultSet.getString("FPAYMENT_STATE"))
                         .withMailingDate(fmailing_time != null ? fmailing_time.getTime() : 0)
                         .withCreationDate(invoicesResultSet.getLong("FCREATED_TIME"))
                         .withFromDate(range.getFrom())
                         .withTillDate(range.getUntil())
                         .withCharges(getCharges(connection,
                                                 invoicesResultSet.getString("FACCOUNT_ID"),
                                                 invoicesResultSet.getString("FCALC_ID")));
    }

    private List<Charge> getCharges(Connection connection, String accountId, String calculationID) throws SQLException {
        List<Charge> charges = new ArrayList<>();

        try (PreparedStatement chargesStatement = connection.prepareStatement(SqlDaoQueries.CHARGES_SELECT)) {
            chargesStatement.setString(1, accountId);
            chargesStatement.setString(2, calculationID);

            try (ResultSet chargesResultSet = chargesStatement.executeQuery()) {
                while (chargesResultSet.next()) {
                    charges.add(DtoFactory.getInstance().createDto(Charge.class)
                                          .withProvidedFreeAmount(chargesResultSet.getDouble("FPROVIDED_FREE_AMOUNT"))
                                          .withProvidedPrepaidAmount(chargesResultSet.getDouble("FPROVIDED_PREPAID_AMOUNT"))
                                          .withPaidAmount(chargesResultSet.getDouble("FPAID_AMOUNT"))
                                          .withServiceId(chargesResultSet.getString("FSERVICE_ID"))
                                          .withPaidPrice(chargesResultSet.getDouble("FPAID_PRICE"))
                                          .withFreeAmount(chargesResultSet.getDouble("FFREE_AMOUNT"))
                                          .withPrePaidAmount(chargesResultSet.getDouble("FPREPAID_AMOUNT"))
                                          .withDetails(getMemoryChargeDetails(connection, accountId, calculationID))
                               );
                }
            }

        }
        return charges;
    }
}
