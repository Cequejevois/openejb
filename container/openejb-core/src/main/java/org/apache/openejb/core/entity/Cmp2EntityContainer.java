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
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.openejb.core.entity;

import org.apache.openejb.DeploymentInfo;
import org.apache.openejb.OpenEJBException;
import org.apache.openejb.ContainerType;
import org.apache.openejb.spi.SecurityService;
import org.apache.openejb.core.ThreadContext;
import org.apache.openejb.core.transaction.TransactionContainer;

import javax.transaction.TransactionManager;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * @version $Rev$ $Date$
 */
public class Cmp2EntityContainer implements org.apache.openejb.RpcContainer, TransactionContainer {

    private Map deploymentRegistry = new HashMap();

    private Object containerID = null;

    public Cmp2EntityContainer(Object id, TransactionManager transactionManager, SecurityService securityService) throws OpenEJBException {
        this.containerID = id;
    }

    public Object invoke(Object deployID, Method callMethod, Object [] args, Object primKey, Object securityIdentity) throws OpenEJBException {
        return null;
    }

    public void deploy(DeploymentInfo info) throws OpenEJBException {
        Map registry = new HashMap(deploymentRegistry);
        registry.put(info.getDeploymentID(), info);
        deploymentRegistry = registry;
        org.apache.openejb.core.CoreDeploymentInfo di = (org.apache.openejb.core.CoreDeploymentInfo) info;
        di.setContainer(this);
    }

    public void undeploy(DeploymentInfo info) throws OpenEJBException {
        deploymentRegistry.remove(info.getDeploymentID());
    }

    public DeploymentInfo [] deployments() {
        return (DeploymentInfo []) deploymentRegistry.values().toArray(new DeploymentInfo[deploymentRegistry.size()]);
    }

    public Object getContainerID() {
        return containerID;
    }

    public ContainerType getContainerType() {
        return ContainerType.CMP_ENTITY;
    }

    public DeploymentInfo getDeploymentInfo(Object deploymentID) {
        return (DeploymentInfo) deploymentRegistry.get(deploymentID);
    }

    public void discardInstance(Object instance, ThreadContext context) {
    }
}

