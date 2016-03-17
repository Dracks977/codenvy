/*
 *
 * CODENVY CONFIDENTIAL
 * ________________
 *
 * [2012] - [2015] Codenvy, S.A.
 * All Rights Reserved.
 * NOTICE: All information contained herein is, and remains
 * the property of Codenvy S.A. and its suppliers,
 * if any. The intellectual and technical concepts contained
 * herein are proprietary to Codenvy S.A.
 * and its suppliers and may be covered by U.S. and Foreign Patents,
 * patents in process, and are protected by trade secret or copyright law.
 * Dissemination of this information or reproduction of this material
 * is strictly forbidden unless prior written permission is obtained
 * from Codenvy S.A..
 */

IMPORT 'macros.pig';

l = loadResources('$LOG', '$FROM_DATE', '$TO_DATE', '$USER', '$WS');
a1 = filterByEvent(l, 'signup-validation-email-send');
a2 = extractParam(a1, 'EMAIL', email);
a = FOREACH a2 GENERATE dt, email;

result = FOREACH a GENERATE UUID(),
                             TOTUPLE('date', ToMilliSeconds(dt)),
                             TOTUPLE('email', email);
STORE result INTO '$STORAGE_URL.$STORAGE_TABLE' USING MongoStorage;