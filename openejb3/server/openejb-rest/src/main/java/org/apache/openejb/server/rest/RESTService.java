/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 *     contributor license agreements.  See the NOTICE file distributed with
 *     this work for additional information regarding copyright ownership.
 *     The ASF licenses this file to You under the Apache License, Version 2.0
 *     (the "License"); you may not use this file except in compliance with
 *     the License.  You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing, software
 *     distributed under the License is distributed on an "AS IS" BASIS,
 *     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *     See the License for the specific language governing permissions and
 *     limitations under the License.
 */

package org.apache.openejb.server.rest;

import org.apache.openejb.assembler.classic.AppInfo;
import org.apache.openejb.assembler.classic.Assembler;
import org.apache.openejb.assembler.classic.DeploymentListener;
import org.apache.openejb.assembler.classic.WebAppInfo;
import org.apache.openejb.core.CoreContainerSystem;
import org.apache.openejb.core.WebContext;
import org.apache.openejb.loader.SystemInstance;
import org.apache.openejb.server.SelfManaging;
import org.apache.openejb.server.ServerService;
import org.apache.openejb.server.ServiceException;
import org.apache.openejb.server.httpd.HttpListenerRegistry;
import org.apache.openejb.server.httpd.util.HttpUtil;
import org.apache.openejb.spi.ContainerSystem;
import org.apache.openejb.util.LogCategory;
import org.apache.openejb.util.Logger;

import javax.ws.rs.core.Application;
import javax.ws.rs.core.UriBuilder;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * @author Romain Manni-Bucau
 */
public abstract class RESTService implements ServerService, SelfManaging, DeploymentListener {
    public static final Logger LOGGER = Logger.getInstance(LogCategory.OPENEJB_RS, RESTService.class);

    private static final String IP = "n/a";
    private static final int PORT = -1;
    public static final String NOPATH_PREFIX = "http://nopath/";

    private final Set<AppInfo> deployedApplications = new HashSet<AppInfo>();
    private final Set<WebAppInfo> deployedWebApps = new HashSet<WebAppInfo>();
    private Assembler assembler;
    private CoreContainerSystem containerSystem;
    private RsRegistry rsRegistry;

    private Map<String, Object> restInstances = new HashMap<String, Object>();

    @Override public void afterApplicationCreated(final AppInfo appInfo) {
        if (deployedApplications.add(appInfo)) {
            for (final WebAppInfo webApp : appInfo.webApps) {
                final WebContext webContext = containerSystem.getWebContext(webApp.moduleId);
                if (webContext == null) {
                    return;
                }

                if (!deployedWebApps.add(webApp)) {
                    return;
                }

                final ClassLoader classLoader = getClassLoader(webContext.getClassLoader());

                // The spec says:
                //
                // "The resources and providers that make up a JAX-RS application are configured via an application-supplied
                // subclass of Application. An implementation MAY provide alternate mechanisms for locating resource
                // classes and providers (e.g. runtime class scanning) but use of Application is the only portable means of
                //  configuration."
                //
                //  The choice here is to deploy using the Application if it exists or to use the scanned classes
                //  if there is no Application.
                //
                //  Like this providing an Application subclass user can totally control deployed services.

                if (webApp.restApplications.isEmpty()) {
                    for (String clazz : webApp.restClass) {
                        try {
                            deployRestObject(webApp.contextRoot, load(classLoader, clazz), null, classLoader, RsHttpListener.Scope.PROTOTYPE);
                        } catch (ClassNotFoundException e) {
                            throw new OpenEJBRestRuntimeException("can't find class " + clazz, e);
                        }
                        LOGGER.info("REST service deployed: " + clazz);
                    }
                } else {
                    for (String app : webApp.restApplications) { // normally a unique one but we support more
                        Application appInstance;
                        try {
                            appInstance = Application.class.cast(load(classLoader, app));
                        } catch (ClassNotFoundException e) {
                            throw new OpenEJBRestRuntimeException("can't find class " + app, e);
                        }

                        for (Object o : appInstance.getSingletons()) {
                            deployRestObject(webApp.contextRoot, o, appInstance, classLoader, RsHttpListener.Scope.SINGLETON);
                            LOGGER.info("deploying REST singleton: " + o);
                        }
                        for (Class<?> clazz : appInstance.getClasses()) {
                            deployRestObject(webApp.contextRoot, instantiate(clazz), appInstance, classLoader, RsHttpListener.Scope.PROTOTYPE);
                            LOGGER.info("deploying REST class: " + clazz);
                        }

                        LOGGER.info("REST application deployed: " + app);
                    }
                }
            }
        }
    }

    private void deployRestObject(String context, Object o, Application app, ClassLoader classLoader, RsHttpListener.Scope scope) {
        final String nopath = getAddress(NOPATH_PREFIX + context, o) + "/.*";
        final RsHttpListener listener = createHttpListener(o, scope);
        final List<String> addresses = rsRegistry.createRsHttpListener(listener, classLoader, nopath.substring(NOPATH_PREFIX.length() - 1));
        final String address = HttpUtil.selectSingleAddress(addresses);

        restInstances.put(address, o);
        listener.deploy(getFullContext(address, context), o, app);
    }

    protected abstract RsHttpListener createHttpListener(Object o, RsHttpListener.Scope scope);


    private static String getFullContext(String address, String context) {
        int idx = address.indexOf(context);
        String base = address.substring(0, idx);
        if (!base.endsWith("/") && !context.startsWith("/")) {
            base = base + '/';
        }
        return base + context;
    }

    private String getAddress(String context, Object o) {
        try {
            return UriBuilder.fromUri(new URI(context)).path(o.getClass()).build().toURL().toString();
        } catch (MalformedURLException e) {
            throw new OpenEJBRestRuntimeException("url is malformed", e);
        } catch (URISyntaxException e) {
            throw new OpenEJBRestRuntimeException("uri syntax is not correct", e);
        }
    }

    private void undeployRestObject(String context) {
        RsHttpListener.class.cast(rsRegistry.removeListener(context)).undeploy();
    }

    private Object load(ClassLoader classLoader, String clazz) throws ClassNotFoundException {
        Class<?> loaded;
        loaded = classLoader.loadClass(clazz);
        return instantiate(loaded);
    }

    private <T> T instantiate(Class<T> clazz) {
        try {
            return clazz.newInstance();
        } catch (InstantiationException e) {
            final String msg = "can't instantiate REST object " + clazz;
            LOGGER.error(msg, e);
            throw new OpenEJBRestRuntimeException(msg, e);
        } catch (IllegalAccessException e) {
            final String msg = "can't access REST object " + clazz + clazz;
            LOGGER.error(msg, e);
            throw new OpenEJBRestRuntimeException(msg, e);
        }
    }

    private static ClassLoader getClassLoader(ClassLoader classLoader) {
        ClassLoader cl = classLoader;
        if (cl == null) {
            cl = Thread.currentThread().getContextClassLoader();
        }
        if (cl == null) {
            cl = RESTService.class.getClassLoader();
        }
        return cl;
    }

    @Override public void beforeApplicationDestroyed(AppInfo appInfo) {
        if (deployedApplications.contains(appInfo)) {
            for (WebAppInfo webApp : appInfo.webApps) {
                for (Map.Entry<String, Object> instances : restInstances.entrySet()) {
                    if (instances.getKey().endsWith(webApp.contextRoot)) {
                        undeployRestObject(instances.getKey());
                    }
                }
            }
        }
    }

    @Override public void start() throws ServiceException {
        beforeStart();

        containerSystem = (CoreContainerSystem) SystemInstance.get().getComponent(ContainerSystem.class);
        assembler = SystemInstance.get().getComponent(Assembler.class);
        if (assembler != null) {
            assembler.addDeploymentListener(this);
            for (AppInfo appInfo : assembler.getDeployedApplications()) {
                afterApplicationCreated(appInfo);
            }
        }
    }

    protected void beforeStart() {
        rsRegistry = SystemInstance.get().getComponent(RsRegistry.class);
        if (rsRegistry == null && SystemInstance.get().getComponent(HttpListenerRegistry.class) != null) {
            rsRegistry = new RsRegistryImpl();
        }
    }

    @Override public void stop() throws ServiceException {
        if (assembler != null) {
            assembler.removeDeploymentListener(this);
            for (AppInfo appInfo : new ArrayList<AppInfo>(deployedApplications)) {
                beforeApplicationDestroyed(appInfo);
            }
        }
    }

    @Override public void service(InputStream in, OutputStream out) throws ServiceException, IOException {
        throw new UnsupportedOperationException(getClass().getName() + " cannot be invoked directly");
    }

    @Override public void service(Socket socket) throws ServiceException, IOException {
        throw new UnsupportedOperationException(getClass().getName() + " cannot be invoked directly");
    }

    @Override public String getIP() {
        return IP;
    }

    @Override public int getPort() {
        return PORT;
    }

    @Override public void init(Properties properties) throws Exception {
        // no-op
    }
}
