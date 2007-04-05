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
package org.apache.openejb.core.ivm;

import java.io.ObjectStreamException;
import java.lang.reflect.Method;
import java.rmi.RemoteException;
import java.util.HashMap;
import java.util.Map;

import javax.ejb.EJBHome;
import javax.ejb.EJBException;

import org.apache.openejb.ProxyInfo;
import org.apache.openejb.RpcContainer;
import org.apache.openejb.InterfaceType;
import org.apache.openejb.DeploymentInfo;
import org.apache.openejb.core.ServerFederation;
import org.apache.openejb.loader.SystemInstance;
import org.apache.openejb.spi.ApplicationServer;
import org.apache.openejb.util.proxy.ProxyManager;
import org.apache.openejb.util.Logger;

public abstract class EjbHomeProxyHandler extends BaseEjbProxyHandler {
    private static final Logger logger = Logger.getInstance("OpenEJB", "org.apache.openejb.util.resources");

    private final Map<String,MethodType> dispatchTable;

    private static enum MethodType {
        CREATE,
        FIND,
        HOME_HANDLE,
        META_DATA,
        REMOVE
    }

    public EjbHomeProxyHandler(RpcContainer container, Object pk, Object depID, InterfaceType interfaceType) {
        super(container, pk, depID, interfaceType);
        dispatchTable = new HashMap<String,MethodType>();
        dispatchTable.put("create", MethodType.CREATE);
        dispatchTable.put("getEJBMetaData", MethodType.META_DATA);
        dispatchTable.put("getHomeHandle", MethodType.HOME_HANDLE);
        dispatchTable.put("remove", MethodType.REMOVE);

        if (interfaceType.isHome()) {
            DeploymentInfo deploymentInfo = container.getDeploymentInfo(depID);
            Class homeInterface = deploymentInfo.getInterface(interfaceType);
            Method[] methods = homeInterface.getMethods();
            for (Method method : methods) {
                if (method.getName().startsWith("create")){
                    dispatchTable.put(method.getName(), MethodType.CREATE);
                } else if (method.getName().startsWith("find")){
                    dispatchTable.put(method.getName(), MethodType.FIND);
                }
            }
        }

    }

    public void invalidateReference() {
        throw new IllegalStateException("A home reference must never be invalidated!");
    }

    public Object createProxy(ProxyInfo proxyInfo) {
        Object newProxy = null;
        try {

            InterfaceType interfaceType = InterfaceType.EJB_OBJECT;
            switch(this.interfaceType){
                case EJB_HOME: interfaceType = InterfaceType.EJB_OBJECT; break;
                case EJB_LOCAL_HOME: interfaceType = InterfaceType.EJB_LOCAL; break;
                case BUSINESS_REMOTE_HOME: interfaceType = InterfaceType.BUSINESS_REMOTE; break;
                case BUSINESS_LOCAL_HOME: interfaceType = InterfaceType.BUSINESS_LOCAL; break;
            }
            EjbObjectProxyHandler handler = newEjbObjectHandler(proxyInfo.getBeanContainer(), proxyInfo.getPrimaryKey(), proxyInfo.getDeploymentInfo().getDeploymentID(), interfaceType);
            handler.setLocal(isLocal());
            handler.doIntraVmCopy = this.doIntraVmCopy;
            Class[] interfaces = new Class[]{proxyInfo.getInterface(), IntraVmProxy.class};
            newProxy = ProxyManager.newProxyInstance(interfaces, handler);
        } catch (IllegalAccessException iae) {
            throw new RuntimeException("Could not create IVM proxy for " + proxyInfo.getInterface() + " interface");
        }
        if (newProxy == null) throw new RuntimeException("Could not create IVM proxy for " + proxyInfo.getInterface() + " interface");

        return newProxy;
    }

    protected abstract EjbObjectProxyHandler newEjbObjectHandler(RpcContainer container, Object pk, Object depID, InterfaceType interfaceType);

    protected Object _invoke(Object proxy, Method method, Object[] args) throws Throwable {

        if (logger.isInfoEnabled()) {
            logger.info("invoking method " + method.getName() + " on " + deploymentID);
        }

        String methodName = method.getName();

        try {
            java.lang.Object retValue;
            MethodType operation = dispatchTable.get(methodName);

            if (operation == null) {
                retValue = homeMethod(method, args, proxy);
            } else {
                switch (operation) {
                    /*-- CREATE ------------- <HomeInterface>.create(<x>) ---*/
                    case CREATE:
                        retValue = create(method, args, proxy);
                        break;
                    case FIND:
                        retValue = findX(method, args, proxy);
                        break;
                        /*-- GET EJB METADATA ------ EJBHome.getEJBMetaData() ---*/
                    case META_DATA:
                        retValue = getEJBMetaData(method, args, proxy);
                        break;
                        /*-- GET HOME HANDLE -------- EJBHome.getHomeHandle() ---*/
                    case HOME_HANDLE:
                        retValue = getHomeHandle(method, args, proxy);
                        break;
                        /*-- REMOVE ------------------------ EJBHome.remove() ---*/
                    case REMOVE: {
                        Class type = method.getParameterTypes()[0];

                        /*-- HANDLE ------- EJBHome.remove(Handle handle) ---*/
                        if (javax.ejb.Handle.class.isAssignableFrom(type)) {
                            retValue = removeWithHandle(method, args, proxy);
                        } else {
                            /*-- PRIMARY KEY ----- EJBHome.remove(Object key) ---*/
                            retValue = removeByPrimaryKey(method, args, proxy);
                        }
                        break;
                    }
                    default:
                        throw new RuntimeException("Inconsistent internal state: value " + operation + " for operation " + methodName);
                }
            }

            if (logger.isDebugEnabled()) {
                logger.debug("finished invoking method " + method.getName() + ". Return value:" + retValue);
            } else if (logger.isInfoEnabled()) {
                logger.info("finished invoking method " + method.getName());
            }

            return retValue;

            /*
            * The ire is thrown by the container system and propagated by
            * the server to the stub.
            */
        } catch (RemoteException re) {
            if (isLocal()) {
                throw new EJBException(re.getMessage(), (Exception) re.detail);
            } else {
                throw re;
            }

        } catch (org.apache.openejb.InvalidateReferenceException ire) {
            Throwable cause = ire.getRootCause();
            if (cause instanceof RemoteException && isLocal()) {
                RemoteException re = (RemoteException) cause;
                Throwable detail = (re.detail != null) ? re.detail : re;
                cause = new EJBException(re.getMessage(), (Exception) detail);
            }
            throw cause;
            /*
            * Application exceptions must be reported dirctly to the client. They
            * do not impact the viability of the proxy.
            */
        } catch (org.apache.openejb.ApplicationException ae) {
            throw ae.getRootCause();
            /*
            * A system exception would be highly unusual and would indicate a sever
            * problem with the container system.
            */
        } catch (org.apache.openejb.SystemException se) {
            if (isLocal()) {
                throw new EJBException("Container has suffered a SystemException", (Exception) se.getRootCause());
            } else {
                throw new RemoteException("Container has suffered a SystemException", se.getRootCause());
            }
        } catch (org.apache.openejb.OpenEJBException oe) {
            if (isLocal()) {
                throw new EJBException("Unknown Container Exception", (Exception) oe.getRootCause());
            } else {
                throw new RemoteException("Unknown Container Exception", oe.getRootCause());
            }
        } catch (Throwable t) {
            logger.info("finished invoking method " + method.getName() + " with exception:" + t, t);
            throw t;
        }
    }

    /*-------------------------------------------------*/
    /*  Home interface methods                         */
    /*-------------------------------------------------*/

    protected Object homeMethod(Method method, Object[] args, Object proxy) throws Throwable {
        checkAuthorization(method);
        return container.invoke(deploymentID, method, args, null, getThreadSpecificSecurityIdentity());
    }

    protected Object create(Method method, Object[] args, Object proxy) throws Throwable {
        ProxyInfo proxyInfo = (ProxyInfo) container.invoke(deploymentID, method, args, null, getThreadSpecificSecurityIdentity());
        assert proxyInfo != null: "Container returned a null ProxyInfo: ContainerID="+container.getContainerID();
        return createProxy(proxyInfo);
    }

    protected abstract Object findX(Method method, Object[] args, Object proxy) throws Throwable;

    /*-------------------------------------------------*/
    /*  EJBHome methods                                */
    /*-------------------------------------------------*/

    protected Object getEJBMetaData(Method method, Object[] args, Object proxy) throws Throwable {
        checkAuthorization(method);
        IntraVmMetaData metaData = new IntraVmMetaData(getDeploymentInfo().getHomeInterface(), getDeploymentInfo().getRemoteInterface(), getDeploymentInfo().getPrimaryKeyClass(), getDeploymentInfo().getComponentType());
        metaData.setEJBHome((EJBHome) proxy);
        return metaData;
    }

    protected Object getHomeHandle(Method method, Object[] args, Object proxy) throws Throwable {
        checkAuthorization(method);
        return new IntraVmHandle(proxy);
    }

    public org.apache.openejb.ProxyInfo getProxyInfo() {
        return new org.apache.openejb.ProxyInfo(getDeploymentInfo(), null, getDeploymentInfo().getHomeInterface(), container, interfaceType);
    }

    protected Object _writeReplace(Object proxy) throws ObjectStreamException {
        /*
         * If the proxy is being copied between bean instances in a RPC
         * call we use the IntraVmArtifact
         */
        if (IntraVmCopyMonitor.isIntraVmCopyOperation()) {
            return new IntraVmArtifact(proxy);
            /*
            * If the proxy is referenced by a stateful bean that is  being
            * passivated by the container we allow this object to be serialized.
            */
        } else if (IntraVmCopyMonitor.isStatefulPassivationOperation()) {
            return proxy;
            /*
            * If the proxy is being copied between class loaders
            * we allow this object to be serialized.
            */
        } else if (IntraVmCopyMonitor.isCrossClassLoaderOperation()) {
            return proxy;
            /*
            * If the proxy is serialized outside the core container system,
            * we allow the application server to handle it.
            */
        } else {
            ApplicationServer applicationServer = ServerFederation.getApplicationServer();
            return applicationServer.getEJBHome(this.getProxyInfo());
        }
    }

    protected Object removeWithHandle(Method method, Object[] args, Object proxy) throws Throwable {

        IntraVmHandle handle = (IntraVmHandle) args[0];
        Object primKey = handle.getPrimaryKey();
        EjbObjectProxyHandler stub;
        try {
            stub = (EjbObjectProxyHandler) ProxyManager.getInvocationHandler(handle.getEJBObject());
        } catch (IllegalArgumentException e) {

            stub = null;
        }

        container.invoke(deploymentID, method, args, primKey, getThreadSpecificSecurityIdentity());

        /*
         * This operation takes care of invalidating all the EjbObjectProxyHanders associated with
         * the same RegistryId. See this.createProxy().
         */
        if (stub != null) {
            invalidateAllHandlers(stub.getRegistryId());
        }
        return null;
    }

    protected abstract Object removeByPrimaryKey(Method method, Object[] args, Object proxy) throws Throwable;
}
