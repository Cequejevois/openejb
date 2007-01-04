/**
 *
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
package org.apache.openejb.core.cmp.jpa;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.Set;
import java.util.HashSet;
import javax.ejb.CreateException;
import javax.ejb.EJBObject;
import javax.ejb.EntityBean;
import javax.ejb.FinderException;
import javax.ejb.RemoveException;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.PersistenceException;
import javax.persistence.Query;
import javax.transaction.Transaction;
import javax.transaction.TransactionManager;

import org.apache.openejb.OpenEJBException;
import org.apache.openejb.core.cmp.KeyGenerator;
import org.apache.openejb.core.CoreDeploymentInfo;
import org.apache.openejb.core.ThreadContext;
import org.apache.openejb.core.cmp.CmpCallback;
import org.apache.openejb.core.cmp.CmpEngine;
import org.apache.openejb.core.cmp.SimpleKeyGenerator;
import org.apache.openejb.core.cmp.ComplexKeyGenerator;
import org.apache.openejb.core.cmp.cmp2.Cmp2KeyGenerator;
import org.apache.openejb.util.Logger;
import org.apache.openjpa.event.AbstractLifecycleListener;
import org.apache.openjpa.event.LifecycleEvent;
import org.apache.openjpa.persistence.OpenJPAEntityManager;

public class JpaCmpEngine implements CmpEngine {
    private static final Logger logger = Logger.getInstance("OpenEJB", "org.apache.openejb.core.cmp");
    private static final Object[] NO_ARGS = new Object[0];

    private final CmpCallback cmpCallback;
    private final TransactionManager transactionManager;

    private final Map<Transaction, EntityManager> transactionData = new WeakHashMap<Transaction, EntityManager>();

    private final ThreadLocal<Set<EntityBean>> creating = new ThreadLocal<Set<EntityBean>>() {
        protected Set<EntityBean> initialValue() {
            return new HashSet<EntityBean>();
        }
    };

    public JpaCmpEngine(CmpCallback cmpCallback, TransactionManager transactionManager, String connectorName, ClassLoader classLoader) {
        this.cmpCallback = cmpCallback;
        this.transactionManager = transactionManager;
    }

    public void deploy(CoreDeploymentInfo deploymentInfo) throws OpenEJBException {
        Class beanClass = deploymentInfo.getBeanClass();
        if (deploymentInfo.isCmp2()) {
            String beanClassName = beanClass.getName();
            String cmpBeanImplName = beanClassName + "_JPA";
            ClassLoader classLoader = deploymentInfo.getClassLoader();
            try {
                Class<?> cmpBeanImpl = classLoader.loadClass(cmpBeanImplName);
                deploymentInfo.setCmpBeanImpl(cmpBeanImpl);
            } catch (ClassNotFoundException e) {
                throw new OpenEJBException("CMP 2.x implementation class not found " + cmpBeanImplName);
            }
        } else {
            deploymentInfo.setCmpBeanImpl(beanClass);
        }
        configureKeyGenerator(deploymentInfo);
    }

    private EntityManager getEntityManager(CoreDeploymentInfo deploymentInfo) {
        try {
            Transaction transaction = transactionManager.getTransaction();
            EntityManager entityManager = transactionData.get(transaction);
            if (entityManager == null) {
                EntityManagerFactory entityManagerFactory = (EntityManagerFactory) deploymentInfo.getJndiEnc().lookup("env/openejb/cmp");

                // todo close entityManager when tx completes
                entityManager = entityManagerFactory.createEntityManager();
                if (entityManager instanceof OpenJPAEntityManager) {
                    OpenJPAEntityManager openjpaEM = (OpenJPAEntityManager) entityManager;
                    openjpaEM.addLifecycleListener(new OpenJPALifecycleListener(), (Class[])null);
                }
                transactionData.put(transaction, entityManager);
            }
            return entityManager;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public Object createBean(EntityBean bean, ThreadContext callContext) throws CreateException {
        CoreDeploymentInfo deploymentInfo = callContext.getDeploymentInfo();
        EntityManager entityManager = getEntityManager(deploymentInfo);

        // TODO verify that extract primary key requires a flush followed by a merge
        creating.get().add(bean);
        try {
            entityManager.persist(bean);
            entityManager.flush();
            bean = entityManager.merge(bean);
        } finally {
            creating.get().remove(bean);
        }

        // extract the primary key from the bean
        KeyGenerator kg = deploymentInfo.getKeyGenerator();
        Object primaryKey = kg.getPrimaryKey(bean);

        return primaryKey;
    }

    public Object loadBean(ThreadContext callContext, Object primaryKey) {
        CoreDeploymentInfo deploymentInfo = callContext.getDeploymentInfo();
        Class<?> beanClass = deploymentInfo.getCmpBeanImpl();
        EntityManager entityManager = getEntityManager(deploymentInfo);
        Object bean = entityManager.find(beanClass, primaryKey);
        return bean;
    }

    public void removeBean(ThreadContext callContext) {
        CoreDeploymentInfo deploymentInfo = callContext.getDeploymentInfo();
        Class<?> beanClass = deploymentInfo.getCmpBeanImpl();

        EntityManager entityManager = getEntityManager(deploymentInfo);
        Object bean = entityManager.find(beanClass, callContext.getPrimaryKey());
        entityManager.remove(bean);
    }

    public List<Object> queryBeans(ThreadContext callContext, String queryString, Object[] args) throws FinderException {
        logger.error("Executing query " + queryString);
        CoreDeploymentInfo deploymentInfo = callContext.getDeploymentInfo();
        EntityManager entityManager = getEntityManager(deploymentInfo);
        Query query = entityManager.createQuery(queryString);
        // process args
        if (args == null) {
            args = NO_ARGS;
        }
        for (int i = 0; i < args.length; i++) {
            Object arg = args[i];
                if (arg instanceof EJBObject) {
                    // todo replace EjbObject arg with actual bean instance from EjbObject
//                    try {
//                        Object pk = ((EJBObject) arg).getPrimaryKey();
//                        Object bean = entityManager.find(beanClass, pk);
//                        arg = bean;
//                    } catch (RemoteException re) {
//                        throw new FinderException("Could not extract primary key from EJBObject reference; argument number " + i);
//                    }
                }
            query.setParameter(i + 1, arg);
        }

        // todo results should not be iterated over, but should insted
        // perform all work in a wrapper list on demand by the application code
        List results = query.getResultList();
        for (Object value : results) {
            if (value instanceof EntityBean) {
                // todo don't activate beans already activated
                EntityBean entity = (EntityBean) value;
                cmpCallback.ejbActivate(entity);
            }
        }
        return results;
    }

    private void configureKeyGenerator(CoreDeploymentInfo di) throws OpenEJBException {
        if (di.isCmp2()) {
            di.setKeyGenerator(new Cmp2KeyGenerator());
        } else {
            String primaryKeyField = di.getPrimaryKeyField();
            Class cmpBeanImpl = di.getCmpBeanImpl();
            if (primaryKeyField != null) {
                di.setKeyGenerator(new SimpleKeyGenerator(cmpBeanImpl, primaryKeyField));
            } else {
                di.setKeyGenerator(new ComplexKeyGenerator(cmpBeanImpl, di.getPrimaryKeyClass()));
            }
        }
    }

    private class OpenJPALifecycleListener extends AbstractLifecycleListener {
//        protected void eventOccurred(LifecycleEvent event) {
//            int type = event.getType();
//            switch (type) {
//                case LifecycleEvent.BEFORE_PERSIST:
//                    System.out.println("BEFORE_PERSIST");
//                    break;
//                case LifecycleEvent.AFTER_PERSIST:
//                    System.out.println("AFTER_PERSIST");
//                    break;
//                case LifecycleEvent.AFTER_LOAD:
//                    System.out.println("AFTER_LOAD");
//                    break;
//                case LifecycleEvent.BEFORE_STORE:
//                    System.out.println("BEFORE_STORE");
//                    break;
//                case LifecycleEvent.AFTER_STORE:
//                    System.out.println("AFTER_STORE");
//                    break;
//                case LifecycleEvent.BEFORE_CLEAR:
//                    System.out.println("BEFORE_CLEAR");
//                    break;
//                case LifecycleEvent.AFTER_CLEAR:
//                    System.out.println("AFTER_CLEAR");
//                    break;
//                case LifecycleEvent.BEFORE_DELETE:
//                    System.out.println("BEFORE_DELETE");
//                    break;
//                case LifecycleEvent.AFTER_DELETE:
//                    System.out.println("AFTER_DELETE");
//                    break;
//                case LifecycleEvent.BEFORE_DIRTY:
//                    System.out.println("BEFORE_DIRTY");
//                    break;
//                case LifecycleEvent.AFTER_DIRTY:
//                    System.out.println("AFTER_DIRTY");
//                    break;
//                case LifecycleEvent.BEFORE_DIRTY_FLUSHED:
//                    System.out.println("BEFORE_DIRTY_FLUSHED");
//                    break;
//                case LifecycleEvent.AFTER_DIRTY_FLUSHED:
//                    System.out.println("AFTER_DIRTY_FLUSHED");
//                    break;
//                case LifecycleEvent.BEFORE_DETACH:
//                    System.out.println("BEFORE_DETACH");
//                    break;
//                case LifecycleEvent.AFTER_DETACH:
//                    System.out.println("AFTER_DETACH");
//                    break;
//                case LifecycleEvent.BEFORE_ATTACH:
//                    System.out.println("BEFORE_ATTACH");
//                    break;
//                case LifecycleEvent.AFTER_ATTACH:
//                    System.out.println("AFTER_ATTACH");
//                    break;
//                case LifecycleEvent.AFTER_REFRESH:
//                    System.out.println("AFTER_REFRESH");
//                    break;
//                default:
//                    System.out.println("default");
//                    break;
//            }
//            super.eventOccurred(event);
//        }

        public void afterLoad(LifecycleEvent lifecycleEvent) {
            eventOccurred(lifecycleEvent);
            Object bean = lifecycleEvent.getSource();
            // This may seem a bit strange to call ejbActivate immedately followed by ejbLoad,
            // but it is completely legal.  Since the ejbActivate method is not allowed to access
            // persistent state of the bean (EJB 3.0fr 8.5.2) there should be no concern that the
            // call back method clears the bean state before ejbLoad is called.
            cmpCallback.setEntityContext((EntityBean) bean);
            cmpCallback.ejbActivate((EntityBean) bean);
            cmpCallback.ejbLoad((EntityBean) bean);
        }

        public void beforeStore(LifecycleEvent lifecycleEvent) {
            eventOccurred(lifecycleEvent);
            EntityBean bean = (EntityBean) lifecycleEvent.getSource();
            if (!creating.get().contains(bean)) {
                cmpCallback.ejbStore(bean);
            }
        }

        public void afterAttach(LifecycleEvent lifecycleEvent) {
            eventOccurred(lifecycleEvent);
            Object bean = lifecycleEvent.getSource();
            cmpCallback.setEntityContext((EntityBean) bean);
        }

        public void beforeDelete(LifecycleEvent lifecycleEvent) {
            eventOccurred(lifecycleEvent);
            try {
                Object bean = lifecycleEvent.getSource();
                cmpCallback.ejbRemove((EntityBean) bean);
            } catch (RemoveException e) {
                throw new PersistenceException(e);
            }
        }

        public void afterDetach(LifecycleEvent lifecycleEvent) {
            eventOccurred(lifecycleEvent);
            // todo detach is called after ejbRemove which does not need ejbPassivate
            Object bean = lifecycleEvent.getSource();
            cmpCallback.ejbPassivate((EntityBean) bean);
            cmpCallback.unsetEntityContext((EntityBean) bean);
        }

        public void beforePersist(LifecycleEvent lifecycleEvent) {
            eventOccurred(lifecycleEvent);
        }

        public void afterRefresh(LifecycleEvent lifecycleEvent) {
            eventOccurred(lifecycleEvent);
        }

        public void beforeDetach(LifecycleEvent lifecycleEvent) {
            eventOccurred(lifecycleEvent);
        }

        public void beforeAttach(LifecycleEvent lifecycleEvent) {
            eventOccurred(lifecycleEvent);
        }
    }
}
