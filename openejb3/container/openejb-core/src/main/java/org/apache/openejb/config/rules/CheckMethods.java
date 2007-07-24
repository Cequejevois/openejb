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
package org.apache.openejb.config.rules;

import org.apache.openejb.OpenEJBException;
import org.apache.openejb.jee.EnterpriseBean;
import org.apache.openejb.jee.RemoteBean;
import org.apache.openejb.jee.EntityBean;
import org.apache.openejb.jee.SessionBean;
import org.apache.openejb.config.EjbSet;
import org.apache.openejb.config.ValidationFailure;
import org.apache.openejb.config.ValidationRule;
import org.apache.openejb.config.ValidationWarning;
import org.apache.openejb.config.ValidationError;
import org.apache.openejb.util.SafeToolkit;

import javax.ejb.EJBLocalObject;
import java.lang.reflect.Method;

public class CheckMethods extends ValidationBase {

    public void validate(EjbSet set) {

        this.set = set;

        for (EnterpriseBean bean : set.getEjbJar().getEnterpriseBeans()) {
            if (!(bean instanceof RemoteBean)) continue;
            RemoteBean b = (RemoteBean) bean;

            if (b.getHome() != null) {
                check_remoteInterfaceMethods(b);
                check_homeInterfaceMethods(b);
            }
            if (b.getLocalHome() != null) {
                check_localInterfaceMethods(b);
                check_localHomeInterfaceMethods(b);
            }
        }
    }

    private void check_localHomeInterfaceMethods(RemoteBean b) {
        Class home = null;
        Class bean = null;
        try {
            home = loadClass(b.getLocalHome());
            bean = loadClass(b.getEjbClass());
        } catch (OpenEJBException e) {
            return;
        }

        if (check_hasCreateMethod(b, bean, home)) {
            check_createMethodsAreImplemented(b, bean, home);
            check_postCreateMethodsAreImplemented(b, bean, home);
        }

        check_unusedCreateMethods(b, bean, home);
    }

    private void check_localInterfaceMethods(RemoteBean b) {
        Class intrface = null;
        Class beanClass = null;
        try {
            intrface = loadClass(b.getLocal());
            beanClass = loadClass(b.getEjbClass());
        } catch (OpenEJBException e) {
            return;
        }

        Method[] interfaceMethods = intrface.getMethods();
        Method[] beanClassMethods = intrface.getMethods();

        for (int i = 0; i < interfaceMethods.length; i++) {
            if (interfaceMethods[i].getDeclaringClass() == EJBLocalObject.class) continue;
            try {
                String name = interfaceMethods[i].getName();
                Class[] params = interfaceMethods[i].getParameterTypes();
                Method beanMethod = beanClass.getMethod(name, params);
            } catch (NoSuchMethodException nsme) {

                fail(b, "no.busines.method", interfaceMethods[i].getName(), interfaceMethods[i].toString(), "local", intrface.getName(), beanClass.getName());
            }
        }

    }

    private void check_remoteInterfaceMethods(RemoteBean b) {

        Class intrface = null;
        Class beanClass = null;
        try {
            intrface = loadClass(b.getRemote());
            beanClass = loadClass(b.getEjbClass());
        } catch (OpenEJBException e) {
            return;
        }

        Method[] interfaceMethods = intrface.getMethods();
        Method[] beanClassMethods = intrface.getMethods();

        for (int i = 0; i < interfaceMethods.length; i++) {
            if (interfaceMethods[i].getDeclaringClass() == javax.ejb.EJBObject.class) continue;
            try {
                String name = interfaceMethods[i].getName();
                Class[] params = interfaceMethods[i].getParameterTypes();
                Method beanMethod = beanClass.getMethod(name, params);
            } catch (NoSuchMethodException nsme) {

                fail(b, "no.busines.method", interfaceMethods[i].getName(), interfaceMethods[i].toString(), "remote", intrface.getName(), beanClass.getName());

            }
        }
    }


    private void check_homeInterfaceMethods(RemoteBean b) {
        Class home = null;
        Class bean = null;
        try {
            home = loadClass(b.getHome());
            bean = loadClass(b.getEjbClass());
        } catch (OpenEJBException e) {
            return;
        }

        if (check_hasCreateMethod(b, bean, home)) {
            check_createMethodsAreImplemented(b, bean, home);
            check_postCreateMethodsAreImplemented(b, bean, home);
        }

        check_unusedCreateMethods(b, bean, home);
    }

    public boolean check_hasCreateMethod(RemoteBean b, Class bean, Class home) {

        if (b instanceof SessionBean && !javax.ejb.SessionBean.class.isAssignableFrom(bean)){
            // This is a pojo-style bean
            return false;
        }

        if (b instanceof EntityBean) {
            // entity beans are not required to have a create method
            return false;
        }

        Method[] homeMethods = home.getMethods();

        boolean hasCreateMethod = false;

        for (int i = 0; i < homeMethods.length && !hasCreateMethod; i++) {
            hasCreateMethod = homeMethods[i].getName().startsWith("create");
        }

        if (!hasCreateMethod) {

            fail(b, "no.home.create", b.getHome(), b.getRemote());

        }

        return hasCreateMethod;
    }

    public boolean check_createMethodsAreImplemented(RemoteBean b, Class bean, Class home) {
        boolean result = true;

        Method[] homeMethods = home.getMethods();

        for (int i = 0; i < homeMethods.length; i++) {
            if (!homeMethods[i].getName().startsWith("create")) continue;

            Method create = homeMethods[i];

            StringBuilder ejbCreateName = new StringBuilder(create.getName());
            ejbCreateName.replace(0,1, "ejbC");

            try {
                if (EnterpriseBean.class.isAssignableFrom(bean)) {
                    bean.getMethod(ejbCreateName.toString(), create.getParameterTypes());
                } else {
                    // TODO: Check for Init method in pojo session bean class
                }
            } catch (NoSuchMethodException e) {
                result = false;

                String paramString = getParameters(create);

                if (b instanceof EntityBean) {
                    EntityBean entity = (EntityBean) b;

                    fail(b, "entity.no.ejb.create", b.getEjbClass(), entity.getPrimKeyClass(), ejbCreateName.toString(), paramString);

                } else {

                    fail(b, "session.no.ejb.create", b.getEjbClass(), ejbCreateName.toString(), paramString);

                }
            }
        }

        return result;
    }

    public boolean check_postCreateMethodsAreImplemented(RemoteBean b, Class bean, Class home) {
        boolean result = true;

        if (b instanceof SessionBean) return true;

        Method[] homeMethods = home.getMethods();
        Method[] beanMethods = bean.getMethods();

        for (int i = 0; i < homeMethods.length; i++) {
            if (!homeMethods[i].getName().startsWith("create")) continue;
            Method create = homeMethods[i];
            StringBuilder ejbPostCreateName = new StringBuilder(create.getName());
            ejbPostCreateName.replace(0,1, "ejbPostC");
            try {
                bean.getMethod(ejbPostCreateName.toString(), create.getParameterTypes());
            } catch (NoSuchMethodException e) {
                result = false;

                String paramString = getParameters(create);

                fail(b, "no.ejb.post.create", b.getEjbClass(), ejbPostCreateName.toString(), paramString);

            }
        }

        return result;
    }

    public boolean check_unusedCreateMethods(RemoteBean b, Class bean, Class home) {
        boolean result = true;

        Method[] homeMethods = home.getMethods();
        Method[] beanMethods = bean.getMethods();

        for (int i = 0; i < homeMethods.length; i++) {
            if (!beanMethods[i].getName().startsWith("ejbCreate")) continue;
            Method ejbCreate = beanMethods[i];
            StringBuilder create = new StringBuilder(ejbCreate.getName());
            create.replace(0,4, "c");
            try {
                // TODO, don't just check the remote home interface, there is a local too
                home.getMethod(create.toString(), ejbCreate.getParameterTypes());
            } catch (NoSuchMethodException e) {
                result = false;

                String paramString = getParameters(ejbCreate);

                warn(b, "unused.ejb.create", b.getEjbClass(), ejbCreate.getName(), create.toString(), paramString, home.getName());

            }
        }

        return result;
    }

/// public void check_findMethods(){
///     if(this.componentType == this.BMP_ENTITY ){
///
///         String beanMethodName = "ejbF"+method.getName().substring(1);
///         beanMethod = beanClass.getMethod(beanMethodName,method.getParameterTypes());
///     }
/// }
///
/// public void check_homeMethods(){
///     String beanMethodName = "ejbHome"+method.getName().substring(0,1).toUpperCase()+method.getName().substring(1);
///     beanMethod = beanClass.getMethod(beanMethodName,method.getParameterTypes());
/// }

}

