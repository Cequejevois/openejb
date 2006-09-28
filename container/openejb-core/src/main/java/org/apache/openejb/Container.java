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
package org.apache.openejb;

public interface Container {

    final public static int STATELESS = 1;
    final public static int STATEFUL = 2;
    final public static int ENTITY = 3;
    final public static int MESSAGE_DRIVEN = 4;

    public int getContainerType();

    public Object getContainerID();

    public DeploymentInfo getDeploymentInfo(Object deploymentID);

    public DeploymentInfo [] deployments();

    public void deploy(Object deploymentID, DeploymentInfo info) throws OpenEJBException;
}
