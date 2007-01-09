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
package org.apache.openejb.alt.config;

import org.apache.openejb.jee.EjbJar;
import org.apache.openejb.alt.config.ejb.OpenejbJar;

import java.io.File;

/**
 * Class is to remain "dumb" and should not have deployment logic added to it.
 * Class is intentionally not an interface as that would encourage "smart" implementations
 * @version $Revision$ $Date$
 */
public class EjbModule implements DeploymentModule {

    private ClassLoader classLoader;
    private final String jarURI;
    private final EjbJar ejbJar;
    private final OpenejbJar openejbJar;
    private final String moduleId;

    public EjbModule(String jarURI, EjbJar ejbJar, OpenejbJar openejbJar) {
        this(null, jarURI, ejbJar, openejbJar);
    }

    public EjbModule(ClassLoader classLoader, String jarURI, EjbJar ejbJar, OpenejbJar openejbJar) {
        this.classLoader = classLoader;
        this.ejbJar = ejbJar;
        this.jarURI = jarURI;
        this.openejbJar = openejbJar;
        File file = new File(jarURI);
        moduleId = file.getName().replaceFirst(".jar$","");
    }

    public String getModuleId() {
        return moduleId;
    }

    public EjbJar getEjbJar() {
        return ejbJar;
    }

    public OpenejbJar getOpenejbJar() {
        return openejbJar;
    }

    public String getJarURI() {
        return jarURI;
    }

    public String getJarLocation() {
        return getJarURI();
    }

    public ClassLoader getClassLoader() {
        return classLoader;
    }

    public void setClassLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }
}
