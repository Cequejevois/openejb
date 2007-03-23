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
package org.apache.openejb.core.security;

import junit.framework.TestCase;
import org.apache.openejb.core.ivm.naming.InitContextFactory;
import org.apache.openejb.config.ConfigurationFactory;
import org.apache.openejb.assembler.classic.Assembler;
import org.apache.openejb.assembler.classic.ProxyFactoryInfo;
import org.apache.openejb.assembler.classic.TransactionServiceInfo;
import org.apache.openejb.assembler.classic.SecurityServiceInfo;
import org.apache.openejb.assembler.classic.ConnectionManagerInfo;
import org.apache.openejb.assembler.classic.StatelessSessionContainerInfo;
import org.apache.openejb.assembler.classic.EjbJarInfo;
import org.apache.openejb.jee.EjbJar;
import org.apache.openejb.jee.StatelessBean;

import javax.naming.InitialContext;
import javax.ejb.Stateless;
import javax.annotation.security.RolesAllowed;
import javax.annotation.security.PermitAll;
import javax.annotation.security.DenyAll;
import javax.annotation.security.RunAs;

/**
 * @version $Rev$ $Date$
 */
public class SecurityTest extends TestCase {


    public void _test() throws Exception {

    }
    public void test() throws Exception {
        System.setProperty(javax.naming.Context.INITIAL_CONTEXT_FACTORY, InitContextFactory.class.getName());

        ConfigurationFactory config = new ConfigurationFactory();
        Assembler assembler = new Assembler();

        assembler.createProxyFactory(config.configureService(ProxyFactoryInfo.class));
        assembler.createTransactionManager(config.configureService(TransactionServiceInfo.class));
        assembler.createSecurityService(config.configureService(SecurityServiceInfo.class));

        assembler.createConnectionManager(config.configureService(ConnectionManagerInfo.class));

        // containers
        assembler.createContainer(config.configureService(StatelessSessionContainerInfo.class));

        EjbJar ejbJar = new EjbJar("SecurityTest");

        ejbJar.addEnterpriseBean(new StatelessBean(FooBean.class));

        EjbJarInfo ejbJarInfo = config.configureApplication(ejbJar);

        assembler.createApplication(ejbJarInfo);

        InitialContext ctx = new InitialContext();
//        Target target = (Target) ctx.lookup("TargetBeanBusinessLocal");
    }

    @Stateless
    public static class FooBean implements Foo {
        @RolesAllowed({"Committer"})
        public String svnCommit(String s) {
            return s;
        }

        @RolesAllowed({"Committer", "Contributor"})
        public String submitPatch(String s) {
            return s;
        }

        @PermitAll
        public String svnCheckout(String s) {
            return s;
        }

        @DenyAll
        public String deleteProject(String s) {
            return s;
        }
    }

    @Stateless
    @RunAs("Contributor")
    public static class BarBean implements Foo {
        @RolesAllowed({"Committer"})
        public String svnCommit(String s) {
            return s;
        }

        @RolesAllowed({"Committer", "Contributor"})
        public String submitPatch(String s) {
            return s;
        }

        @PermitAll
        public String svnCheckout(String s) {
            return s;
        }

        @DenyAll
        public String deleteProject(String s) {
            return s;
        }
    }

    public static interface Foo {

        public String svnCommit(String s);

        public String submitPatch(String s);

        public String svnCheckout(String s);

        public String deleteProject(String s);
    }
}
