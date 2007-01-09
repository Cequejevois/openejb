/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.openejb.core.transaction;

import org.apache.openejb.ApplicationException;

public class TxSupports extends TransactionPolicy {

    public TxSupports(TransactionContainer container) {
        this();
        this.container = container;
    }

    public TxSupports() {
        policyType = Supports;
    }

    public String policyToString() {
        return "TX_Supports: ";
    }

    public void beforeInvoke(Object instance, TransactionContext context) throws org.apache.openejb.SystemException, org.apache.openejb.ApplicationException {

        try {

            context.clientTx = context.getTransactionManager().getTransaction();
            context.currentTx = context.clientTx;

        } catch (javax.transaction.SystemException se) {
            throw new org.apache.openejb.SystemException(se);
        }
    }

    public void afterInvoke(Object instance, TransactionContext context) throws org.apache.openejb.ApplicationException, org.apache.openejb.SystemException {

    }

    public void handleApplicationException(Throwable appException, TransactionContext context) throws ApplicationException {

        throw new ApplicationException(appException);
    }

    public void handleSystemException(Throwable sysException, Object instance, TransactionContext context) throws org.apache.openejb.ApplicationException, org.apache.openejb.SystemException {

        boolean runningInTransaction = (context.currentTx != null);

        if (runningInTransaction) {
            /* [1] Log the system exception or error *********/
            logSystemException(sysException);

            /* [2] Mark the transaction for rollback. ********/
            markTxRollbackOnly(context.currentTx);

            /* [3] Discard instance. *************************/
            discardBeanInstance(instance, context.callContext);

            /* [4] TransactionRolledbackException to client **/
            throwTxExceptionToServer(sysException);

        } else {
            /* [1] Log the system exception or error *********/
            logSystemException(sysException);

            /* [2] Discard instance. *************************/
            discardBeanInstance(instance, context.callContext);

            /* [3] Throw RemoteException to client ***********/
            throwExceptionToServer(sysException);
        }

    }

}

