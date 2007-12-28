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
package org.apache.openejb.tomcat;

import java.util.Properties;

/**
 * @version $Rev$ $Date$
 */
public enum TomcatVersion {

    v3, v40, v41, v50, v55, v6, UNKNOWN;

    private String serverNumber;
    private String serverBuilt;

    private static TomcatVersion version;

    public String getServerNumber() {
        return serverNumber;
    }

    public String getServerBuilt() {
        return serverBuilt;
    }

    public boolean isTheVersion() {
        return get() == this;
    }

    public static boolean hasAnnotationProcessingSupport(){
        switch (get()) {
            case v40:
            case v41:
            case v50:
            case v55:
                return false;
            default:
                return true;
        }
    }
    
    public static TomcatVersion get(){
        if (version != null) return version;

        try {
            Properties properties = new Properties();
            ClassLoader classLoader = TomcatVersion.class.getClassLoader();
            properties.load(classLoader.getResourceAsStream("org/apache/catalina/util/ServerInfo.properties"));

            String serverNumber = properties.getProperty("server.number");

            if (serverNumber.startsWith("3")) version = v3;
            else if (serverNumber.startsWith("4.0")) version = v40;
            else if (serverNumber.startsWith("4.1")) version = v41;
            else if (serverNumber.startsWith("5.0")) version = v50;
            else if (serverNumber.startsWith("5.5")) version = v55;
            else if (serverNumber.startsWith("6.")) version = v6;
            else version = UNKNOWN;

            version.serverNumber = serverNumber;
            version.serverBuilt = properties.getProperty("server.built");

            return version;
        } catch (Exception e) {
            return UNKNOWN;
        }
    }
}
