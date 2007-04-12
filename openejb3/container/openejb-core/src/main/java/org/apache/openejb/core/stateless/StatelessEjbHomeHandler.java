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

import javax.ejb.RemoveException;

import org.apache.openejb.InterfaceType;
import org.apache.openejb.RpcContainer;
import org.apache.openejb.core.ivm.EjbHomeProxyHandler;
import org.apache.openejb.core.ivm.EjbObjectProxyHandler;
import org.apache.openejb.core.ivm.IntraVmHandle;
import org.apache.openejb.util.proxy.ProxyManager;

public class StatelessEjbHomeHandler extends EjbHomeProxyHandler {

    public StatelessEjbHomeHandler(RpcContainer container, Object pk, Object depID, InterfaceType interfaceType) {
        super(container, pk, depID, interfaceType);
    }

    protected Object findX(Method method, Object[] args, Object proxy) throws Throwable {
        throw new UnsupportedOperationException("Stateful beans may not have find methods");
    }

    protected Object removeByPrimaryKey(Method method, Object[] args, Object proxy) throws Throwable {
        throw new RemoveException("Session objects are private resources and do not have primary keys");
    }

    protected Object removeWithHandle(Method method, Object[] args, Object proxy) throws Throwable {
        // stateless can't be removed
        return null;
    }

    protected EjbObjectProxyHandler newEjbObjectHandler(RpcContainer container, Object pk, Object depID, InterfaceType interfaceType) {
        return new StatelessEjbObjectHandler(container, pk, depID, interfaceType);
    }

}
