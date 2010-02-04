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
package org.apache.openejb.client;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.lang.reflect.Method;

import java.rmi.Remote;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.rmi.PortableRemoteObject;
import javax.rmi.CORBA.Tie;
import javax.rmi.CORBA.Stub;

import org.omg.CORBA.ORB;

public class EJBRequest implements ClusterableRequest {

    private transient int requestMethod;
    private transient int deploymentCode = 0;
    private transient Object clientIdentity;
    private transient String deploymentId;
    private transient int serverHash;
    private transient Body body;

    // Only visible on the client side
    private transient final EJBMetaDataImpl ejbMetaData;

    public static final int SESSION_BEAN_STATELESS = 6;
    public static final int SESSION_BEAN_STATEFUL = 7;
    public static final int ENTITY_BM_PERSISTENCE = 8;
    public static final int ENTITY_CM_PERSISTENCE = 9;

    public EJBRequest() {
        body = new Body(null);
        ejbMetaData = null;
    }

    public EJBRequest(int requestMethod, EJBMetaDataImpl ejb, Method method, Object[] args, Object primaryKey) {
        body = new Body(ejb);

        this.ejbMetaData = ejb;
        this.requestMethod = requestMethod;
        setDeploymentCode(ejb.deploymentCode);
        setDeploymentId(ejb.deploymentID);
        setMethodInstance(method);
        setMethodParameters(args);
        setPrimaryKey(primaryKey);
    }

    public EJBMetaDataImpl getEjbMetaData() {
        return ejbMetaData;
    }

    public Class getInterfaceClass() {
        return body.getInterfaceClass();
    }

    public Method getMethodInstance() {
        return body.getMethodInstance();
    }

    public String getMethodName() {
        return body.getMethodName();
    }

    public Object[] getMethodParameters() {
        return body.getMethodParameters();
    }

    public Class[] getMethodParamTypes() {
        return body.getMethodParamTypes();
    }

    public Object getPrimaryKey() {
        return body.getPrimaryKey();
    }

    public void setMethodInstance(Method methodInstance) {
        body.setMethodInstance(methodInstance);
    }

    public void setMethodParameters(Object[] methodParameters) {
        body.setMethodParameters(methodParameters);
    }

    public void setPrimaryKey(Object primaryKey) {
        body.setPrimaryKey(primaryKey);
    }

    public Body getBody() {
        return body;
    }

    public void setBody(Body body) {
        this.body = body;
    }

    public static class Body implements java.io.Externalizable {
        private transient EJBMetaDataImpl ejb;
        private transient ORB orb; 
        private transient Method methodInstance;
        private transient Class interfaceClass;
//        private transient Class methodClass;
        private transient String methodName;
        private transient Class[] methodParamTypes;
        private transient Object[] methodParameters;
        private transient Object primaryKey;

        public Body(EJBMetaDataImpl ejb) {
            this.ejb = ejb;
        }

        public Method getMethodInstance() {
            return methodInstance;
        }

        public Object[] getMethodParameters() {
            return methodParameters;
        }

        public Object getPrimaryKey() {
            return primaryKey;
        }

        public Class getInterfaceClass() {
            return interfaceClass;
        }

        public String getMethodName() {
            return methodName;
        }

        public Class[] getMethodParamTypes() {
            return methodParamTypes;
        }

        public void setMethodInstance(Method methodInstance) {
            if (methodInstance == null) {
                throw new NullPointerException("methodInstance input parameter is null");
            }
            this.methodInstance = methodInstance;
            this.methodName = methodInstance.getName();
            this.methodParamTypes = methodInstance.getParameterTypes();
            Class methodClass = methodInstance.getDeclaringClass();

            if (ejb.homeClass != null) {
                if (methodClass.isAssignableFrom(ejb.homeClass)){
                    this.interfaceClass = ejb.homeClass;
                    return;
                }
            }

            if (ejb.remoteClass != null) {
                if (methodClass.isAssignableFrom(ejb.remoteClass)){
                    this.interfaceClass = ejb.remoteClass;
                    return;
                }
            }

            for (Class businessClass : ejb.businessClasses) {
                if (methodClass.isAssignableFrom(businessClass)){
                    this.interfaceClass = businessClass;
                    return;
                }
            }
        }

        public void setMethodParameters(Object[] methodParameters) {
            this.methodParameters = methodParameters;
        }

        public void setPrimaryKey(Object primaryKey) {
            this.primaryKey = primaryKey;
        }

        public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
            byte version = in.readByte(); // future use

            ClassNotFoundException result = null;
            primaryKey = null;
//            methodClass = null;
            methodName = null;
            methodInstance = null;
            try {
                primaryKey = in.readObject();
                interfaceClass = (Class) in.readObject();
//                methodClass = (Class) in.readObject();
            } catch (ClassNotFoundException cnfe) {
                if (result == null) result = cnfe;
            }
            methodName = in.readUTF();

            try {
                readMethodParameters(in);
            } catch (ClassNotFoundException cnfe) {
                if (result == null) result = cnfe;
            }

            if (interfaceClass != null) {
                try {
                    methodInstance = interfaceClass.getMethod(methodName, methodParamTypes);
                } catch (NoSuchMethodException nsme) {
                    //if (result == null) result = nsme;
                }
            }
            if (result != null)
                throw result;
        }

        public void writeExternal(ObjectOutput out) throws IOException {
            // write out the version of the serialized data for future use
            out.writeByte(1);

            out.writeObject(primaryKey);

            out.writeObject(interfaceClass);
//            out.writeObject(methodClass);
            out.writeUTF(methodName);

            writeMethodParameters(out, methodParamTypes, methodParameters);
        }

        protected void writeMethodParameters(ObjectOutput out, Class[] types, Object[] args) throws IOException {

            out.writeByte(types.length);

            for (int i = 0; i < types.length; i++) {
                Class type = types[i];
                Object obj = args[i];

                if (type.isPrimitive()) {
                    if (type == Byte.TYPE) {
                        out.write(B);
                        byte bytevalue = ((Byte) obj).byteValue();
                        out.writeByte(bytevalue);

                    } else if (type == Character.TYPE) {
                        out.write(C);
                        char charvalue = ((Character) obj).charValue();
                        out.writeChar(charvalue);

                    } else if (type == Integer.TYPE) {
                        out.write(I);
                        int intvalue = ((Integer) obj).intValue();
                        out.writeInt(intvalue);

                    } else if (type == Boolean.TYPE) {
                        out.write(Z);
                        boolean booleanvalue = ((Boolean) obj).booleanValue();
                        out.writeBoolean(booleanvalue);

                    } else if (type == Long.TYPE) {
                        out.write(J);
                        long longvalue = ((Long) obj).longValue();
                        out.writeLong(longvalue);

                    } else if (type == Float.TYPE) {
                        out.write(F);
                        float fvalue = ((Float) obj).floatValue();
                        out.writeFloat(fvalue);

                    } else if (type == Double.TYPE) {
                        out.write(D);
                        double dvalue = ((Double) obj).doubleValue();
                        out.writeDouble(dvalue);

                    } else if (type == Short.TYPE) {
                        out.write(S);
                        short shortvalue = ((Short) obj).shortValue();
                        out.writeShort(shortvalue);

                    } else {
                        throw new IOException("Unkown primitive type: " + type);
                    }
                } else {
                    if (obj instanceof PortableRemoteObject && obj instanceof Remote) {
                        Tie tie = javax.rmi.CORBA.Util.getTie((Remote) obj);
                        if (tie == null) {
                            throw new IOException("Unable to serialize PortableRemoteObject; object has not been exported: " + obj);
                        }
                        ORB orb = getORB();
                        tie.orb(orb);
                        obj = PortableRemoteObject.toStub((Remote) obj);
                    }
                    out.write(L);
                    out.writeObject(type);
                    out.writeObject(obj);
                }
            }
        }

        static final Class[] noArgsC = new Class[0];
        static final Object[] noArgsO = new Object[0];

        /**
         * Obtain an ORB instance for this request to activate remote 
         * arguments and return results.
         * 
         * @return An ORB instance.
         */
        protected ORB getORB() throws IOException {
            // first ORB request?  Check our various sources 
            if (orb == null) {
                try {
                    Context initialContext = new InitialContext();
                    orb = (ORB) initialContext.lookup("java:comp/ORB");
                } catch (Throwable e) {
                    try {
                        // any orb will do if we can't get a context one. 
                        orb = ORB.init(); 
                    } catch (Throwable ex) {
                        throw new IOException("Unable to connect PortableRemoteObject stub to an ORB, no ORB bound to java:comp/ORB");
                    }
                }
            }
            return orb; 
        }

        protected void readMethodParameters(ObjectInput in) throws IOException, ClassNotFoundException {
            int length = in.read();

            if (length < 1) {
                methodParamTypes = noArgsC;
                methodParameters = noArgsO;
                return;
            }

            Class[] types = new Class[length];
            Object[] args = new Object[length];

            for (int i = 0; i < types.length; i++) {
                Class clazz = null;
                Object obj = null;

                int type = in.read();

                switch (type) {
                    case B:
                        clazz = Byte.TYPE;
                        obj = new Byte(in.readByte());
                        break;

                    case C:
                        clazz = Character.TYPE;
                        obj = new Character(in.readChar());
                        break;

                    case I:
                        clazz = Integer.TYPE;
                        obj = new Integer(in.readInt());
                        break;

                    case Z:
                        clazz = Boolean.TYPE;
                        obj = new Boolean(in.readBoolean());
                        break;

                    case J:
                        clazz = Long.TYPE;
                        obj = new Long(in.readLong());
                        break;

                    case F:
                        clazz = Float.TYPE;
                        obj = new Float(in.readFloat());
                        break;

                    case D:
                        clazz = Double.TYPE;
                        obj = new Double(in.readDouble());
                        break;

                    case S:
                        clazz = Short.TYPE;
                        obj = new Short(in.readShort());
                        break;

                    case L:
                        clazz = (Class) in.readObject();
                        obj = in.readObject();
                        if (obj instanceof Stub) {
                            Stub stub = (Stub)obj;
                            ORB orb = getORB();
                            stub.connect(orb);
                        }
                        break;
                    default:
                        throw new IOException("Unkown data type: " + type);
                }

                types[i] = clazz;
                args[i] = obj;
            }

            methodParamTypes = types;
            methodParameters = args;
        }

        private static final int I = 0;
        private static final int B = 1;
        private static final int J = 2;
        private static final int F = 3;
        private static final int D = 4;
        private static final int S = 5;
        private static final int C = 6;
        private static final int Z = 7;
        private static final int L = 8;
        private static final int A = 9;
    }

    public byte getRequestType() {
        return RequestMethodConstants.EJB_REQUEST;
    }

    public int getRequestMethod() {
        return requestMethod;
    }

    public Object getClientIdentity() {
        return clientIdentity;
    }

    public String getDeploymentId() {
        return deploymentId;
    }

    public int getDeploymentCode() {
        return deploymentCode;
    }

    public void setRequestMethod(int requestMethod) {
        this.requestMethod = requestMethod;
    }

    public void setClientIdentity(Object clientIdentity) {
        this.clientIdentity = clientIdentity;
    }

    public void setDeploymentId(String deploymentId) {
        this.deploymentId = deploymentId;
    }

    public void setDeploymentCode(int deploymentCode) {
        this.deploymentCode = deploymentCode;
    }

    public void setServerHash(int serverHash) {
        this.serverHash = serverHash;
    }

    public int getServerHash() {
        return serverHash;
    }

    public String toString() {
        StringBuffer s = null;
        switch (requestMethod) {
            case RequestMethodConstants.EJB_HOME_GET_EJB_META_DATA:
                s = new StringBuffer("EJB_HOME.GET_EJB_META_DATA");
                break;
            case RequestMethodConstants.EJB_HOME_GET_HOME_HANDLE:
                s = new StringBuffer("EJB_HOME.GET_HOME_HANDLE");
                break;
            case RequestMethodConstants.EJB_HOME_REMOVE_BY_HANDLE:
                s = new StringBuffer("EJB_HOME.REMOVE_BY_HANDLE");
                break;
            case RequestMethodConstants.EJB_HOME_REMOVE_BY_PKEY:
                s = new StringBuffer("EJB_HOME.REMOVE_BY_PKEY");
                break;
            case RequestMethodConstants.EJB_HOME_FIND:
                s = new StringBuffer("EJB_HOME.FIND");
                break;
            case RequestMethodConstants.EJB_HOME_CREATE:
                s = new StringBuffer("EJB_HOME.CREATE");
                break;
            case RequestMethodConstants.EJB_HOME_METHOD:
                s = new StringBuffer("EJB_HOME.HOME_METHOD");
                break;
            case RequestMethodConstants.EJB_OBJECT_GET_EJB_HOME:
                s = new StringBuffer("EJB_OBJECT.GET_EJB_HOME");
                break;
            case RequestMethodConstants.EJB_OBJECT_GET_HANDLE:
                s = new StringBuffer("EJB_OBJECT.GET_HANDLE");
                break;
            case RequestMethodConstants.EJB_OBJECT_GET_PRIMARY_KEY:
                s = new StringBuffer("EJB_OBJECT.GET_PRIMARY_KEY");
                break;
            case RequestMethodConstants.EJB_OBJECT_IS_IDENTICAL:
                s = new StringBuffer("EJB_OBJECT.IS_IDENTICAL");
                break;
            case RequestMethodConstants.EJB_OBJECT_REMOVE:
                s = new StringBuffer("EJB_OBJECT.REMOVE");
                break;
            case RequestMethodConstants.EJB_OBJECT_BUSINESS_METHOD:
                s = new StringBuffer("EJB_OBJECT.BUSINESS_METHOD");
                break;
            default:
                s = new StringBuffer("EJB_UKNOWN."+requestMethod);
        }
        s.append(':').append(deploymentId);
        if (body != null) {
            s.append(':').append(body.getMethodName());
            s.append(':').append(body.getPrimaryKey());
        }
        return s.toString();
    }

    /*
    When the Request externalizes itself, it will reset
    the appropriate values so that this instance can be used
    again.

    There will be one request instance for each handler
    */

    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        ClassNotFoundException result = null;

        requestMethod = -1;
        deploymentId = null;
        deploymentCode = -1;
        clientIdentity = null;

        requestMethod = in.readByte();
        try {
            deploymentId = (String) in.readObject();
        } catch (ClassNotFoundException cnfe) {
            result = cnfe;
        }
        deploymentCode = in.readShort();
        try {
            clientIdentity = in.readObject();
        } catch (ClassNotFoundException cnfe) {
            if (result == null) result = cnfe;
        }
        serverHash = in.readInt();
        if (result != null)
            throw result;
    }

    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeByte(requestMethod);

        if (deploymentCode > 0) {
            out.writeObject(null);
        } else {
            out.writeObject(deploymentId);
        }

        out.writeShort(deploymentCode);
        out.writeObject(clientIdentity);
        out.writeInt(serverHash);
        body.writeExternal(out);
    }

}

