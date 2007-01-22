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

package org.apache.openejb.jee.oej2;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlElements;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlElementWrapper;


/**
 * <p>Java class for openejb-jarType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="openejb-jarType">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element ref="{http://geronimo.apache.org/xml/ns/deployment-1.2}environment" minOccurs="0"/>
 *         &lt;element ref="{http://geronimo.apache.org/xml/ns/naming-1.2}cmp-connection-factory" minOccurs="0"/>
 *         &lt;element name="ejb-ql-compiler-factory" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         &lt;element name="db-syntax-factory" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         &lt;element name="enforce-foreign-key-constraints" type="{http://geronimo.apache.org/xml/ns/deployment-1.2}emptyType" minOccurs="0"/>
 *         &lt;element name="enterprise-beans">
 *           &lt;complexType>
 *             &lt;complexContent>
 *               &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *                 &lt;choice maxOccurs="unbounded" minOccurs="0">
 *                   &lt;element name="session" type="{http://openejb.apache.org/xml/ns/openejb-jar-2.2}session-beanType"/>
 *                   &lt;element name="entity" type="{http://openejb.apache.org/xml/ns/openejb-jar-2.2}entity-beanType"/>
 *                   &lt;element name="message-driven" type="{http://openejb.apache.org/xml/ns/openejb-jar-2.2}message-driven-beanType"/>
 *                 &lt;/choice>
 *               &lt;/restriction>
 *             &lt;/complexContent>
 *           &lt;/complexType>
 *         &lt;/element>
 *         &lt;element name="relationships" type="{http://openejb.apache.org/xml/ns/openejb-jar-2.2}relationshipsType" minOccurs="0"/>
 *         &lt;element ref="{http://geronimo.apache.org/xml/ns/naming-1.2}message-destination" maxOccurs="unbounded" minOccurs="0"/>
 *         &lt;element ref="{http://geronimo.apache.org/xml/ns/j2ee/application-1.2}security" minOccurs="0"/>
 *         &lt;element ref="{http://geronimo.apache.org/xml/ns/deployment-1.2}service" maxOccurs="unbounded" minOccurs="0"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "openejb-jarType", propOrder = {
    "environment",
    "cmpConnectionFactory",
    "ejbQlCompilerFactory",
    "dbSyntaxFactory",
    "enforceForeignKeyConstraints",
    "enterpriseBeans",
    "relationships",
    "messageDestination",
    "security",
    "service"
})
public class OpenejbJarType {

    @XmlElement(name = "environment", namespace = "http://geronimo.apache.org/xml/ns/deployment-1.2")
    protected EnvironmentType environment;

    @XmlElement(name = "cmp-connection-factory", namespace = "http://geronimo.apache.org/xml/ns/naming-1.2")
    protected ResourceLocatorType cmpConnectionFactory;

    @XmlElement(name = "ejb-ql-compiler-factory")
    protected String ejbQlCompilerFactory;

    @XmlElement(name = "db-syntax-factory")
    protected String dbSyntaxFactory;

    @XmlElement(name = "enforce-foreign-key-constraints")
    protected EmptyType enforceForeignKeyConstraints;

    @XmlElementWrapper(name = "enterprise-beans")
    @XmlElements({
    @XmlElement(name = "message-driven", required = true, type = MessageDrivenBeanType.class),
    @XmlElement(name = "session", required = true, type = SessionBeanType.class),
    @XmlElement(name = "entity", required = true, type = EntityBeanType.class)})
    protected List<EnterpriseBean> enterpriseBeans = new ArrayList<EnterpriseBean>();

    @XmlElement()
    protected RelationshipsType relationships;

    @XmlElement(name = "message-destination", namespace = "http://geronimo.apache.org/xml/ns/naming-1.2")
    protected List<MessageDestinationType> messageDestination;

    @XmlElement(name="security", namespace = "http://geronimo.apache.org/xml/ns/j2ee/application-1.2")
    protected AbstractSecurityType security;

    @XmlElementRef(name = "service", namespace = "http://geronimo.apache.org/xml/ns/deployment-1.2", type = JAXBElement.class)
    protected List<JAXBElement<? extends AbstractServiceType>> service;

    /**
     * Gets the value of the environment property.
     * 
     * @return
     *     possible object is
     *     {@link EnvironmentType }
     *     
     */
    public EnvironmentType getEnvironment() {
        return environment;
    }

    /**
     * Sets the value of the environment property.
     * 
     * @param value
     *     allowed object is
     *     {@link EnvironmentType }
     *     
     */
    public void setEnvironment(EnvironmentType value) {
        this.environment = value;
    }

    /**
     * Gets the value of the cmpConnectionFactory property.
     * 
     * @return
     *     possible object is
     *     {@link ResourceLocatorType }
     *     
     */
    public ResourceLocatorType getCmpConnectionFactory() {
        return cmpConnectionFactory;
    }

    /**
     * Sets the value of the cmpConnectionFactory property.
     * 
     * @param value
     *     allowed object is
     *     {@link ResourceLocatorType }
     *     
     */
    public void setCmpConnectionFactory(ResourceLocatorType value) {
        this.cmpConnectionFactory = value;
    }

    /**
     * Gets the value of the ejbQlCompilerFactory property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getEjbQlCompilerFactory() {
        return ejbQlCompilerFactory;
    }

    /**
     * Sets the value of the ejbQlCompilerFactory property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setEjbQlCompilerFactory(String value) {
        this.ejbQlCompilerFactory = value;
    }

    /**
     * Gets the value of the dbSyntaxFactory property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getDbSyntaxFactory() {
        return dbSyntaxFactory;
    }

    /**
     * Sets the value of the dbSyntaxFactory property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setDbSyntaxFactory(String value) {
        this.dbSyntaxFactory = value;
    }

    /**
     * Gets the value of the enforceForeignKeyConstraints property.
     * 
     * @return
     *     possible object is
     *     {@link EmptyType }
     *     
     */
    public EmptyType getEnforceForeignKeyConstraints() {
        return enforceForeignKeyConstraints;
    }

    /**
     * Sets the value of the enforceForeignKeyConstraints property.
     * 
     * @param value
     *     allowed object is
     *     {@link EmptyType }
     *     
     */
    public void setEnforceForeignKeyConstraints(EmptyType value) {
        this.enforceForeignKeyConstraints = value;
    }

    public List<EnterpriseBean> getEnterpriseBeans() {
        return enterpriseBeans;
    }

    /**
     * Gets the value of the relationships property.
     * 
     * @return
     *     possible object is
     *     {@link RelationshipsType }
     *     
     */
    public RelationshipsType getRelationships() {
        return relationships;
    }

    /**
     * Sets the value of the relationships property.
     * 
     * @param value
     *     allowed object is
     *     {@link RelationshipsType }
     *     
     */
    public void setRelationships(RelationshipsType value) {
        this.relationships = value;
    }

    /**
     * Gets the value of the messageDestination property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the messageDestination property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getMessageDestination().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link MessageDestinationType }
     * 
     * 
     */
    public List<MessageDestinationType> getMessageDestination() {
        if (messageDestination == null) {
            messageDestination = new ArrayList<MessageDestinationType>();
        }
        return this.messageDestination;
    }

    /**
     * Gets the value of the security property.
     * 
     * @return
     *     possible object is
     *     {@link AbstractSecurityType }
     *     
     */
    public AbstractSecurityType getSecurity() {
        return security;
    }

    /**
     * Sets the value of the security property.
     * 
     * @param value
     *     allowed object is
     *     {@link AbstractSecurityType }
     *     
     */
    public void setSecurity(AbstractSecurityType value) {
        this.security = value;
    }

    /**
     * Gets the value of the service property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the service property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getService().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link JAXBElement }{@code <}{@link GbeanType }{@code >}
     * {@link JAXBElement }{@code <}{@link AbstractServiceType }{@code >}
     * 
     * 
     */
    public List<JAXBElement<? extends AbstractServiceType>> getService() {
        if (service == null) {
            service = new ArrayList<JAXBElement<? extends AbstractServiceType>>();
        }
        return this.service;
    }

}
