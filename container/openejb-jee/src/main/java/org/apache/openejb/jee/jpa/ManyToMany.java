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

package org.apache.openejb.jee.jpa;

import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlTransient;


/**
 *
 *
 *         @Target({METHOD, FIELD}) @Retention(RUNTIME)
 *         public @interface ManyToMany {
 *           Class targetEntity() default void.class;
 *           CascadeType[] cascade() default {};
 *           FetchType fetch() default LAZY;
 *           String mappedBy() default "";
 *         }
 *
 *
 *
 * <p>Java class for many-to-many complex type.
 *
 * <p>The following schema fragment specifies the expected content contained within this class.
 *
 * <pre>
 * &lt;complexType name="many-to-many">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="order-by" type="{http://java.sun.com/xml/ns/persistence/orm}order-by" minOccurs="0"/>
 *         &lt;element name="map-key" type="{http://java.sun.com/xml/ns/persistence/orm}map-key" minOccurs="0"/>
 *         &lt;element name="join-table" type="{http://java.sun.com/xml/ns/persistence/orm}join-table" minOccurs="0"/>
 *         &lt;element name="cascade" type="{http://java.sun.com/xml/ns/persistence/orm}cascade-type" minOccurs="0"/>
 *       &lt;/sequence>
 *       &lt;attribute name="fetch" type="{http://java.sun.com/xml/ns/persistence/orm}fetch-type" />
 *       &lt;attribute name="mapped-by" type="{http://www.w3.org/2001/XMLSchema}string" />
 *       &lt;attribute name="name" use="required" type="{http://www.w3.org/2001/XMLSchema}string" />
 *       &lt;attribute name="target-entity" type="{http://www.w3.org/2001/XMLSchema}string" />
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 *
 *
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "many-to-many", propOrder = {
    "orderBy",
    "mapKey",
    "joinTable",
    "cascade"
})
public class ManyToMany implements RelationField {

    @XmlElement(name = "order-by")
    protected String orderBy;
    @XmlElement(name = "map-key")
    protected MapKey mapKey;
    @XmlElement(name = "join-table")
    protected JoinTable joinTable;
    protected CascadeType cascade;
    @XmlAttribute
    protected FetchType fetch;
    @XmlAttribute(name = "mapped-by")
    protected String mappedBy;
    @XmlAttribute(required = true)
    protected String name;
    @XmlAttribute(name = "target-entity")
    protected String targetEntity;
    @XmlTransient
    protected RelationField relatedField;
    @XmlTransient
    protected boolean syntheticField;

    /**
     * Gets the value of the orderBy property.
     *
     * @return
     *     possible object is
     *     {@link String }
     *
     */
    public String getOrderBy() {
        return orderBy;
    }

    /**
     * Sets the value of the orderBy property.
     *
     * @param value
     *     allowed object is
     *     {@link String }
     *
     */
    public void setOrderBy(String value) {
        this.orderBy = value;
    }

    /**
     * Gets the value of the mapKey property.
     *
     * @return
     *     possible object is
     *     {@link MapKey }
     *
     */
    public MapKey getMapKey() {
        return mapKey;
    }

    /**
     * Sets the value of the mapKey property.
     *
     * @param value
     *     allowed object is
     *     {@link MapKey }
     *
     */
    public void setMapKey(MapKey value) {
        this.mapKey = value;
    }

    /**
     * Gets the value of the joinTable property.
     *
     * @return
     *     possible object is
     *     {@link JoinTable }
     *
     */
    public JoinTable getJoinTable() {
        return joinTable;
    }

    /**
     * Sets the value of the joinTable property.
     *
     * @param value
     *     allowed object is
     *     {@link JoinTable }
     *
     */
    public void setJoinTable(JoinTable value) {
        this.joinTable = value;
    }

    /**
     * Gets the value of the cascade property.
     *
     * @return
     *     possible object is
     *     {@link CascadeType }
     *
     */
    public CascadeType getCascade() {
        return cascade;
    }

    /**
     * Sets the value of the cascade property.
     *
     * @param value
     *     allowed object is
     *     {@link CascadeType }
     *
     */
    public void setCascade(CascadeType value) {
        this.cascade = value;
    }

    /**
     * Gets the value of the fetch property.
     *
     * @return
     *     possible object is
     *     {@link FetchType }
     *
     */
    public FetchType getFetch() {
        return fetch;
    }

    /**
     * Sets the value of the fetch property.
     *
     * @param value
     *     allowed object is
     *     {@link FetchType }
     *
     */
    public void setFetch(FetchType value) {
        this.fetch = value;
    }

    /**
     * Gets the value of the mappedBy property.
     *
     * @return
     *     possible object is
     *     {@link String }
     *
     */
    public String getMappedBy() {
        return mappedBy;
    }

    /**
     * Sets the value of the mappedBy property.
     *
     * @param value
     *     allowed object is
     *     {@link String }
     *
     */
    public void setMappedBy(String value) {
        this.mappedBy = value;
    }

    /**
     * Gets the value of the name property.
     *
     * @return
     *     possible object is
     *     {@link String }
     *
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the value of the name property.
     *
     * @param value
     *     allowed object is
     *     {@link String }
     *
     */
    public void setName(String value) {
        this.name = value;
    }

    /**
     * Gets the value of the targetEntity property.
     *
     * @return
     *     possible object is
     *     {@link String }
     *
     */
    public String getTargetEntity() {
        return targetEntity;
    }

    /**
     * Sets the value of the targetEntity property.
     *
     * @param value
     *     allowed object is
     *     {@link String }
     *
     */
    public void setTargetEntity(String value) {
        this.targetEntity = value;
    }

    public List<JoinColumn> getJoinColumn() {
        throw new UnsupportedOperationException("Many to many element can not have join columns");
    }
    
    /**
     * This is only used for xml converters and will normally return null.
     * Gets the field on the target entity for this relationship.
     * @return the field on the target entity for this relationship.
     */
    public RelationField getRelatedField() {
        return relatedField;
    }

    /**
     * Gets the field on the target entity for this relationship.
     * @param value field on the target entity for this relationship.
     */
    public void setRelatedField(RelationField value) {
        this.relatedField = value;
    }

    /**
     * This is only used for xml converters and will normally return false.
     * A true value indicates that this field was generated for CMR back references.
     * @return true if this field was generated for CMR back references.
     */
    public boolean isSyntheticField() {
        return syntheticField;
    }

    /**
     * This is only used for xml converters and will normally return false.
     * A true value indicates that this field was generated for CMR back references.
     * @return true if this field was generated for CMR back references.
     */
    public void setSyntheticField(boolean syntheticField) {
        this.syntheticField = syntheticField;
    }

    public Object getKey() {
        return name;
    }
}
