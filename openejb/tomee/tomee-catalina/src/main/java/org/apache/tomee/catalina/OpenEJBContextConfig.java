/*
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
package org.apache.tomee.catalina;

import org.apache.catalina.core.StandardContext;
import org.apache.catalina.deploy.WebXml;
import org.apache.catalina.startup.ContextConfig;
import org.apache.openejb.assembler.classic.WebAppInfo;
import org.apache.openejb.loader.SystemInstance;
import org.apache.openejb.util.LogCategory;
import org.apache.openejb.util.Logger;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.bcel.classfile.ClassFormatException;
import org.xml.sax.InputSource;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;

public class OpenEJBContextConfig extends ContextConfig {

    private static Logger logger = Logger.getInstance(LogCategory.OPENEJB, OpenEJBContextConfig.class);

    private TomcatWebAppBuilder.StandardContextInfo info;

    public OpenEJBContextConfig(TomcatWebAppBuilder.StandardContextInfo standardContextInfo) {
        logger.info("OpenEJBContextConfig("+standardContextInfo.toString());
        info = standardContextInfo;
    }

    @Override
    protected WebXml createWebXml() {
        String prefix = "";
        if (context instanceof StandardContext) {
            StandardContext standardContext = (StandardContext) context;
            prefix = standardContext.getEncodedPath();
            if (prefix.startsWith("/")) {
                prefix = prefix.substring(1);
            }
        }
        return new OpenEJBWebXml(prefix);
    }

    public class OpenEJBWebXml extends WebXml {
        public static final String OPENEJB_WEB_XML_MAJOR_VERSION_PROPERTY = "openejb.web.xml.major";

        private String prefix;

        public OpenEJBWebXml(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public int getMajorVersion() {
            return SystemInstance.get().getOptions().get(prefix + "." + OPENEJB_WEB_XML_MAJOR_VERSION_PROPERTY,
                    SystemInstance.get().getOptions().get(OPENEJB_WEB_XML_MAJOR_VERSION_PROPERTY, super.getMajorVersion()));
        }
    }

    @Override
    protected void parseWebXml(InputSource source, WebXml dest, boolean fragment) {
        super.parseWebXml(source, dest, fragment);
    }

    @Override
    protected void processAnnotationsFile(File file, WebXml fragment,
            boolean handlesTypesOnly) {
        logger.info("processAnnotationsFile "+ file.getAbsolutePath() );
        try {
            final WebAppInfo webAppInfo = info.get();

            if (webAppInfo == null) {
                logger.warning("WebAppInfo not found. " + info);
                super.processAnnotationsFile(file, fragment, handlesTypesOnly);
                return;
            }

            logger.info("Optimized Scan of File " + file.getAbsolutePath());

            // TODO We should just remember which jars each class came from
            // then we wouldn't need to lookup the class from the URL in this
            // way to guarantee we only add classes from this URL.
            final URLClassLoader loader = new URLClassLoader(new URL[]{file.toURI().toURL()});
            for (String webAnnotatedClassName : webAppInfo.webAnnotatedClasses) {

                final String classFile = webAnnotatedClassName.replace('.', '/') + ".class";
                final URL classUrl = loader.getResource(classFile);

                if (classUrl == null) {
                    logger.debug("Not present " + webAnnotatedClassName);
                    continue;
                }

                logger.debug("Found " + webAnnotatedClassName);

                final InputStream inputStream = classUrl.openStream();
                try {
                    processAnnotationsStream(inputStream, fragment, handlesTypesOnly);
                    logger.debug("Succeeded " + webAnnotatedClassName);
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    inputStream.close();
                }

            }
        } catch (Exception e) {
            logger.error("OpenEJBContextConfig.processAnnotationsFile: failed.", e);
        }
    }

    @Override
    protected void processAnnotationsUrl(URL url, WebXml fragment, boolean handlesTypeOnly) {
        logger.info("processAnnotationsUrl " + url);
        if (SystemInstance.get().getOptions().get("tomee.tomcat.scan", false)) {
            super.processAnnotationsUrl(url, fragment, handlesTypeOnly);
            return;
        }

        try {
            final WebAppInfo webAppInfo = info.get();

            if (webAppInfo == null) {
                logger.warning("WebAppInfo not found. " + info);
                super.processAnnotationsUrl(url, fragment, handlesTypeOnly);
                return;
            }

            logger.info("Optimized Scan of URL " + url);

            // TODO We should just remember which jars each class came from
            // then we wouldn't need to lookup the class from the URL in this
            // way to guarantee we only add classes from this URL.
            final URLClassLoader loader = new URLClassLoader(new URL[]{url});
            for (String webAnnotatedClassName : webAppInfo.webAnnotatedClasses) {

                final String classFile = webAnnotatedClassName.replace('.', '/') + ".class";
                final URL classUrl = loader.getResource(classFile);

                if (classUrl == null) {
                    logger.debug("Not present " + webAnnotatedClassName);
                    continue;
                }

                logger.debug("Found " + webAnnotatedClassName);

                final InputStream inputStream = classUrl.openStream();
                try {
                    processAnnotationsStream(inputStream, fragment, handlesTypeOnly);
                    logger.debug("Succeeded " + webAnnotatedClassName);
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    inputStream.close();
                }

            }
        } catch (Exception e) {
            logger.error("OpenEJBContextConfig.processAnnotationsUrl: failed.", e);
        }
    }
}
