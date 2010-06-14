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

package org.apache.openejb.jee;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlID;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlElements;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.adapters.CollapsedStringAdapter;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Collection;


/**
 * The ejb-jarType defines the root element of the EJB
 * deployment descriptor. It contains
 * <p/>
 * - an optional description of the ejb-jar file
 * - an optional display name
 * - an optional icon that contains a small and a large
 * icon file name
 * - structural information about all included
 * enterprise beans that is not specified through
 * annotations
 * - structural information about interceptor classes
 * - a descriptor for container managed relationships,
 * if any.
 * - an optional application-assembly descriptor
 * - an optional name of an ejb-client-jar file for the
 * ejb-jar.
 */
@XmlRootElement(name = "ejb-jar")
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ejb-jarType", propOrder = {
        "moduleName",
        "descriptions",
        "displayNames",
        "icon",
        "enterpriseBeans",
        "interceptors",
        "relationships",
        "assemblyDescriptor",
        "ejbClientJar"
        })
public class EjbJar {

    @XmlElement(name = "module-name")
    protected String moduleName;
    @XmlTransient
    protected TextMap description = new TextMap();
    @XmlTransient
    protected TextMap displayName = new TextMap();
    @XmlElement(name = "icon", required = true)
    protected LocalCollection<Icon> icon = new LocalCollection<Icon>();
    @XmlTransient
    protected Map<String,EnterpriseBean> enterpriseBeans = new LinkedHashMap<String,EnterpriseBean>();

    private Interceptors interceptors;
    protected Relationships relationships;
    @XmlElement(name = "assembly-descriptor")
    protected AssemblyDescriptor assemblyDescriptor;
    @XmlElement(name = "ejb-client-jar")
    protected String ejbClientJar;
    @XmlAttribute
    @XmlJavaTypeAdapter(CollapsedStringAdapter.class)
    @XmlID
    protected String id;
    @XmlAttribute(name = "metadata-complete")
    protected Boolean metadataComplete;
    @XmlAttribute(required = true)
    @XmlJavaTypeAdapter(CollapsedStringAdapter.class)
    protected String version;

    public EjbJar() {
    }

    public EjbJar(String id) {
        this.id = id;
    }

    public String getModuleName() {
        return moduleName;
    }

    public void setModuleName(String moduleName) {
        this.moduleName = moduleName;
    }

    @XmlElement(name = "description", required = true)
    public Text[] getDescriptions() {
        return description.toArray();
    }

    public void setDescriptions(Text[] text) {
        description.set(text);
    }

    public String getDescription() {
        return description.get();
    }

    @XmlElement(name = "display-name", required = true)
    public Text[] getDisplayNames() {
        return displayName.toArray();
    }

    public void setDisplayNames(Text[] text) {
        displayName.set(text);
    }

    public String getDisplayName() {
        return displayName.get();
    }

    public Collection<Icon> getIcons() {
        if (icon == null) {
            icon = new LocalCollection<Icon>();
        }
        return icon;
    }

    public Map<String,Icon> getIconMap() {
        if (icon == null) {
            icon = new LocalCollection<Icon>();
        }
        return icon.toMap();
    }

    public Icon getIcon() {
        return icon.getLocal();
    }

    @XmlElementWrapper(name = "enterprise-beans")
    @XmlElements({
    @XmlElement(name = "message-driven", required = true, type = MessageDrivenBean.class),
    @XmlElement(name = "session", required = true, type = SessionBean.class),
    @XmlElement(name = "entity", required = true, type = EntityBean.class)})
    public EnterpriseBean[] getEnterpriseBeans() {
        return enterpriseBeans.values().toArray(new EnterpriseBean[enterpriseBeans.size()]);
    }

    public void setEnterpriseBeans(EnterpriseBean[] v) {
        enterpriseBeans.clear();
        for (EnterpriseBean e : v) enterpriseBeans.put(e.getEjbName(), e);
    }

    public <T extends EnterpriseBean> T addEnterpriseBean(T bean){
        enterpriseBeans.put(bean.getEjbName(), bean);
        return bean;
    }

    public EnterpriseBean removeEnterpriseBean(String name){
        EnterpriseBean bean = enterpriseBeans.remove(name);
        return bean;
    }

    public EnterpriseBean getEnterpriseBean(String ejbName){
        return enterpriseBeans.get(ejbName);
    }

    public Map<String,EnterpriseBean> getEnterpriseBeansByEjbName() {
        return enterpriseBeans;
    }

    public Interceptor[] getInterceptors() {
        if (interceptors == null) return new Interceptor[]{};
        return interceptors.getInterceptor();
    }

    public Interceptor addInterceptor(Interceptor interceptor) {
        if (interceptors == null) interceptors = new Interceptors();
        return interceptors.addInterceptor(interceptor);
    }

    public Interceptor getInterceptor(String className) {
        if (interceptors == null) return null;
        return interceptors.getInterceptor(className);
    }

    public Relationships getRelationships() {
        return relationships;
    }

    public void setRelationships(Relationships value) {
        this.relationships = value;
    }

    public AssemblyDescriptor getAssemblyDescriptor() {
        if (assemblyDescriptor == null){
            assemblyDescriptor = new AssemblyDescriptor();
        }
        return assemblyDescriptor;
    }

    public void setAssemblyDescriptor(AssemblyDescriptor value) {
        this.assemblyDescriptor = value;
    }

    public String getEjbClientJar() {
        return ejbClientJar;
    }

    public void setEjbClientJar(String value) {
        this.ejbClientJar = value;
    }

    public String getId() {
        return id;
    }

    public void setId(String value) {
        this.id = value;
    }

    public Boolean isMetadataComplete() {
        return metadataComplete != null && metadataComplete;
    }

    public void setMetadataComplete(Boolean value) {
        this.metadataComplete = value;
    }

    public String getVersion() {
        if (version == null) {
            return "3.0";
        } else {
            return version;
        }
    }

    public void setVersion(String value) {
        this.version = value;
    }

}
