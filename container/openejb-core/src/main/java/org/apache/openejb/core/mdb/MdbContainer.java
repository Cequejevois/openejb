/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.openejb.core.mdb;

import org.apache.openejb.OpenEJBException;
import org.apache.openejb.DeploymentInfo;
import org.apache.openejb.Container;
import org.apache.openejb.SystemException;
import org.apache.openejb.ApplicationException;
import org.apache.openejb.spi.SecurityService;
import org.apache.openejb.core.ThreadContext;
import org.apache.openejb.core.CoreDeploymentInfo;
import org.apache.openejb.core.transaction.TransactionContext;
import org.apache.openejb.core.transaction.TransactionPolicy;
import org.apache.log4j.Logger;
import org.apache.xbean.recipe.ObjectRecipe;

import javax.transaction.TransactionManager;
import javax.transaction.Transaction;
import javax.transaction.xa.XAResource;
import javax.resource.spi.ResourceAdapter;
import javax.resource.spi.ActivationSpec;
import javax.resource.ResourceException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Arrays;

public class MdbContainer implements Container {
    private static final Logger logger = Logger.getLogger("OpenEJB");
    private static final Object[] NO_ARGS = new Object[0];

    private final Object containerID;
    private final TransactionManager transactionManager;
    private final SecurityService securityService;
    private final ResourceAdapter resourceAdapter;
    private final Class activationSpecClass;

    private final Map<Object, DeploymentInfo> deployments = new HashMap<Object, DeploymentInfo>();
    private final Map<Object, EndpointFactory> endpointFactories = new HashMap<Object, EndpointFactory>();

    public MdbContainer(Object containerID, TransactionManager transactionManager, SecurityService securityService, ResourceAdapter resourceAdapter, Class activationSpecClass) {
        this.containerID = containerID;
        this.transactionManager = transactionManager;
        this.securityService = securityService;
        this.resourceAdapter = resourceAdapter;
        this.activationSpecClass = activationSpecClass;
    }

    public synchronized DeploymentInfo [] deployments() {
        return deployments.values().toArray(new DeploymentInfo[deployments.size()]);
    }

    public synchronized DeploymentInfo getDeploymentInfo(Object deploymentID) {
        return deployments.get(deploymentID);
    }

    public int getContainerType() {
        return Container.MESSAGE_DRIVEN;
    }

    public Object getContainerID() {
        return containerID;
    }

    public void deploy(Object deploymentId, DeploymentInfo deploymentInfo) throws OpenEJBException {
        // create the activation spec
        ActivationSpec activationSpec = createActivationSpec(deploymentInfo);

        // create the message endpoint
        MdbInstanceFactory instanceFactory = new MdbInstanceFactory(deploymentInfo, transactionManager, securityService, 0);
        EndpointFactory endpointFactory = new EndpointFactory(activationSpec, this, deploymentInfo, instanceFactory);

        // update the data structures
        // this must be done before activating the endpoint since the ra may immedately begin delivering messages
        synchronized (this) {
            deploymentInfo.setContainer(this);
            deployments.put(deploymentId, deploymentInfo);
            endpointFactories.put(deploymentId, endpointFactory);
        }

        // activate the endpoint
        try {
            resourceAdapter.endpointActivation(endpointFactory, activationSpec);
        } catch (ResourceException e) {
            // activation failed... clean up
            synchronized (this) {
                deploymentInfo.setContainer(null);
                deployments.remove(deploymentId);
                endpointFactories.remove(deploymentId);
            }

            throw new OpenEJBException(e);
        }

    }

    private ActivationSpec createActivationSpec(DeploymentInfo deploymentInfo)throws OpenEJBException {
        try {
            // initialize the object recipe
            ObjectRecipe objectRecipe = new ObjectRecipe(activationSpecClass);
            Map<String, String> activationProperties = deploymentInfo.getActivationProperties();
            for (Map.Entry<String, String> entry : activationProperties.entrySet()) {
                objectRecipe.setMethodProperty(entry.getKey(), entry.getValue());
            }

            // create the activationSpec
            ActivationSpec activationSpec = (ActivationSpec) objectRecipe.create(deploymentInfo.getClassLoader());

            // validate the activation spec
            activationSpec.validate();

            // set the resource adapter into the activation spec
            activationSpec.setResourceAdapter(resourceAdapter);

            return activationSpec;
        } catch (Exception e) {
            throw new OpenEJBException("Unable to create activation spec");
        }
    }

    public void undeploy(Object deploymentId) throws OpenEJBException {
        try {
            EndpointFactory endpointFactory;
            synchronized (this) {
                endpointFactory = endpointFactories.get(deploymentId);
            }
            if (endpointFactory != null) {
                resourceAdapter.endpointDeactivation(endpointFactory, endpointFactory.getActivationSpec());
            }
        } finally {
            synchronized (this) {
                endpointFactories.remove(deploymentId);
                DeploymentInfo deploymentInfo = deployments.remove(deploymentId);
                if (deploymentInfo != null) {
                    deploymentInfo.setContainer(null);
                }
            }
        }
    }

    public void beforeDelivery(Object deployId, Object instance, Method method, XAResource xaResource) throws SystemException {
        // get the target deployment (MDB)
        CoreDeploymentInfo deployInfo = (CoreDeploymentInfo) getDeploymentInfo(deployId);
        if (deployInfo == null) throw new SystemException("Unknown deployment " + deployId);

        // intialize call context
        ThreadContext callContext = ThreadContext.getThreadContext();
        callContext.setDeploymentInfo(deployInfo);
        MdbCallContext mdbCallContext = new MdbCallContext();
        callContext.setUnspecified(mdbCallContext);
        mdbCallContext.deliveryMethod = method;

        // create the tx data
        mdbCallContext.txPolicy = deployInfo.getTransactionPolicy(method);
        mdbCallContext.txContext = new TransactionContext(callContext, transactionManager);

        // install the application classloader
        installAppClassLoader(mdbCallContext, deployInfo.getClassLoader());

        // call the tx before method
        try {
            mdbCallContext.txPolicy.beforeInvoke(instance, mdbCallContext.txContext);
            enlistResource(xaResource);
        } catch (ApplicationException e) {
            restoreAdapterClassLoader(mdbCallContext);

            throw new SystemException("Should never get an Application exception", e);
        } catch (SystemException e) {
            restoreAdapterClassLoader(mdbCallContext);
            throw e;
        }
    }

    private void enlistResource(XAResource xaResource) throws SystemException {
        if (xaResource == null) return;

        try {
            Transaction transaction = transactionManager.getTransaction();
            if (transaction != null) {
                transaction.enlistResource(xaResource);
            }
        } catch (Exception e) {
            throw new SystemException("Unable to enlist xa resource in the transaction", e);
        }
    }

    public Object invoke(Object instance, Method method, Object... args) throws SystemException, ApplicationException {
        if (args == null) {
            args = NO_ARGS;
        }

        // get the context data
        ThreadContext callContext = ThreadContext.getThreadContext();
        CoreDeploymentInfo deployInfo = callContext.getDeploymentInfo();
        MdbCallContext mdbCallContext = (MdbCallContext) callContext.getUnspecified();

        if (mdbCallContext == null) {
            throw new IllegalStateException("beforeDelivery was not called");
        }

        // verify the delivery method passed to beforeDeliver is the same method that was invoked
        if (!mdbCallContext.deliveryMethod.getName().equals(method.getName()) ||
                !Arrays.deepEquals(mdbCallContext.deliveryMethod.getParameterTypes(), method.getParameterTypes())) {
            throw new IllegalStateException("Delivery method specified in beforeDelivery is not the delivery method called");
        }

        // remember the return value or exception so it can be logged
        Object returnValue = null;
        OpenEJBException openEjbException = null;
        try {
            if (logger.isInfoEnabled()) {
                logger.info("invoking method " + method.getName() + " on " + deployInfo.getDeploymentID());
            }

            // determine the target method on the bean instance class
            Method targetMethod = deployInfo.getMatchingBeanMethod(method);

            // invoke the target method
            returnValue = _invoke(instance, targetMethod, args, mdbCallContext);
            return returnValue;
        } catch (ApplicationException e) {
            openEjbException = e;
            throw e;
        } catch (SystemException e) {
            openEjbException = e;
            throw e;
        } finally {
            // Log the invocation results
            if (logger.isDebugEnabled()) {
                if (openEjbException == null) {
                    logger.debug("finished invoking method " + method.getName() + ". Return value:" + returnValue);
                } else {
                    Throwable exception = (openEjbException.getRootCause() != null) ? openEjbException.getRootCause() : openEjbException;
                    logger.debug("finished invoking method " + method.getName() + " with exception " + exception);
                }
            }
        }
    }

    private Object _invoke(Object instance, Method runMethod, Object [] args, MdbCallContext mdbCallContext) throws SystemException, ApplicationException {
        try {
            Object returnValue = runMethod.invoke(instance, args);
            return returnValue;
        } catch (java.lang.reflect.InvocationTargetException ite) {// handle exceptions thrown by enterprise bean
            if (ite.getTargetException() instanceof RuntimeException) {
                //
                /// System Exception ****************************
                mdbCallContext.txPolicy.handleSystemException(ite.getTargetException(), instance, mdbCallContext.txContext);
            } else {
                //
                // Application Exception ***********************
                mdbCallContext.txPolicy.handleApplicationException(ite.getTargetException(), mdbCallContext.txContext);
            }
        } catch (Throwable re) {// handle reflection exception
            //  Any exception thrown by reflection; not by the enterprise bean. Possible
            //  Exceptions are:
            //    IllegalAccessException - if the underlying method is inaccessible.
            //    IllegalArgumentException - if the number of actual and formal parameters differ, or if an unwrapping conversion fails.
            //    NullPointerException - if the specified object is null and the method is an instance method.
            //    ExceptionInInitializerError - if the initialization provoked by this method fails.
            mdbCallContext.txPolicy.handleSystemException(re, instance, mdbCallContext.txContext);
        }
        throw new AssertionError("Should not get here");
    }


    public void afterDelivery(Object instance) throws SystemException {
        // get the mdb call context
        ThreadContext callContext = ThreadContext.getThreadContext();
        MdbCallContext mdbCallContext = (MdbCallContext) callContext.getUnspecified();
        ThreadContext.setThreadContext(null);

        // invoke the tx after method
        try {
            mdbCallContext.txPolicy.afterInvoke(instance, mdbCallContext.txContext);
        } catch (ApplicationException e) {
            throw new SystemException("Should never get an Application exception", e);
        } finally {
            restoreAdapterClassLoader(mdbCallContext);
        }
    }

    public void release(Object instance) {
        // get the mdb call context
        ThreadContext callContext = ThreadContext.getThreadContext();
        MdbCallContext mdbCallContext = (MdbCallContext) callContext.getUnspecified();
        ThreadContext.setThreadContext(null);

        // if we have an mdb call context we need to invoke the after invoke method
        if (mdbCallContext != null) {
            try {
                mdbCallContext.txPolicy.afterInvoke(instance, mdbCallContext.txContext);
            } catch (Exception e) {
                logger.error("error while releasing message endpoint", e);
            } finally {
                restoreAdapterClassLoader(mdbCallContext);
            }
        }
    }

    private static class MdbCallContext {
        private Method deliveryMethod;
        private ClassLoader adapterClassLoader;
        private TransactionPolicy txPolicy;
        private TransactionContext txContext;
    }

    private void installAppClassLoader(MdbCallContext mdbCallContext, ClassLoader applicationClassLoader) {
        Thread currentThread = Thread.currentThread();

        mdbCallContext.adapterClassLoader = currentThread.getContextClassLoader();
        if (mdbCallContext.adapterClassLoader != applicationClassLoader) {
            currentThread.setContextClassLoader(applicationClassLoader);
        }
    }

    private void restoreAdapterClassLoader(MdbCallContext mdbCallContext) {
        Thread.currentThread().setContextClassLoader(mdbCallContext.adapterClassLoader);
        mdbCallContext.adapterClassLoader = null;
    }
}
