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
package org.apache.openejb.alt.config;

import org.apache.openejb.OpenEJBException;
import org.apache.openejb.jee.AroundInvoke;
import org.apache.openejb.jee.EjbJar;
import org.apache.openejb.jee.EnterpriseBean;
import org.apache.openejb.jee.LifecycleCallback;
import org.apache.openejb.jee.RemoteBean;
import org.apache.openejb.jee.SessionBean;
import org.apache.openejb.jee.StatefulBean;
import org.apache.openejb.jee.StatelessBean;
import org.apache.openejb.jee.TransactionType;
import org.apache.openejb.jee.AssemblyDescriptor;
import org.apache.openejb.jee.TransAttribute;
import org.apache.openejb.jee.MethodTransaction;
import org.apache.openejb.jee.ContainerTransaction;
import org.apache.openejb.jee.MethodParams;
import org.apache.openejb.jee.MessageDrivenBean;
import org.apache.openejb.jee.ApplicationClient;
import org.apache.openejb.jee.EjbRef;
import org.apache.openejb.jee.InjectionTarget;
import org.apache.openejb.jee.JndiConsumer;
import org.apache.openejb.util.Logger;
import org.apache.xbean.finder.ClassFinder;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.ejb.Local;
import javax.ejb.LocalHome;
import javax.ejb.PostActivate;
import javax.ejb.PrePassivate;
import javax.ejb.Remote;
import javax.ejb.RemoteHome;
import javax.ejb.Stateful;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionManagement;
import javax.ejb.TransactionManagementType;
import javax.ejb.TransactionAttributeType;
import javax.ejb.MessageDriven;
import javax.ejb.EJB;
import javax.ejb.EJBHome;
import java.io.File;
import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

/**
 * @version $Rev$ $Date$
 */
public class AnnotationDeployer implements DynamicDeployer {

    public static final Logger logger = Logger.getInstance("OpenEJB.startup", AnnotationDeployer.class.getPackage().getName());
    private final DynamicDeployer deployer;
    private final DiscoverBeansInClassLoader discoverBeansInClassLoader;
    private final ProcessAnnotatedBeans processAnnotatedBeans;

    public AnnotationDeployer(DynamicDeployer deployer) {
        this.deployer = deployer;

        discoverBeansInClassLoader = new DiscoverBeansInClassLoader();
        processAnnotatedBeans = new ProcessAnnotatedBeans();
    }

    public ClientModule deploy(ClientModule clientModule) throws OpenEJBException {
        clientModule = discoverBeansInClassLoader.deploy(clientModule);
        clientModule = deployer.deploy(clientModule);
        clientModule = processAnnotatedBeans.deploy(clientModule);
        return clientModule;
    }

    public EjbModule deploy(EjbModule ejbModule) throws OpenEJBException {
        ejbModule = discoverBeansInClassLoader.deploy(ejbModule);
        ejbModule = deployer.deploy(ejbModule);
        ejbModule = processAnnotatedBeans.deploy(ejbModule);
        return ejbModule;
    }

    public static class DiscoverBeansInClassLoader implements DynamicDeployer {

        public ClientModule deploy(ClientModule clientModule) throws OpenEJBException {
            return clientModule;
        }

        public EjbModule deploy(EjbModule ejbModule) throws OpenEJBException {
            ClassFinder finder;
            if (ejbModule.getJarURI() != null) {
                try {
                    String location = ejbModule.getJarURI();
                    File file = new File(location);

                    URL url;
                    if (file.exists()) {
                        url = file.toURL();
                    } else {
                        url = new URL(location);
                    }
                    finder = new ClassFinder(ejbModule.getClassLoader(), url);
                } catch (MalformedURLException e) {
                    DeploymentLoader.logger.warning("Unable to scrape for @Stateful, @Stateless or @MessageDriven annotations.  EjbModule URL not valid: " + ejbModule.getJarURI(), e);
                    return ejbModule;
                }
            } else {
                try {
                    finder = new ClassFinder(ejbModule.getClassLoader());
                } catch (Exception e) {
                    DeploymentLoader.logger.warning("Unable to scrape for @Stateful, @Stateless or @MessageDriven annotations.  ClassFinder failed.", e);
                    return ejbModule;
                }
            }

            EjbJar ejbJar = ejbModule.getEjbJar();

            List<Class> classes = finder.findAnnotatedClasses(Stateless.class);
            for (Class beanClass : classes) {
                Stateless stateless = (Stateless) beanClass.getAnnotation(Stateless.class);
                String ejbName = stateless.name().length() == 0 ? beanClass.getSimpleName() : stateless.name();
                if (ejbJar.getEnterpriseBean(ejbName) == null) {
                    ejbJar.addEnterpriseBean(new StatelessBean(ejbName, beanClass.getName()));
                }
            }

            classes = finder.findAnnotatedClasses(Stateful.class);
            for (Class beanClass : classes) {
                Stateful stateless = (Stateful) beanClass.getAnnotation(Stateful.class);
                String ejbName = stateless.name().length() == 0 ? beanClass.getName() : stateless.name();
                if (ejbJar.getEnterpriseBean(ejbName) == null) {
                    ejbJar.addEnterpriseBean(new StatefulBean(ejbName, beanClass.getName()));
                }
            }

            classes = finder.findAnnotatedClasses(MessageDriven.class);
            for (Class beanClass : classes) {
                MessageDriven mdb = (MessageDriven) beanClass.getAnnotation(MessageDriven.class);
                String ejbName = mdb.name().length() == 0 ? beanClass.getName() : mdb.name();
                if (ejbJar.getEnterpriseBean(ejbName) == null) {
                    MessageDrivenBean messageBean = new MessageDrivenBean(ejbName);
                    Class interfce = mdb.messageListenerInterface();
                    if (interfce != null){
                        messageBean.setMessagingType(interfce.getName());
                    }
                    ejbJar.addEnterpriseBean(messageBean);
                }
            }

            return ejbModule;
        }
    }

    public static class ProcessAnnotatedBeans implements DynamicDeployer {
        public ClientModule deploy(ClientModule clientModule) throws OpenEJBException {
            ClassLoader classLoader = clientModule.getClassLoader();
            Class clazz = null;
            try {
                clazz = classLoader.loadClass(clientModule.getMainClass());
            } catch (ClassNotFoundException e) {
                throw new OpenEJBException("Unable to load Client main-class: "+clientModule.getMainClass(), e);
            }
            ApplicationClient client = clientModule.getApplicationClient();

            buildAnnotatedRefs(clazz, client);

            return clientModule;
        }

        public EjbModule deploy(EjbModule ejbModule) throws OpenEJBException {

            ClassLoader classLoader = ejbModule.getClassLoader();
            EnterpriseBean[] enterpriseBeans = ejbModule.getEjbJar().getEnterpriseBeans();
            for (EnterpriseBean bean : enterpriseBeans) {
                final String ejbName = bean.getEjbName();

                Class clazz = null;
                try {
                    clazz = classLoader.loadClass(bean.getEjbClass());
                } catch (ClassNotFoundException e) {
                    throw new OpenEJBException("Unable to load bean class: " + bean.getEjbClass());
                }
                ClassFinder classFinder = new ClassFinder(clazz);

                if (bean.getTransactionType() == null) {
                    TransactionManagement tx = (TransactionManagement) clazz.getAnnotation(TransactionManagement.class);
                    TransactionManagementType transactionType = TransactionManagementType.CONTAINER;
                    if(tx != null){
                        transactionType = tx.value();
                    }
                    switch(transactionType){
                        case BEAN: bean.setTransactionType(TransactionType.BEAN); break;
                        case CONTAINER: bean.setTransactionType(TransactionType.CONTAINER); break;
                    }
                }

                AssemblyDescriptor assemblyDescriptor = ejbModule.getEjbJar().getAssemblyDescriptor();
                if (assemblyDescriptor == null){
                    assemblyDescriptor = new AssemblyDescriptor();
                    ejbModule.getEjbJar().setAssemblyDescriptor(assemblyDescriptor);
                }

                if (bean.getTransactionType() == TransactionType.CONTAINER){
                    Map<String, List<MethodTransaction>> methodTransactions = assemblyDescriptor.getMethodTransactions(ejbName);

                    // SET THE DEFAULT
                    if (!methodTransactions.containsKey("*")){
                        TransactionAttribute attribute = (TransactionAttribute) clazz.getAnnotation(TransactionAttribute.class);
                        if (attribute != null){
                            ContainerTransaction ctx = new ContainerTransaction(cast(attribute.value()), ejbName, "*");
                            assemblyDescriptor.getContainerTransaction().add(ctx);
                        }
                    }

                    List<Method> methods = classFinder.findAnnotatedMethods(TransactionAttribute.class);
                    for (Method method : methods) {
                        TransactionAttribute attribute = method.getAnnotation(TransactionAttribute.class);
                        if (!methodTransactions.containsKey(method.getName())){
                            // no method with this name in descriptor
                            addContainerTransaction(attribute, ejbName, method, assemblyDescriptor);
                        } else {
                            // method name already declared
                            List<MethodTransaction> list = methodTransactions.get(method.getName());
                            for (MethodTransaction mtx : list) {
                                MethodParams methodParams = mtx.getMethodParams();
                                if (methodParams == null){
                                    // params not specified, so this is more specific
                                    addContainerTransaction(attribute, ejbName, method, assemblyDescriptor);
                                } else {
                                    List<String> params1 = methodParams.getMethodParam();
                                    String[] params2 = asStrings(method.getParameterTypes());
                                    if (params1.size() != params2.length) {
                                        // params not the same
                                        addContainerTransaction(attribute, ejbName, method, assemblyDescriptor);
                                    } else {
                                        for (int i = 0; i < params1.size(); i++) {
                                            String a = params1.get(i);
                                            String b = params2[i];
                                            if (!a.equals(b)){
                                                // params not the same
                                                addContainerTransaction(attribute, ejbName, method, assemblyDescriptor);
                                                break;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                LifecycleCallback postConstruct = getFirst(bean.getPostConstruct());
                if (postConstruct == null) {
                    Method method = getFirst(classFinder.findAnnotatedMethods(PostConstruct.class));
                    if (method != null) bean.addPostConstruct(method.getName());
                }

                LifecycleCallback preDestroy = getFirst(bean.getPreDestroy());
                if (preDestroy == null) {
                    Method method = getFirst(classFinder.findAnnotatedMethods(PreDestroy.class));
                    if (method != null) bean.addPreDestroy(method.getName());
                }

                AroundInvoke aroundInvoke = getFirst(bean.getAroundInvoke());
                if (aroundInvoke == null) {
                    Method method = getFirst(classFinder.findAnnotatedMethods(javax.interceptor.AroundInvoke.class));
                    if (method != null) bean.addAroundInvoke(method.getName());
                }

                if (bean instanceof org.apache.openejb.jee.SessionBean) {
                    org.apache.openejb.jee.SessionBean sessionBean = (org.apache.openejb.jee.SessionBean) bean;

                    LifecycleCallback postActivate = getFirst(sessionBean.getPostActivate());
                    if (postActivate == null) {
                        Method method = getFirst(classFinder.findAnnotatedMethods(PostActivate.class));
                        if (method != null) sessionBean.addPostActivate(method.getName());
                    }

                    LifecycleCallback prePassivate = getFirst(sessionBean.getPrePassivate());
                    if (prePassivate == null) {
                        Method method = getFirst(classFinder.findAnnotatedMethods(PrePassivate.class));
                        if (method != null) sessionBean.addPrePassivate(method.getName());
                    }
                }

                if (bean instanceof RemoteBean) {
                    RemoteBean remoteBean = (RemoteBean) bean;

                    if (remoteBean.getHome() == null) {
                        RemoteHome remoteHome = (RemoteHome) clazz.getAnnotation(RemoteHome.class);
                        if (remoteHome != null) {
                            Class homeClass = remoteHome.value();
                            try {
                                Method create = homeClass.getMethod("create");
                                Class remoteClass = create.getReturnType();
                                remoteBean.setHome(homeClass.getName());
                                remoteBean.setRemote(remoteClass.getName());
                            } catch (NoSuchMethodException e) {
                                logger.error("Class annotated as a RemoteHome has no 'create()' method.  Unable to determine remote interface type.  Bean class: " + clazz.getName() + ",  Home class: " + homeClass.getName());
                            }
                        }
                    }

                    if (remoteBean.getLocalHome() == null) {
                        LocalHome localHome = (LocalHome) clazz.getAnnotation(LocalHome.class);
                        if (localHome != null) {
                            Class homeClass = localHome.value();
                            try {
                                Method create = homeClass.getMethod("create");
                                Class remoteClass = create.getReturnType();
                                remoteBean.setHome(homeClass.getName());
                                remoteBean.setRemote(remoteClass.getName());
                            } catch (NoSuchMethodException e) {
                                logger.error("Class annotated as a LocalHome has no 'create()' method.  Unable to determine remote interface type.  Bean class: " + clazz.getName() + ",  Home class: " + homeClass.getName());
                            }
                        }
                    }

                    if (remoteBean instanceof SessionBean) {
                        SessionBean sessionBean = (SessionBean) remoteBean;

                        List<Class> interfaces = new ArrayList();
                        interfaces.addAll(Arrays.asList(clazz.getInterfaces()));

                        // Remove anything not eligable to be a remote or local interface
                        for (Class interfce : copy(interfaces)) {
                            String name = interfce.getName();
                            if (name.equals("java.io.Serializable") || name.equals("java.io.Externalizable") || name.startsWith("javax.ejb.")) {
                                interfaces.remove(interfce);
                            }
                        }

                        Remote remote = (Remote) clazz.getAnnotation(Remote.class);
                        if (remote != null) {
                            for (Class interfce : remote.value()) {
                                sessionBean.setBusinessRemote(interfce.getName()); // TODO: This should be turned back into an array
                            }
                        } else {
//                            for (Class interfce : copy(interfaces)) {
//                                if (interfce.isAnnotationPresent(Remote.class)) {
//                                    sessionBean.setBusinessRemote(interfce.getName()); // TODO: This should be turned back into an array
//                                    interfaces.remove(interfce);
//                                }
//                            }
                        }


                        Local local = (Local) clazz.getAnnotation(Local.class);
                        if (local != null) {
                            for (Class interfce : local.value()) {
                                sessionBean.setBusinessLocal(interfce.getName()); // TODO: This should be turned back into an array
                            }
                        } else {
//                            for (Class interfce : copy(interfaces)) {
//                                sessionBean.setBusinessLocal(interfce.getName()); // TODO: This should be turned back into an array
//                            }
                        }
                    }
                }

                buildAnnotatedRefs(clazz, bean);

            }
            return ejbModule;
        }

        private void buildAnnotatedRefs(Class clazz, JndiConsumer consumer) {
            ClassFinder finder = new ClassFinder(clazz);

            for (Field field : finder.findAnnotatedFields(EJB.class)) {
                EJB ejb = field.getAnnotation(EJB.class);

                Member member = new FieldMember(field);

                EjbRef ejbRef = buildEjbRef(member, ejb);

                consumer.getEjbRef().add(ejbRef);
            }

            for (Method method : finder.findAnnotatedMethods(EJB.class)) {
                EJB ejb = method.getAnnotation(EJB.class);

                Member member = new MethodMember(method);

                EjbRef ejbRef = buildEjbRef(member, ejb);

                consumer.getEjbRef().add(ejbRef);
            }
        }

        private EjbRef buildEjbRef(Member member, EJB ejb) {
            EjbRef ejbRef = new EjbRef();

            // Set the member name where this will be injected
            InjectionTarget target = new InjectionTarget();
            target.setInjectionTargetClass(member.getDeclaringClass().getName());
            target.setInjectionTargetName(member.getName());
            ejbRef.getInjectionTarget().add(target);

            Class interfce = ejb.beanInterface();
            if (interfce.equals(Object.class)){
                interfce = member.getType();
            }

            // Get the home and remote interfaces
            if (EJBHome.class.isAssignableFrom(interfce)){
                ejbRef.setHome(interfce.getName());
                Method[] methods = interfce.getMethods();
                for (Method method : methods) {
                    if (method.getName().startsWith("create")){
                        ejbRef.setRemote(method.getReturnType().getName());
                        break;
                    }
                }
            } else {
                // Must be a business interface ref
                ejbRef.setRemote(interfce.getName());
            }

            // Get the ejb-ref-name
            String refName = ejb.name();
            if (refName.equals("")){
                refName = member.getDeclaringClass().getName() +"/"+ member.getName();
            }
            ejbRef.setEjbRefName(refName);

            // Set the ejb-link, if any
            String ejbName = ejb.beanName();
            if (ejbName.equals("")){
                ejbName = null;
            }
            ejbRef.setEjbLink(ejbName);

            // Set the mappedName, if any
            String mappedName = ejb.mappedName();
            if (mappedName.equals("")){
                mappedName = null;
            }
            ejbRef.setMappedName(mappedName);
            return ejbRef;
        }

        private List<Class> copy(List<Class> classes) {
            return new ArrayList(classes);
        }

        private void addContainerTransaction(TransactionAttribute attribute, String ejbName, Method method, AssemblyDescriptor assemblyDescriptor) {
            ContainerTransaction ctx = new ContainerTransaction(cast(attribute.value()), ejbName, method.getName(), asStrings(method.getParameterTypes()));
            assemblyDescriptor.getContainerTransaction().add(ctx);
        }

        private String[] asStrings(Class[] types) {
            List<String> names = new ArrayList();
            for (Class clazz : types) {
                names.add(clazz.getName());
            }
            return names.toArray(new String[]{});
        }

        private TransAttribute cast(TransactionAttributeType transactionAttributeType) {
            return TransAttribute.valueOf(transactionAttributeType.toString());
        }

        private <T> T getFirst(List<T> list) {
            if (list.size() > 0) {
                return list.get(0);
            }
            return null;
        }
    }

    public static interface Member {
        Class getDeclaringClass();
        String getName();
        Class getType();
    }

    public static class MethodMember implements Member {
        private final Method setter;

        public MethodMember(Method method) {
            this.setter = method;
        }

        public Class getType() {
            return setter.getParameterTypes()[0];
        }

        public Class getDeclaringClass() {
            return setter.getDeclaringClass();
        }

        public String getName() {
            StringBuilder name = new StringBuilder(setter.getName());

            // remove 'set'
            name.delete(0, 2);

            // lowercase first char
            name.setCharAt(0, Character.toLowerCase(name.charAt(0)));

            return name.toString();
        }

        public String toString() {
            return setter.toString();
        }
    }

    public static class FieldMember implements Member {
        private final Field field;

        public FieldMember(Field field) {
            this.field = field;
        }

        public Class getType() {
            return field.getType();
        }

        public String toString() {
            return field.toString();
        }

        public Class getDeclaringClass() {
            return field.getDeclaringClass();
        }

        public String getName() {
            return field.getName();
        }
    }


}
