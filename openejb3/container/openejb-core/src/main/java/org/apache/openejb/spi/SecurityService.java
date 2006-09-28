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
package org.apache.openejb.spi;

public interface SecurityService extends Service {

    public boolean isCallerAuthorized(Object securityIdentity, String [] roleNames);

    public Object translateTo(Object securityIdentity, Class type);

    /*
     * Associates a security identity object with the current thread. Setting 
     * this argument to null, will effectively dissociate the thread with a
     * security identity.  This is used when access enterprise beans through 
     * the global JNDI name space. Its not used when calling invoke on a 
     * RpcContainer object.
    */
    public void setSecurityIdentity(Object securityIdentity);

    /*
    * Obtains the security identity associated with the current thread.
    * If there is no association, then null is returned. 
    */
    public Object getSecurityIdentity();
}