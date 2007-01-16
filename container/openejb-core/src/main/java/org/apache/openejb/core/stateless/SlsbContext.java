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

import java.lang.reflect.Method;
import java.security.Principal;
import javax.ejb.EJBLocalObject;
import javax.ejb.EJBObject;
import javax.ejb.SessionContext;
import javax.ejb.TimerService;
import javax.transaction.UserTransaction;
import javax.transaction.TransactionManager;
import javax.xml.rpc.handler.MessageContext;

import org.apache.openejb.DeploymentInfo;
import org.apache.openejb.InterfaceType;
import org.apache.openejb.InternalErrorException;
import org.apache.openejb.RpcContainer;
import org.apache.openejb.spi.SecurityService;
import org.apache.openejb.core.BaseContext;
import org.apache.openejb.core.Operation;
import org.apache.openejb.core.ThreadContext;
import org.apache.openejb.core.ivm.EjbObjectProxyHandler;
import org.apache.openejb.core.ivm.IntraVmProxy;
import org.apache.openejb.util.proxy.ProxyManager;


/**
 * @version $Rev$ $Date$
 */
public class SlsbContext extends BaseContext implements SessionContext {


    public SlsbContext(TransactionManager transactionManager, SecurityService securityService) {
        super(transactionManager, securityService);
    }

    public SlsbContext(TransactionManager transactionManager, SecurityService securityService, UserTransaction userTransaction) {
        super(transactionManager, securityService, userTransaction);
    }

    protected void init() {
        states[Operation.INJECTION.ordinal()] = INJECTION;
        states[Operation.LIFECYCLE.ordinal()] = LIFECYCLE;
        states[Operation.BUSINESS.ordinal()] = BUSINESS;
        states[Operation.BUSINESS_WS.ordinal()] = BUSINESS_WS;
        states[Operation.TIMEOUT.ordinal()] = TIMEOUT;
    }

    public EJBLocalObject getEJBLocalObject() throws IllegalStateException {
        return ((StatelessState) getState()).getEJBLocalObject();
    }

    public EJBObject getEJBObject() throws IllegalStateException {
        return ((StatelessState) getState()).getEJBObject();
    }

    public MessageContext getMessageContext() throws IllegalStateException {
        return ((StatelessState) getState()).getMessageContext();
    }

    public Object getBusinessObject(Class aClass) {
        return ((StatelessState) getState()).getBusinessObject(aClass);
    }

    public Class getInvokedBusinessInterface() {
        return ((StatelessState) getState()).getInvokedBusinessInterface();
    }

    protected class StatelessState extends State implements SessionContext {

        public EJBLocalObject getEJBLocalObject() throws IllegalStateException {
            ThreadContext threadContext = ThreadContext.getThreadContext();
            DeploymentInfo di = threadContext.getDeploymentInfo();

            EjbObjectProxyHandler handler = new StatelessEjbObjectHandler((RpcContainer) di.getContainer(), threadContext.getPrimaryKey(), di.getDeploymentID(), InterfaceType.EJB_LOCAL);
            handler.setLocal(true);
            try {
                Class[] interfaces = new Class[]{di.getLocalInterface(), org.apache.openejb.core.ivm.IntraVmProxy.class};
                return (EJBLocalObject) ProxyManager.newProxyInstance(interfaces, handler);
            } catch (IllegalAccessException iae) {
                throw new InternalErrorException("Could not create IVM proxy for " + di.getLocalInterface() + " interface", iae);
            }
        }

        public EJBObject getEJBObject() throws IllegalStateException {
            ThreadContext threadContext = ThreadContext.getThreadContext();
            DeploymentInfo di = threadContext.getDeploymentInfo();

            EjbObjectProxyHandler handler = new StatelessEjbObjectHandler((RpcContainer) di.getContainer(), threadContext.getPrimaryKey(), di.getDeploymentID(), InterfaceType.EJB_OBJECT);
            try {
                Class[] interfaces = new Class[]{di.getRemoteInterface(), org.apache.openejb.core.ivm.IntraVmProxy.class};
                return (EJBObject) ProxyManager.newProxyInstance(interfaces, handler);
            } catch (IllegalAccessException iae) {
                throw new InternalErrorException("Could not create IVM proxy for " + di.getLocalInterface() + " interface", iae);
            }
        }

        public MessageContext getMessageContext() throws IllegalStateException {
            throw new UnsupportedOperationException("not implemented");
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

            try {
                EjbObjectProxyHandler handler = new StatelessEjbObjectHandler((RpcContainer) di.getContainer(), threadContext.getPrimaryKey(), di.getDeploymentID(), interfaceType);
                Class[] interfaces = new Class[]{interfce, IntraVmProxy.class};
                return ProxyManager.newProxyInstance(interfaces, handler);
            } catch (IllegalAccessException iae) {
                throw new InternalErrorException("Could not create IVM proxy for " + interfce.getName() + " interface", iae);
            }
        }

        public Class getInvokedBusinessInterface() {
            ThreadContext threadContext = ThreadContext.getThreadContext();
            DeploymentInfo di = threadContext.getDeploymentInfo();
            Class methodClass = threadContext.get(Method.class).getDeclaringClass();

            if (di.getBusinessLocalInterface() != null && di.getBusinessLocalInterface().isAssignableFrom(methodClass)) return di.getBusinessLocalInterface();
            else if (di.getBusinessRemoteInterface() != null && di.getBusinessRemoteInterface().isAssignableFrom(methodClass)) return di.getBusinessRemoteInterface();
            else throw new InternalErrorException("Should have found some business interface");
        }
    }

    /**
     * Dependency injection methods (e.g., setSessionContext)
     */
    private final StatelessState INJECTION = new StatelessState() {
        public EJBLocalObject getEJBLocalObject() throws IllegalStateException {
            throw new IllegalStateException();
        }

        public EJBObject getEJBObject() throws IllegalStateException {
            throw new IllegalStateException();
        }

        public MessageContext getMessageContext() throws IllegalStateException {
            throw new IllegalStateException();
        }

        public Object getBusinessObject(Class interfce) {
            throw new IllegalStateException();
        }

        public Class getInvokedBusinessInterface() {
            throw new IllegalStateException();
        }

        public Principal getCallerPrincipal() {
            throw new IllegalStateException();
        }

        public boolean isCallerInRole(String roleName) {
            throw new IllegalStateException();
        }

        public UserTransaction getUserTransaction() throws IllegalStateException {
            throw new IllegalStateException();
        }

        public void setRollbackOnly() throws IllegalStateException {
            throw new IllegalStateException();
        }

        public boolean getRollbackOnly() throws IllegalStateException {
            throw new IllegalStateException();
        }

        public TimerService getTimerService() throws IllegalStateException {
            throw new IllegalStateException();
        }

        public boolean isUserTransactionAccessAllowed() {
            return false;
        }

        public boolean isMessageContextAccessAllowed() {
            return false;
        }

        public boolean isResourceManagerAccessAllowed() {
            return false;
        }

        public boolean isEnterpriseBeanAccessAllowed() {
            return false;
        }

        public boolean isEntityManagerFactoryAccessAllowed() {
            return false;
        }

        public boolean isEntityManagerAccessAllowed() {
            return false;
        }

        public boolean isTimerAccessAllowed() {
            return false;
        }
    };

    /**
     * PostConstruct, Pre-Destroy lifecycle callback interceptor methods
     */
    private final StatelessState LIFECYCLE = new StatelessState() {
        public MessageContext getMessageContext() throws IllegalStateException {
            throw new IllegalStateException();
        }

        public Class getInvokedBusinessInterface() {
            throw new IllegalStateException();
        }

        public Principal getCallerPrincipal() {
            throw new IllegalStateException();
        }

        public boolean isCallerInRole(String roleName) {
            throw new IllegalStateException();
        }

        public UserTransaction getUserTransaction() throws IllegalStateException {
            throw new IllegalStateException();
        }

        public void setRollbackOnly() throws IllegalStateException {
            throw new IllegalStateException();
        }

        public boolean getRollbackOnly() throws IllegalStateException {
            throw new IllegalStateException();
        }

        public boolean isUserTransactionAccessAllowed() {
            return false;
        }

        public boolean isMessageContextAccessAllowed() {
            return false;
        }

        public boolean isJNDIAccessAllowed() {
            return false;
        }

        public boolean isResourceManagerAccessAllowed() {
            return false;
        }

        public boolean isEnterpriseBeanAccessAllowed() {
            return false;
        }

        public boolean isEntityManagerFactoryAccessAllowed() {
            return false;
        }

        public boolean isEntityManagerAccessAllowed() {
            return false;
        }

        public boolean isTimerAccessAllowed() {
            return false;
        }
    };

    /**
     * Business method from business interface or component interface; business
     * method interceptor method
     */
    private final StatelessState BUSINESS = new StatelessState() {
        public MessageContext getMessageContext() throws IllegalStateException {
            throw new IllegalStateException();
        }

        public UserTransaction getUserTransaction() throws IllegalStateException {
            throw new IllegalStateException();
        }

        public boolean isUserTransactionAccessAllowed() {
            return false;
        }

        public boolean isMessageContextAccessAllowed() {
            return false;
        }
    };

    /**
     * Business method from web service endpoint
     */
    private final StatelessState BUSINESS_WS = new StatelessState() {
        public Class getInvokedBusinessInterface() {
            throw new IllegalStateException();
        }

        public UserTransaction getUserTransaction() throws IllegalStateException {
            throw new IllegalStateException();
        }

        public boolean isUserTransactionAccessAllowed() {
            return false;
        }
    };

    /**
     * Timeout callback method
     */
    private final StatelessState TIMEOUT = new StatelessState() {
        public Class getInvokedBusinessInterface() {
            throw new IllegalStateException();
        }

        public MessageContext getMessageContext() throws IllegalStateException {
            throw new IllegalStateException();
        }

        public UserTransaction getUserTransaction() throws IllegalStateException {
            throw new IllegalStateException();
        }

        public boolean isUserTransactionAccessAllowed() {
            return false;
        }

        public boolean isMessageContextAccessAllowed() {
            return false;
        }
    };

}
