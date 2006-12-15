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

import org.apache.openejb.core.CoreDeploymentInfo;
import org.apache.openejb.core.cmp.CmpUtil;
import org.apache.openejb.test.entity.MultiValuedCmr;

import javax.ejb.EJBLocalObject;
import javax.ejb.EntityBean;
import java.util.Set;

public class MultiValuedCmrImpl<Bean extends EntityBean, Proxy extends EJBLocalObject> implements MultiValuedCmr<Bean, Proxy> {
    private final EntityBean source;
    private final Class<? extends EntityBean> sourceType;
    private final String sourceProperty;
    private final Class<Bean> relatedType;
    private final String relatedProperty;
    private final CoreDeploymentInfo relatedInfo;
    private final CmpWrapperFactory sourceWrapperFactory;
    private final CmpWrapperFactory relatedWrapperFactory;
    private final CollectionRef<Bean> collectionRef = new CollectionRef<Bean>();

    public MultiValuedCmrImpl(EntityBean source, String sourceProperty, Class<Bean> relatedType, String relatedProperty) {
        if (source == null) throw new NullPointerException("source is null");
        if (sourceProperty == null) throw new NullPointerException("sourceProperty is null");
        if (relatedType == null) throw new NullPointerException("relatedType is null");
        if (relatedProperty == null) throw new NullPointerException("relatedProperty is null");
        this.source = source;
        this.sourceProperty = sourceProperty;
        this.relatedType = relatedType;
        this.relatedProperty = relatedProperty;
        this.sourceType = source.getClass();

        this.relatedInfo = CmpUtil.getDeploymentInfo(relatedType);

        sourceWrapperFactory = new CmpWrapperFactory(sourceType);
        relatedWrapperFactory = new CmpWrapperFactory(relatedType);
    }

    public Set<Proxy> get(Set<Bean> others) {
        if (others == null) throw new NullPointerException("others is null");
        collectionRef.set(others);
        Set<Proxy> cmrSet = new CmrSet<Bean, Proxy>(source, sourceProperty, sourceWrapperFactory, relatedInfo, relatedType, relatedProperty, relatedWrapperFactory, collectionRef);
        return cmrSet;
    }

    public void set(Set<Bean> relatedBeans, Set<Proxy> newProxies) {
        // clear back reference in the old related beans
        for (Bean oldBean : relatedBeans) {
            if (oldBean != null) {
                getCmpWrapper(oldBean).removeCmr(relatedProperty, source);
            }
        }
        relatedBeans.clear();

        for (Proxy newProxy : newProxies) {
            Bean newBean = (Bean) CmpUtil.getEntityBean(newProxy);

            if (newProxy != null) {
                // set the back reference in the new related bean
                Object oldBackRef = getCmpWrapper(newBean).addCmr(relatedProperty, source);

                // add the bean to our value map
                relatedBeans.add(newBean);

                // if the new related beas was related to another bean, we need
                // to clear the back reference in that old bean
                if (oldBackRef != null) {
                    getCmpWrapper(oldBackRef).removeCmr(sourceProperty, newBean);
                }
            }
        }
    }

    public void deleted(Set<Bean> relatedBeans) {
        collectionRef.set(null);

        // clear back reference in the old related beans
        for (Bean oldBean : relatedBeans) {
            if (oldBean != null) {
                getCmpWrapper(oldBean).removeCmr(relatedProperty, source);
            }
        }
    }

    private CmpWrapper getCmpWrapper(Object object) {
        if (object == null) return null;
        if (sourceType.isInstance(object)) {
            return sourceWrapperFactory.createCmpEntityBean(object);
        } else if (relatedType.isInstance(object)) {
            return relatedWrapperFactory.createCmpEntityBean(object);
        }
        throw new IllegalArgumentException("Unknown cmp bean type " + object.getClass().getName());
    }

}
