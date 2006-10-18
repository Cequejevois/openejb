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
package org.apache.openejb.core.stateless;

import org.apache.openejb.RpcContainer;
import org.apache.openejb.InterfaceType;
import org.apache.openejb.core.CoreDeploymentInfo;
import org.apache.openejb.core.Operations;
import org.apache.openejb.core.ThreadContext;
import org.apache.openejb.core.ivm.EjbObjectProxyHandler;
import org.apache.openejb.spi.SecurityService;

import javax.transaction.TransactionManager;
import javax.xml.rpc.handler.MessageContext;

public class StatelessContext
        extends org.apache.openejb.core.CoreContext implements javax.ejb.SessionContext {
    public StatelessContext(TransactionManager transactionManager, SecurityService securityService) {
        super(transactionManager, securityService);
    }

    public void checkBeanState(byte methodCategory) throws IllegalStateException {
        /*  
        SECURITY_METHOD:
        USER_TRANSACTION_METHOD:
        ROLLBACK_METHOD:
        EJBOBJECT_METHOD:

        The super class, CoreContext determines if Context.getUserTransaction( ) method 
        maybe called before invoking this.checkBeanState( ).  Only "bean managed" transaction
        beans may access this method.

        */
        ThreadContext callContext = ThreadContext.getThreadContext();
        CoreDeploymentInfo di = callContext.getDeploymentInfo();

        switch (callContext.getCurrentOperation()) {
            case Operations.OP_SET_CONTEXT:
                /* 
                Allowed Operations: 
                    getEJBHome
                Prohibited Operations:
                    getCallerPrincipal
                    getRollbackOnly,
                    isCallerInRole
                    setRollbackOnly
                    getEJBObject
                    getPrimaryKey
                    getUserTransaction
                */
                if (methodCategory != EJBHOME_METHOD)
                    throw new IllegalStateException("Invalid operation attempted");
                break;
            case Operations.OP_CREATE:
            case Operations.OP_REMOVE:
                /* 
                Allowed Operations: 
                    getEJBHome
                    getEJBObject
                    getPrimaryKey
                    getUserTransaction
                Prohibited Operations:
                    getCallerPrincipal
                    getRollbackOnly,
                    isCallerInRole
                    setRollbackOnly
                */
                if (methodCategory == EJBHOME_METHOD
                        || methodCategory == EJBOBJECT_METHOD
                        || methodCategory == USER_TRANSACTION_METHOD)
                    break;
                else
                    throw new IllegalStateException("Invalid operation attempted");
            case Operations.OP_BUSINESS:
                /* 
                Allowed Operations: 
                    getEJBHome
                    getEJBObject
                    getPrimaryKey
                    getUserTransaction
                    getCallerPrincipal
                    getRollbackOnly,
                    isCallerInRole
                    setRollbackOnly
                Prohibited Operations:
                */
                break;
        }

    }

    protected EjbObjectProxyHandler newEjbObjectHandler(RpcContainer container, Object pk, Object depID, InterfaceType interfaceType) {
        return new StatelessEjbObjectHandler(container, pk, depID, interfaceType);
    }

    public MessageContext getMessageContext() {
        throw new UnsupportedOperationException("not implemented");
    }

    public Object getBusinessObject(Class businessInterface) {
        throw new UnsupportedOperationException("not implemented");
    }

    public Class getInvokedBusinessInterface() {
        throw new UnsupportedOperationException("not implemented");
    }
}