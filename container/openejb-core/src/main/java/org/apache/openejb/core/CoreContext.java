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
package org.apache.openejb.core;

import javax.ejb.EJBHome;
import javax.ejb.EJBLocalHome;
import javax.ejb.EJBLocalObject;
import javax.ejb.TimerService;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.transaction.Status;
import javax.transaction.TransactionManager;
import javax.transaction.UserTransaction;

import org.apache.openejb.DeploymentInfo;
import org.apache.openejb.InterfaceType;
import org.apache.openejb.RpcContainer;
import org.apache.openejb.InternalErrorException;
import org.apache.openejb.core.ivm.EjbObjectProxyHandler;
import org.apache.openejb.core.ivm.IntraVmProxy;
import org.apache.openejb.spi.SecurityService;
import org.apache.openejb.util.proxy.ProxyManager;


public abstract class CoreContext implements java.io.Serializable {

    public final static byte SECURITY_METHOD = (byte) 1;

    public final static byte USER_TRANSACTION_METHOD = (byte) 2;

    public final static byte ROLLBACK_METHOD = (byte) 3;

    public final static byte EJBOBJECT_METHOD = (byte) 4;

    public final static byte EJBHOME_METHOD = (byte) 5;

    private final UserTransaction userTransaction;
    private final SecurityService securityService;
    private final TransactionManager transactionManager;

    public CoreContext(TransactionManager transactionManager, SecurityService securityService) {
        this.transactionManager = transactionManager;
        this.securityService = securityService;
        this.userTransaction = new CoreUserTransaction(transactionManager);
    }

    protected CoreContext(TransactionManager transactionManager, SecurityService securityService, UserTransaction userTransaction) {
        this.transactionManager = transactionManager;
        this.securityService = securityService;
        this.userTransaction = userTransaction;
    }

    private TransactionManager getTransactionManager() {
        return transactionManager;
    }

    public abstract void checkBeanState(byte methodCategory) throws IllegalStateException;

    public java.security.Principal getCallerPrincipal() {
        checkBeanState(SECURITY_METHOD);
        Object securityIdentity = null;
        return (java.security.Principal) getSecurityService().translateTo(securityIdentity, java.security.Principal.class);
    }

    private SecurityService getSecurityService() {
        return securityService;
    }

    public boolean isCallerInRole(java.lang.String roleName) {
        checkBeanState(SECURITY_METHOD);
        return securityService.isCallerInRole(roleName);
    }

    public EJBHome getEJBHome() {
        checkBeanState(EJBHOME_METHOD);

        ThreadContext threadContext = ThreadContext.getThreadContext();
        org.apache.openejb.core.CoreDeploymentInfo di = (org.apache.openejb.core.CoreDeploymentInfo) threadContext.getDeploymentInfo();

        return di.getEJBHome();
    }

    public javax.ejb.EJBObject getEJBObject() {
        checkBeanState(EJBOBJECT_METHOD);

        ThreadContext threadContext = ThreadContext.getThreadContext();
        org.apache.openejb.DeploymentInfo di = threadContext.getDeploymentInfo();

        EjbObjectProxyHandler handler = newEjbObjectHandler((RpcContainer) di.getContainer(), threadContext.getPrimaryKey(), di.getDeploymentID(), InterfaceType.EJB_OBJECT);
        Object newProxy = null;
        try {
            Class[] interfaces = new Class[]{di.getRemoteInterface(), org.apache.openejb.core.ivm.IntraVmProxy.class};
            newProxy = ProxyManager.newProxyInstance(interfaces, handler);
        } catch (IllegalAccessException iae) {
            throw new RuntimeException("Could not create IVM proxy for " + di.getRemoteInterface() + " interface", iae);
        }
        return (javax.ejb.EJBObject) newProxy;
    }

    public EJBLocalObject getEJBLocalObject() {
        ThreadContext threadContext = ThreadContext.getThreadContext();
        org.apache.openejb.DeploymentInfo di = threadContext.getDeploymentInfo();

        EjbObjectProxyHandler handler = newEjbObjectHandler((RpcContainer) di.getContainer(), threadContext.getPrimaryKey(), di.getDeploymentID(), InterfaceType.EJB_LOCAL);

        Object newProxy = null;
        try {
            Class[] interfaces = new Class[]{di.getLocalInterface(), org.apache.openejb.core.ivm.IntraVmProxy.class};
            newProxy = ProxyManager.newProxyInstance(interfaces, handler);
        } catch (IllegalAccessException iae) {
            throw new RuntimeException("Could not create IVM proxy for " + di.getLocalInterface() + " interface", iae);
        }
        return (EJBLocalObject) newProxy;
    }

    public Object getBusinessObject(Class interfce) {
        ThreadContext threadContext = ThreadContext.getThreadContext();
        DeploymentInfo di = threadContext.getDeploymentInfo();

        InterfaceType interfaceType;
        if (di.getBusinessLocalInterface() != null && di.getBusinessLocalInterface().getName().equals(interfce.getName())) {
            interfaceType = InterfaceType.BUSINESS_LOCAL;
        } else if (di.getBusinessRemoteInterface() != null && di.getBusinessRemoteInterface().getName().equals(interfce.getName())) {
            interfaceType = InterfaceType.BUSINESS_REMOTE;
        } else {
            throw new IllegalArgumentException("Component has no such interface " + interfce.getName());
        }

        Object newProxy;
        try {
            EjbObjectProxyHandler handler = newEjbObjectHandler((RpcContainer) di.getContainer(), threadContext.getPrimaryKey(), di.getDeploymentID(), interfaceType);
            Class[] interfaces = new Class[]{interfce, IntraVmProxy.class};
            newProxy = ProxyManager.newProxyInstance(interfaces, handler);
        } catch (IllegalAccessException iae) {
            throw new InternalErrorException("Could not create IVM proxy for " + interfce.getName() + " interface", iae);
        }
        return newProxy;
    }

    public EJBLocalHome getEJBLocalHome() {
        ThreadContext threadContext = ThreadContext.getThreadContext();
        org.apache.openejb.core.CoreDeploymentInfo di = threadContext.getDeploymentInfo();

        return di.getEJBLocalHome();
    }

    public TimerService getTimerService() {
        return null;
    }

    public Object getPrimaryKey() {
        /*
        * This method is only declared in the EntityContext interface and is therefor
        * unavailable in the SessionContext and doesn't not require a check for bean kind (Entity vs Session).
        */
        checkBeanState(EJBOBJECT_METHOD);

        ThreadContext threadContext = ThreadContext.getThreadContext();
        return threadContext.getPrimaryKey();
    }

    public boolean getRollbackOnly() {

        ThreadContext threadContext = ThreadContext.getThreadContext();
        org.apache.openejb.DeploymentInfo di = threadContext.getDeploymentInfo();
        if (di.isBeanManagedTransaction()) {
            throw new IllegalStateException("bean-managed transaction beans can not access the getRollbackOnly( ) method");
        }

        checkBeanState(ROLLBACK_METHOD);
        try {
            int status = getTransactionManager().getStatus();
            if (status == Status.STATUS_MARKED_ROLLBACK || status == Status.STATUS_ROLLEDBACK) {
                return true;
            } else if (status == Status.STATUS_NO_TRANSACTION) {
                // this would be true for Supports tx attribute where no tx was propagated
                throw new IllegalStateException("No current transaction");
            } else {
                return false;
            }
        } catch (javax.transaction.SystemException se) {
            throw new RuntimeException("Transaction service has thrown a SystemException", se);
        }
    }

    public void setRollbackOnly() {
        ThreadContext threadContext = ThreadContext.getThreadContext();
        org.apache.openejb.DeploymentInfo di = threadContext.getDeploymentInfo();
        if (di.isBeanManagedTransaction()) {
            throw new IllegalStateException("bean-managed transaction beans can not access the setRollbackOnly( ) method");
        }

        checkBeanState(ROLLBACK_METHOD);

        try {
            getTransactionManager().setRollbackOnly();
        } catch (javax.transaction.SystemException se) {
            throw new RuntimeException("Transaction service has thrown a SystemException", se);
        }

    }

    public javax.transaction.UserTransaction getUserTransaction() {

        ThreadContext threadContext = ThreadContext.getThreadContext();
        org.apache.openejb.DeploymentInfo di = threadContext.getDeploymentInfo();
        if (di.isBeanManagedTransaction()) {
            checkBeanState(USER_TRANSACTION_METHOD);
            return userTransaction;
        } else {
            throw new java.lang.IllegalStateException("container-managed transaction beans can not access the UserTransaction");
        }
    }

    /**
     * Lookup a resource within the component's private naming context.
     *
     * @param name - Name of the entry (relative to java:comp/env).
     * @return The looked-up object.
     * @see http://java.sun.com/javaee/5/docs/api/javax/ejb/EJBContext.html#lookup(java.lang.String)
     * @see EJB3.0 "Core Contracts and Requirements", section 4.5.2, table 2.
     */
    public Object lookup(String name) {
        Context initialContext = null;
        Object object = null;

        try {
            initialContext = new InitialContext();
            object = initialContext.lookup("java:comp/env/" + name);
        } catch (NamingException nex) {
            throw new IllegalArgumentException(nex);
        }
        return object;
    }

    /*----------------------------------------------------*/
    /* UNSUPPORTED DEPRICATED METHODS                     */
    /*----------------------------------------------------*/

    public boolean isCallerInRole(java.security.Identity role) {
        throw new java.lang.UnsupportedOperationException();
    }

    public java.security.Identity getCallerIdentity() {
        throw new java.lang.UnsupportedOperationException();
    }

    public java.util.Properties getEnvironment() {
        throw new java.lang.UnsupportedOperationException();
    }

    protected abstract EjbObjectProxyHandler newEjbObjectHandler(RpcContainer container, Object pk, Object depID, InterfaceType interfaceType);
}