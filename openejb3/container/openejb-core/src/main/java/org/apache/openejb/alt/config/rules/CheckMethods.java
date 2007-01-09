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
package org.apache.openejb.alt.config.rules;

import org.apache.openejb.OpenEJBException;
import org.apache.openejb.alt.config.Bean;
import org.apache.openejb.alt.config.EjbSet;
import org.apache.openejb.alt.config.EntityBean;
import org.apache.openejb.alt.config.SessionBean;
import org.apache.openejb.alt.config.ValidationFailure;
import org.apache.openejb.alt.config.ValidationRule;
import org.apache.openejb.alt.config.ValidationWarning;
import org.apache.openejb.util.SafeToolkit;

import javax.ejb.EJBLocalObject;
import java.lang.reflect.Method;

public class CheckMethods implements ValidationRule {

    EjbSet set;

    public void validate(EjbSet set) {

        this.set = set;

        Bean[] beans = set.getBeans();
        for (int i = 0; i < beans.length; i++) {
            Bean b = beans[i];
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

    private void check_localHomeInterfaceMethods(Bean b) {
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

    private void check_localInterfaceMethods(Bean b) {
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

                ValidationFailure failure = new ValidationFailure("no.busines.method");
                failure.setDetails(interfaceMethods[i].getName(), interfaceMethods[i].toString(), "local", intrface.getName(), beanClass.getName());
                failure.setBean(b);

                set.addFailure(failure);

            }
        }

    }

    private void check_remoteInterfaceMethods(Bean b) {

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

                ValidationFailure failure = new ValidationFailure("no.busines.method");
                failure.setDetails(interfaceMethods[i].getName(), interfaceMethods[i].toString(), "remote", intrface.getName(), beanClass.getName());
                failure.setBean(b);

                set.addFailure(failure);

            }
        }
    }

    private void check_homeInterfaceMethods(Bean b) {
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

    public boolean check_hasCreateMethod(Bean b, Class bean, Class home) {

        if (b instanceof org.apache.openejb.alt.config.SessionBean && !javax.ejb.SessionBean.class.isAssignableFrom(bean)){
            // This is a pojo-style bean
            return false;
        }

        Method[] homeMethods = home.getMethods();

        boolean hasCreateMethod = false;

        for (int i = 0; i < homeMethods.length && !hasCreateMethod; i++) {
            hasCreateMethod = homeMethods[i].getName().startsWith("create");
        }

        if (!hasCreateMethod) {

            ValidationFailure failure = new ValidationFailure("no.home.create");
            failure.setDetails(b.getHome(), b.getRemote());
            failure.setBean(b);

            set.addFailure(failure);

        }

        return hasCreateMethod;
    }

    public boolean check_createMethodsAreImplemented(Bean b, Class bean, Class home) {
        boolean result = true;

        Method[] homeMethods = home.getMethods();
        Method[] beanMethods = bean.getMethods();

        for (int i = 0; i < homeMethods.length; i++) {
            if (!homeMethods[i].getName().startsWith("create")) continue;
            Method create = homeMethods[i];

            StringBuilder ejbCreateName = new StringBuilder(create.getName());
            ejbCreateName.replace(0,1, "ejbC");
            try {
                bean.getMethod(ejbCreateName.toString(), create.getParameterTypes());
            } catch (NoSuchMethodException e) {
                result = false;

                String paramString = getParameters(create);

                if (b instanceof EntityBean) {
                    EntityBean entity = (EntityBean) b;

                    ValidationFailure failure = new ValidationFailure("entity.no.ejb.create");
                    failure.setDetails(b.getEjbClass(), entity.getPrimaryKey(), ejbCreateName.toString(), paramString);
                    failure.setBean(b);

                    set.addFailure(failure);

                } else {

                    ValidationFailure failure = new ValidationFailure("session.no.ejb.create");
                    failure.setDetails(b.getEjbClass(), ejbCreateName.toString(), paramString);
                    failure.setBean(b);

                    set.addFailure(failure);

                }
            }
        }

        return result;
    }

    public boolean check_postCreateMethodsAreImplemented(Bean b, Class bean, Class home) {
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

                ValidationFailure failure = new ValidationFailure("no.ejb.post.create");
                failure.setDetails(b.getEjbClass(), ejbPostCreateName.toString(), paramString);
                failure.setBean(b);

                set.addFailure(failure);

            }
        }

        return result;
    }

    public boolean check_unusedCreateMethods(Bean b, Class bean, Class home) {
        boolean result = true;

        Method[] homeMethods = home.getMethods();
        Method[] beanMethods = bean.getMethods();

        for (int i = 0; i < homeMethods.length; i++) {
            if (!beanMethods[i].getName().startsWith("ejbCreate")) continue;
            Method ejbCreate = beanMethods[i];
            StringBuilder create = new StringBuilder(ejbCreate.getName());
            create.replace(0,4, "c");
            try {
                home.getMethod(create.toString(), ejbCreate.getParameterTypes());
            } catch (NoSuchMethodException e) {
                result = false;

                String paramString = getParameters(ejbCreate);

                ValidationWarning warning = new ValidationWarning("unused.ejb.create");
                warning.setDetails(b.getEjbClass(), ejbCreate.getName(), create.toString(), paramString, home.getName());
                warning.setBean(b);

                set.addWarning(warning);

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

    private String getParameters(Method method) {
        Class[] params = method.getParameterTypes();
        StringBuffer paramString = new StringBuffer(512);

        if (params.length > 0) {
            paramString.append(params[0].getName());
        }

        for (int i = 1; i < params.length; i++) {
            paramString.append(", ");
            paramString.append(params[i]);
        }

        return paramString.toString();
    }

    private Class loadClass(String clazz) throws OpenEJBException {
        ClassLoader cl = set.getClassLoader();
        try {
            return cl.loadClass(clazz);
        } catch (ClassNotFoundException cnfe) {
            throw new OpenEJBException(SafeToolkit.messages.format("cl0007", clazz, set.getJarPath()));
        }
    }
}

