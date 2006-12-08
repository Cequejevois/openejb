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
package org.apache.openejb.test.entity.cmr;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import javax.ejb.EJBException;

import org.apache.openejb.test.entity.cmr.CompoundPK;
import org.apache.openejb.test.entity.cmr.onetomany.ALocalHome;
import org.apache.openejb.test.entity.cmr.onetomany.ALocal;
import org.apache.openejb.test.entity.cmr.onetomany.BLocalHome;
import org.apache.openejb.test.entity.cmr.onetomany.BLocal;

/**
 *
 * @version $Revision: 451417 $ $Date: 2006-09-29 13:13:22 -0700 (Fri, 29 Sep 2006) $
 */
public class OneToManyCompoundPKTests extends AbstractCMRTest {
    private ALocalHome ahome;
    private ALocal a;
    private BLocalHome bhome;
    private BLocal b;

    public OneToManyCompoundPKTests() {
        super("OneToManyCompoundPk.");
    }

    protected void setUp() throws Exception {
        super.setUp();

        ahome = (ALocalHome) initialContext.lookup("client/tests/entity/cmr/oneToMany/compoundPk/ALocalHome");
        bhome = (BLocalHome) initialContext.lookup("client/tests/entity/cmr/oneToMany/compoundPk/BLocalHome");
    }

    public void testAGetBExistingAB() throws Exception {
        beginTransaction();
        ALocal a = ahome.findByPrimaryKey(new CompoundPK(new Integer(1), "value1"));
        Set bSet = a.getB();
        assertEquals(2, bSet.size());
        for (Iterator iter = bSet.iterator(); iter.hasNext();) {
            BLocal b = (BLocal) iter.next();
            if ( b.getField1().equals(new Integer(11)) ) {
                assertEquals("value11", b.getField2());
            } else if ( b.getField1().equals(new Integer(22)) ) {
                assertEquals("value22", b.getField2());
            } else {
                fail();
            }
        }
        completeTransaction();
    }

    public void testBGetAExistingAB() throws Exception {
        beginTransaction();
        BLocal b = bhome.findByPrimaryKey(new CompoundPK(new Integer(11), "value11"));
        ALocal a = b.getA();
        assertNotNull(a);
        assertEquals(new Integer(1), a.getField1());
        assertEquals("value1", a.getField2());

        b = bhome.findByPrimaryKey(new CompoundPK(new Integer(22), "value22"));
        a = b.getA();
        assertNotNull(a);
        assertEquals(new Integer(1), a.getField1());
        assertEquals("value1", a.getField2());
        completeTransaction();
    }

    private void assertStateDropExisting() throws Exception {
        Connection c = ds.getConnection();
        Statement s = c.createStatement();
        ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM B WHERE fka1 = 1 AND fka2 = 'value1'");
        assertTrue(rs.next());
        assertEquals(0, rs.getInt(1));
        rs.close();
        s.close();
        c.close();
    }

    /**
     * TODO Disabled due to an Axion bug. It has been tested with another
     * DB DataSource successfully.
     */
    public void XtestASetBDropExisting() throws Exception {
        beginTransaction();
        ALocal a = ahome.findByPrimaryKey(new CompoundPK(new Integer(1), "value1"));
        a.setB(new HashSet<BLocal>());
        completeTransaction();

        assertStateDropExisting();
    }

    /**
     * TODO Disabled due to an Axion bug. It has been tested with another
     * DB DataSource successfully.
     */
    public void XtestBSetADropExisting() throws Exception {
        beginTransaction();
        BLocal b = bhome.findByPrimaryKey(new Integer(11));
        b.setA(null);
        b = bhome.findByPrimaryKey(new Integer(22));
        b.setA(null);
        completeTransaction();

        assertStateDropExisting();
    }

    private void prepareNewAB() throws Exception {
        CompoundPK pkA = new CompoundPK(new Integer(2), "value2");

        beginTransaction();
        a = ahome.create(pkA);
        b = bhome.create(new CompoundPK(new Integer(22), "value22"));
    }

    private void assertStateNewAB() throws Exception {
        Connection c = ds.getConnection();
        Statement s = c.createStatement();
        ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM A WHERE a1 = 2 AND a2 = 'value2'");
        assertTrue(rs.next());
        assertEquals(1, rs.getInt(1));
        rs.close();

        rs = s.executeQuery("SELECT b1, b2 FROM B WHERE fka1 = 2 AND fka2 = 'value2'");
        assertTrue(rs.next());
        assertEquals(22, rs.getInt(1));
        assertEquals("value22", rs.getString(2));
        rs.close();
        s.close();
        c.close();
    }

    public void testASetBNewAB() throws Exception {
        prepareNewAB();
        Set<BLocal> bSet = new HashSet<BLocal>();
        bSet.add(b);
        a.setB(bSet);
        completeTransaction();

        assertStateNewAB();
    }

    public void testBSetANewAB() throws Exception {
        prepareNewAB();
        b.setA(a);
        completeTransaction();

        assertStateNewAB();
    }

    private void prepareExistingBNewA() throws Exception {
        CompoundPK pkA = new CompoundPK(new Integer(2), "value2");

        beginTransaction();
        a = ahome.create(pkA);
        b = bhome.findByPrimaryKey(new CompoundPK(new Integer(11), "value11"));
    }

    private void assertStateExistingBNewA() throws Exception {
        Connection c = ds.getConnection();
        Statement s = c.createStatement();
        ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM A WHERE a1 = 2 AND a2 = 'value2'");
        assertTrue(rs.next());
        assertEquals(1, rs.getInt(1));
        rs.close();

        rs = s.executeQuery("SELECT b1, b2 FROM B WHERE fka1 = 2 AND fka2 = 'value2'");
        assertTrue(rs.next());
        assertEquals(11, rs.getInt(1));
        assertEquals("value11", rs.getString(2));
        rs.close();
        s.close();
        c.close();
    }

    public void testASetBExistingBNewA() throws Exception {
        prepareExistingBNewA();
        Set<BLocal> bSet = a.getB();
        bSet.add(b);
        completeTransaction();

        assertStateExistingBNewA();
    }

    public void testBSetAExistingBNewA() throws Exception {
        prepareExistingBNewA();
        b.setA(a);
        completeTransaction();

        assertStateExistingBNewA();
    }

    private void prepareExistingANewB() throws Exception {
        CompoundPK pkA = new CompoundPK(new Integer(1), "value1");

        beginTransaction();
        a = ahome.findByPrimaryKey(pkA);
        b = bhome.create(new CompoundPK(new Integer(33), "value33"));
    }

    private void assertStateExistingANewB() throws Exception {
        Connection c = ds.getConnection();
        Statement s = c.createStatement();
        ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM B WHERE fka1 = 1 AND fka2 = 'value1'");
        assertTrue(rs.next());
        assertEquals(3, rs.getInt(1));
        rs.close();

        rs = s.executeQuery("SELECT COUNT(*) FROM B WHERE fka1 = 1 AND fka2 = 'value1' AND b1 = 33 AND b2 = 'value33'");
        assertTrue(rs.next());
        assertEquals(1, rs.getInt(1));
        rs.close();
        s.close();
        c.close();
    }

    public void testASetBExistingANewB() throws Exception {
        prepareExistingANewB();
        Set<BLocal> bSet = a.getB();
        bSet.add(b);
        completeTransaction();

        assertStateExistingANewB();
    }

    public void testBSetAExistingANewB() throws Exception {
        prepareExistingANewB();
        b.setA(a);
        completeTransaction();

        assertStateExistingANewB();
    }

    /**
     * TODO Disabled due to an Axion bug. It has been tested with another
     * DB DataSource successfully.
     */
    public void XtestRemoveRelationships() throws Exception {
        beginTransaction();
        ALocal a = ahome.findByPrimaryKey(new CompoundPK(new Integer(1), "value1"));
        a.remove();
        completeTransaction();

        Connection c = ds.getConnection();
        Statement s = c.createStatement();
        ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM B");
        assertTrue(rs.next());
        assertEquals(2, rs.getInt(1));
        rs.close();
        rs = s.executeQuery("SELECT COUNT(*) FROM B WHERE fka1 = 1 AND fka2 = 'value1'");
        assertTrue(rs.next());
        assertEquals(0, rs.getInt(1));
        rs.close();
        s.close();
        c.close();
    }

    public void testCMPMappedToForeignKeyColumn() throws Exception {
        beginTransaction();
        BLocal b = bhome.findByPrimaryKey(new CompoundPK(new Integer(11), "value11"));

        Integer field3 = b.getField3();
        assertEquals(((CompoundPK) b.getA().getPrimaryKey()).field1, field3);

        String field4 = b.getField4();
        assertEquals(((CompoundPK) b.getA().getPrimaryKey()).field2, field4);
        completeTransaction();
    }

    public void testSetCMPMappedToForeignKeyColumn() throws Exception {
        beginTransaction();
        BLocal b = bhome.findByPrimaryKey(new CompoundPK(new Integer(11), "value11"));

        try {
            b.setField3(new Integer(13));
            fail("Cannot set the value of a CMP field mapped to a foreign key column.");
        } catch (EJBException e) {
        }
        completeTransaction();
    }

    public void testCascadeDelete() throws Exception {
        beginTransaction();
        ALocal a = ahome.findByPrimaryKey(new CompoundPK(new Integer(1), "value1"));
        a.remove();
        completeTransaction();

        Connection c = ds.getConnection();
        Statement s = c.createStatement();
        ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM B");
        assertTrue(rs.next());
        assertEquals(0, rs.getInt(1));
        rs.close();
        s.close();
        c.close();
    }

    protected void buildDBSchema(Connection c) throws Exception {
        Statement s = c.createStatement();
        try {
            s.execute("DROP TABLE A");
        } catch (SQLException e) {
            // ignore
        }
        try {
            s.execute("DROP TABLE B");
        } catch (SQLException e) {
            // ignore
        }

        s.execute("CREATE TABLE A(A1 INTEGER, A2 VARCHAR(50))");
        s.execute("CREATE TABLE B(B1 INTEGER, B2 VARCHAR(50), FKA1 INTEGER, FKA2 VARCHAR(50))");

        s.execute("INSERT INTO A(A1, A2) VALUES(1, 'value1')");
        s.execute("INSERT INTO B(B1, B2, FKA1, FKA2) VALUES(11, 'value11', 1, 'value1')");
        s.execute("INSERT INTO B(B1, B2, FKA1, FKA2) VALUES(22, 'value22', 1, 'value1')");
        s.close();
        c.close();
    }

}
