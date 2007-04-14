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
package org.apache.openejb.core.stateful;

import org.apache.openejb.RpcContainer;
import org.apache.openejb.InterfaceType;
import org.apache.openejb.DeploymentInfo;
import org.apache.openejb.core.ivm.EjbObjectProxyHandler;
import org.apache.openejb.util.proxy.ProxyManager;

import java.lang.reflect.Method;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.List;

public class StatefulEjbObjectHandler extends EjbObjectProxyHandler {

    public StatefulEjbObjectHandler(DeploymentInfo deploymentInfo, Object pk, InterfaceType interfaceType, List<Class> interfaces) {
        super(deploymentInfo, pk, interfaceType, interfaces);
    }

    public Object getRegistryId() {
        return primaryKey;
    }

    protected Object getPrimaryKey(Method method, Object[] args, Object proxy) throws Throwable {
        throw new RemoteException("Session objects are private resources and do not have primary keys");
    }

    protected Object isIdentical(Method method, Object[] args, Object proxy) throws Throwable {
        checkAuthorization(method);
        EjbObjectProxyHandler handler = (EjbObjectProxyHandler) ProxyManager.getInvocationHandler(proxy);
        return new Boolean(primaryKey.equals(handler.primaryKey));
    }

    protected Object remove(Method method, Object[] args, Object proxy) throws Throwable {
        checkAuthorization(method);
        Object value = container.invoke(deploymentID, method, args, primaryKey);

        invalidateAllHandlers(getRegistryId());
        return value;
    }

}
