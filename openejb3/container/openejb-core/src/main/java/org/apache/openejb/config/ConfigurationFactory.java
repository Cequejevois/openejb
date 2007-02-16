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
package org.apache.openejb.config;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import javax.xml.bind.JAXBException;

import org.apache.openejb.OpenEJBException;
import org.apache.openejb.assembler.classic.AppInfo;
import org.apache.openejb.assembler.classic.Assembler;
import org.apache.openejb.assembler.classic.BmpEntityContainerInfo;
import org.apache.openejb.assembler.classic.ClientInfo;
import org.apache.openejb.assembler.classic.CmpEntityContainerInfo;
import org.apache.openejb.assembler.classic.ConnectionManagerInfo;
import org.apache.openejb.assembler.classic.ConnectorInfo;
import org.apache.openejb.assembler.classic.ContainerInfo;
import org.apache.openejb.assembler.classic.ContainerSystemInfo;
import org.apache.openejb.assembler.classic.EjbJarInfo;
import org.apache.openejb.assembler.classic.EnterpriseBeanInfo;
import org.apache.openejb.assembler.classic.FacilitiesInfo;
import org.apache.openejb.assembler.classic.JndiContextInfo;
import org.apache.openejb.assembler.classic.MdbContainerInfo;
import org.apache.openejb.assembler.classic.OpenEjbConfiguration;
import org.apache.openejb.assembler.classic.OpenEjbConfigurationFactory;
import org.apache.openejb.assembler.classic.PersistenceUnitInfo;
import org.apache.openejb.assembler.classic.ProxyFactoryInfo;
import org.apache.openejb.assembler.classic.SecurityServiceInfo;
import org.apache.openejb.assembler.classic.ServiceInfo;
import org.apache.openejb.assembler.classic.StatefulSessionContainerInfo;
import org.apache.openejb.assembler.classic.StatelessSessionContainerInfo;
import org.apache.openejb.assembler.classic.TransactionServiceInfo;
import org.apache.openejb.config.sys.ConnectionManager;
import org.apache.openejb.config.sys.Connector;
import org.apache.openejb.config.sys.Container;
import org.apache.openejb.config.sys.JndiProvider;
import org.apache.openejb.config.sys.Openejb;
import org.apache.openejb.config.sys.ProxyFactory;
import org.apache.openejb.config.sys.SecurityService;
import org.apache.openejb.config.sys.ServiceProvider;
import org.apache.openejb.config.sys.TransactionManager;
import org.apache.openejb.jee.ApplicationClient;
import org.apache.openejb.jee.jpa.EntityMappings;
import org.apache.openejb.jee.jpa.JpaJaxbUtil;
import org.apache.openejb.jee.jpa.unit.Persistence;
import org.apache.openejb.jee.jpa.unit.PersistenceUnit;
import org.apache.openejb.jee.jpa.unit.Property;
import org.apache.openejb.jee.oejb3.EjbDeployment;
import org.apache.openejb.loader.SystemInstance;
import org.apache.openejb.util.Logger;
import org.apache.openejb.util.Messages;

public class ConfigurationFactory implements OpenEjbConfigurationFactory {

    private static final Logger logger = Logger.getInstance("OpenEJB.startup.config", "org.apache.openejb.util.resources");
    private static final Messages messages = new Messages("org.apache.openejb.util.resources");

    private String configLocation = "";

    private OpenEjbConfiguration sys;

    private EjbJarInfoBuilder ejbJarInfoBuilder = new EjbJarInfoBuilder();

    private Openejb openejb;

    private DynamicDeployer deployer;
    private final DeploymentLoader deploymentLoader;
    private final boolean offline;

    public ConfigurationFactory() {
        this(false);
    }

    public static class Chain implements DynamicDeployer{
        private final List<DynamicDeployer> chain = new ArrayList<DynamicDeployer>();

        public boolean add(DynamicDeployer o) {
            return chain.add(o);
        }

        public AppModule deploy(AppModule appModule) throws OpenEJBException {
            for (DynamicDeployer deployer : chain) {
                appModule = deployer.deploy(appModule);
            }
            return appModule;
        }
    }

    public ConfigurationFactory(boolean offline) {
        this.offline = offline;
        deploymentLoader = new DeploymentLoader();

        Chain chain = new Chain();

        chain.add(new ReadDescriptors());
        
        chain.add(new AnnotationDeployer());

        if (System.getProperty("duct tape") != null){
            chain.add(new GeronimoMappedName());
        }

        chain.add(new AutoConfig());
        chain.add(new CmpJpaConversion());
        chain.add(new OpenEjb2CmpConversion());
        chain.add(new SunConversion());

        if (offline) {
            AutoDeploy autoDeploy = new AutoDeploy(this);
            autoDeploy.autoCreateConnectors(false);
            autoDeploy.autoCreateContainers(false);
            chain.add(autoDeploy);
        } else {
            chain.add(new AutoDeploy(this));
        }

        boolean shouldValidate = !SystemInstance.get().getProperty("openejb.validation.skip", "false").equalsIgnoreCase("true");
        if (shouldValidate) {
            chain.add(new ValidateEjbModule());
        } else {
            DeploymentLoader.logger.info("Validation is disabled.");
        }

        this.deployer = chain;
    }

    public void init(Properties props) throws OpenEJBException {

        configLocation = props.getProperty("openejb.conf.file");

        if (configLocation == null) {
            configLocation = props.getProperty("openejb.configuration");
        }

        configLocation = ConfigUtils.searchForConfiguration(configLocation, props);
        if (configLocation != null) {
            props.setProperty("openejb.configuration", configLocation);
        }

    }

    protected void install(ContainerInfo serviceInfo) throws OpenEJBException {
        if (sys != null) {
            sys.containerSystem.containers.add(serviceInfo);
        } else if (!offline) {
            Assembler assembler = SystemInstance.get().getComponent(Assembler.class);
            assembler.createContainer(serviceInfo);
        }
    }

    protected void install(ConnectorInfo serviceInfo) throws OpenEJBException {
        if (sys != null) {
            sys.facilities.connectors.add(serviceInfo);
        } else if (!offline) {
            Assembler assembler = SystemInstance.get().getComponent(Assembler.class);
            assembler.createConnector(serviceInfo);
        }
    }

    public OpenEjbConfiguration getOpenEjbConfiguration() throws OpenEJBException {

        if (configLocation != null) {
            openejb = ConfigUtils.readConfig(configLocation);
        } else {
            openejb = new Openejb();
        }

        sys = new OpenEjbConfiguration();
        sys.containerSystem = new ContainerSystemInfo();
        sys.facilities = new FacilitiesInfo();


        for (JndiProvider provider : openejb.getJndiProvider()) {

            JndiContextInfo info = configureService(provider, JndiContextInfo.class);
            sys.facilities.remoteJndiContexts.add(info);
        }

        sys.facilities.securityService = configureService(openejb.getSecurityService(), SecurityServiceInfo.class);

        sys.facilities.transactionService = configureService(openejb.getTransactionService(), TransactionServiceInfo.class);

        for (Connector connector : openejb.getConnector()) {

            ConnectorInfo connectorInfo = configureService(connector, ConnectorInfo.class);
            sys.facilities.connectors.add(connectorInfo);
        }

        ConnectionManagerInfo service = configureService(openejb.getConnectionManager(), ConnectionManagerInfo.class);
        sys.facilities.connectionManagers.add(service);

        sys.facilities.intraVmServer = configureService(openejb.getProxyFactory(), ProxyFactoryInfo.class);

        for (Container declaration : openejb.getContainer()) {

            Class<? extends ContainerInfo> infoClass = getContainerInfoType(declaration.getCtype());

            if (infoClass == null) {
                throw new OpenEJBException("Unrecognized contianer type " + declaration.getCtype());
            }

            ContainerInfo info = configureService(declaration, infoClass);

            sys.containerSystem.containers.add(info);
        }


        List<String> jarList = DeploymentsResolver.resolveAppLocations(openejb.getDeployments());

        for (String pathname : jarList) {

            try {
                File jarFile = new File(pathname);

                AppInfo appInfo = configureApplication(jarFile);

                sys.containerSystem.applications.add(appInfo);
            } catch (OpenEJBException alreadyHandled) {
            }
        }

        return sys;
    }


    public AppInfo configureApplication(File jarFile) throws OpenEJBException {
        logger.debug("Beginning load: " + jarFile.getAbsolutePath());

        AppInfo appInfo = null;
        try {

            AppModule appModule = deploymentLoader.load(jarFile);

            appInfo = configureApplication(appModule);


        } catch (OpenEJBException e) {
            String message = messages.format("conf.0004", jarFile.getAbsolutePath(), e.getMessage());
            // DO NOT REMOVE THE EXCEPTION FROM THIS LOG MESSAGE
            // removing this message causes NO messages to be printed when embedded
            logger.warning(message, e);
            throw e;
        }
        return appInfo;
    }

    public EjbJarInfo configureApplication(EjbModule ejbModule) throws OpenEJBException {
        AppModule appModule = new AppModule(ejbModule.getClassLoader(), ejbModule.getJarLocation());
        appModule.getEjbModules().add(ejbModule);
        AppInfo appInfo = configureApplication(appModule);
        return appInfo.ejbJars.get(0);
    }

    public ClientInfo configureApplication(ClientModule clientModule) throws OpenEJBException {
        AppModule appModule = new AppModule(clientModule.getClassLoader(), clientModule.getJarLocation());
        appModule.getClientModules().add(clientModule);
        AppInfo appInfo = configureApplication(appModule);
        return appInfo.clients.get(0);
    }

    public AppInfo configureApplication(AppModule appModule) throws OpenEJBException {
        logger.info("Configuring app: "+appModule.getJarLocation());
        deployer.deploy(appModule);

        AppInfo appInfo = new AppInfo();
        for (EjbModule ejbModule : appModule.getEjbModules()) {
            try {
                EjbJarInfo ejbJarInfo = ejbJarInfoBuilder.buildInfo(ejbModule);

                Map<String, EjbDeployment> deploymentsByEjbName = ejbModule.getOpenejbJar().getDeploymentsByEjbName();

                for (EnterpriseBeanInfo bean : ejbJarInfo.enterpriseBeans) {
                    EjbDeployment d = deploymentsByEjbName.get(bean.ejbName);

                    if (!getContainerIds().contains(d.getContainerId())) {
                        String msg = messages.format("config.noContainerFound", d.getContainerId(), d.getEjbName());
                        logger.fatal(msg);
                        throw new OpenEJBException(msg);
                    }

                    bean.containerId = d.getContainerId();
                }

                appInfo.ejbJars.add(ejbJarInfo);

            } catch (OpenEJBException e) {
                ConfigUtils.logger.i18n.warning("conf.0004", ejbModule.getJarURI(), e.getMessage());
                throw e;
            }
        }

        for (ClientModule clientModule : appModule.getClientModules()) {

            ApplicationClient applicationClient = clientModule.getApplicationClient();
            ClientInfo clientInfo = new ClientInfo();
            clientInfo.description = applicationClient.getDescription();
            clientInfo.displayName = applicationClient.getDisplayName();
            clientInfo.codebase = clientModule.getJarLocation();
            clientInfo.mainClass = clientModule.getMainClass();
            clientInfo.moduleId = getClientModuleId(clientModule);

            JndiEncInfoBuilder jndiEncInfoBuilder = new JndiEncInfoBuilder(appInfo.ejbJars);
            clientInfo.jndiEnc = jndiEncInfoBuilder.build(applicationClient, clientModule.getJarLocation());
            appInfo.clients.add(clientInfo);
        }

        for (PersistenceModule persistenceModule : appModule.getPersistenceModules()) {
            String rootUrl = persistenceModule.getRootUrl();
            Persistence persistence = persistenceModule.getPersistence();
            for (PersistenceUnit persistenceUnit : persistence.getPersistenceUnit()) {
                PersistenceUnitInfo info = new PersistenceUnitInfo();
                info.name = persistenceUnit.getName();
                info.persistenceUnitRootUrl = rootUrl;
                info.provider = persistenceUnit.getProvider();
                info.transactionType = persistenceUnit.getTransactionType().toString();

                Boolean excludeUnlistedClasses = persistenceUnit.isExcludeUnlistedClasses();
                info.excludeUnlistedClasses = excludeUnlistedClasses != null && excludeUnlistedClasses;

                info.jtaDataSource = persistenceUnit.getJtaDataSource();
                info.nonJtaDataSource = persistenceUnit.getNonJtaDataSource();

                info.jarFiles.addAll(persistenceUnit.getJarFile());
                info.classes.addAll(persistenceUnit.getClazz());
                info.mappingFiles.addAll(persistenceUnit.getMappingFile());

                // Handle Properties
                org.apache.openejb.jee.jpa.unit.Properties puiProperties = persistenceUnit.getProperties();
                if (puiProperties != null) {
                    for (Property property : puiProperties.getProperty()) {
                        info.properties.put(property.getName(), property.getValue());
                    }
                }

                // Persistence Unit Root Url
                appInfo.persistenceUnits.add(info);
            }
        }

        appInfo.jarPath = appModule.getJarLocation();
        List<URL> additionalLibraries = appModule.getAdditionalLibraries();
        for (URL url : additionalLibraries) {
            File file = new File(url.getPath());
            appInfo.libs.add(file.getAbsolutePath());
        }

        if (appModule.getCmpMappings() != null) {
            try {
                String cmpMappingsXml = JpaJaxbUtil.marshal(EntityMappings.class, appModule.getCmpMappings());
                appInfo.cmpMappingsXml = cmpMappingsXml;
            } catch (JAXBException e) {
                throw new OpenEJBException("Unable to marshal cmp entity mappings", e);
            }
        }

        logger.info("Loaded Module: " + appInfo.jarPath);
        return appInfo;
    }

    private static String getClientModuleId(ClientModule clientModule) {
        String jarLocation = clientModule.getJarLocation();
        File file = new File(jarLocation);
        String name = file.getName();
        if (name.endsWith(".jar") || name.endsWith(".zip")) {
            name = name.replaceFirst("....$", "");
        }
        return name;
    }


    private static class DefaultService {
        private final Class<? extends Service> type;
        private final String id;

        public DefaultService(String id, Class<? extends Service> type) {
            this.id = id;
            this.type = type;
        }
    }

    private static final Map<Class<? extends ServiceInfo>, DefaultService> defaultProviders = new HashMap<Class<? extends ServiceInfo>, DefaultService>();

    static {
        defaultProviders.put(MdbContainerInfo.class, new DefaultService("Default MDB Container", Container.class));
        defaultProviders.put(StatefulSessionContainerInfo.class, new DefaultService("Default Stateful Container", Container.class));
        defaultProviders.put(StatelessSessionContainerInfo.class, new DefaultService("Default Stateless Container", Container.class));
        defaultProviders.put(CmpEntityContainerInfo.class, new DefaultService("Default CMP Container", Container.class));
        defaultProviders.put(BmpEntityContainerInfo.class, new DefaultService("Default BMP Container", Container.class));
        defaultProviders.put(SecurityServiceInfo.class, new DefaultService("Default Security Service", SecurityService.class));
        defaultProviders.put(TransactionServiceInfo.class, new DefaultService("Default Transaction Manager", TransactionManager.class));
        defaultProviders.put(ConnectionManagerInfo.class, new DefaultService("Default Local TX ConnectionManager", ConnectionManager.class));
        defaultProviders.put(ProxyFactoryInfo.class, new DefaultService("Default JDK 1.3 ProxyFactory", ProxyFactory.class));
        defaultProviders.put(ConnectorInfo.class, new DefaultService("Default JDBC Database", Connector.class));
    }

    public <T extends ServiceInfo> T configureService(Class<? extends T> type) throws OpenEJBException {
        DefaultService defaultService = defaultProviders.get(type);

        Service service = null;
        try {
            service = defaultService.type.newInstance();
            service.setProvider(defaultService.id);
            service.setId(defaultService.id);
        } catch (Exception e) {
            throw new OpenEJBException("Cannot instantiate class " + defaultService.type.getName(), e);
        }

        return configureService(service, type);
    }


    private <T extends ServiceInfo>T configureService(Service service, Class<? extends T> type) throws OpenEJBException {
        if (service == null) {
            return configureService(type);
        }

        Properties declaredProperties = getDeclaredProperties(service);

        return configureService(type, service.getId(), declaredProperties, service.getProvider(), service.getClass().getSimpleName());
    }

    /**
     * Resolving the provider for a particular service follows this algorithm:
     *
     * 1.  Attempt to load the provider specified by the 'providerId'.
     * 2.  If this fails, throw NoSuchProviderException
     * 3.  If providerId is null, attempt to load the specified provider using the 'serviceId' as the 'providerId'
     * 4.  If this fails, check the hardcoded defaults for a default providerId using the supplied 'type'
     * 5.  If this fails, throw NoSuchProviderException 
     *
     * @param type required
     * @param serviceId required
     * @param declaredProperties optional
     * @param providerId optional
     * @param serviceType optional
     * @return
     * @throws OpenEJBException
     */
    public <T extends ServiceInfo>T configureService(Class<? extends T> type, String serviceId, Properties declaredProperties, String providerId, String serviceType) throws OpenEJBException {
        if (type == null) throw new NullPointerException("type");
        if (serviceId == null) throw new NullPointerException("serviceId");

        if (declaredProperties == null){
            declaredProperties = new Properties();
        }

        ServiceProvider provider = null;
        if (providerId != null) {
            provider = ServiceUtils.getServiceProvider(providerId);
        } else {
            try {
                provider = ServiceUtils.getServiceProvider(serviceId);
            } catch (NoSuchProviderException e) {
                DefaultService defaultProvider = defaultProviders.get(type);
                if (defaultProvider == null){
                    throw new NoSuchProviderException("Cannot determine a default provider for Service("+serviceId +", "+type.getSimpleName()+")");
                }
                provider = ServiceUtils.getServiceProvider(defaultProvider.id);
                providerId = provider.getId();
            }
        }

        logger.info("Configuring Service(id=" + serviceId + ", type=" + provider.getProviderType() + ", provider-id=" + provider.getId() + ")");
        Properties props = getDefaultProperties(provider);


        props.putAll(declaredProperties);

        Properties serviceProperties = getSystemProperties(serviceId);

        props.putAll(serviceProperties);

        if (serviceType != null && !provider.getProviderType().equals(serviceType)) {
            throw new OpenEJBException(messages.format("conf.4902", serviceId, serviceType));
        }

        T info = null;

        try {
            info = type.newInstance();
        } catch (Exception e) {
            throw new OpenEJBException("Cannot instantiate class " + type.getName(), e);
        }

        info.serviceType = provider.getProviderType();
        info.description = provider.getDescription();
        info.displayName = provider.getDisplayName();
        info.className = provider.getClassName();
        info.id = serviceId;
        info.properties = props;
        info.constructorArgs.addAll(parseConstructorArgs(provider));

//        String serviceId = serviceType + ":" + info.id;
//        if (serviceIds.contains(serviceId)) {
//            handleException("conf.0105", configLocation, info.id, serviceType);
//        }

//        serviceIds.add(serviceId);

        return info;
    }

    private Properties getSystemProperties(String serviceId) {
        // Override with system properties
        Properties serviceProperties = new Properties();
        String prefix = serviceId + ".";
        Properties sysProps = new Properties(System.getProperties());
        sysProps.putAll(SystemInstance.get().getProperties());
        for (Iterator iterator1 = sysProps.entrySet().iterator(); iterator1.hasNext();) {
            Map.Entry entry1 = (Map.Entry) iterator1.next();
            String key = (String) entry1.getKey();
            String value = (String) entry1.getValue();
            if (key.startsWith(prefix)) {
                key = key.replaceFirst(prefix, "");
                serviceProperties.setProperty(key, value);
            }
        }
        return serviceProperties;
    }

    private Properties getDeclaredProperties(Service service) throws OpenEJBException {
        /* 3. Load properties from the content in the Container
        *    element of the configuration file.
        */
        Properties declaredProperties = new Properties();
        try {
            if (service.getContent() != null) {
                String content = service.getContent();
                ByteArrayInputStream in = new ByteArrayInputStream(content.getBytes());

                try {
                    declaredProperties.load(in);
                } catch (IOException ex) {
                    throw new OpenEJBException(ServiceUtils.messages.format("conf.0012", ex.getLocalizedMessage()));
                }

            }
        } catch (OpenEJBException ex) {
            throw new OpenEJBException(ServiceUtils.messages.format("conf.0014", service.getClass().getSimpleName(), service.getId(), configLocation, ex.getLocalizedMessage()));
        }
        return declaredProperties;
    }

    private Properties getDefaultProperties(ServiceProvider provider) throws OpenEJBException {
        Properties props = new Properties();

        try {
            /*
            * 2. Load properties from the content in the service provider
            *    element of the service-jar.xml
            */

            if (provider.getContent() != null) {
                String content = provider.getContent();

                ByteArrayInputStream in = new ByteArrayInputStream(content.getBytes());

                try {
                    props.load(in);
                } catch (IOException ex) {
                    throw new OpenEJBException(ServiceUtils.messages.format("conf.0012", ex.getLocalizedMessage()));
                }

            }
        } catch (OpenEJBException ex) {
            throw new OpenEJBException(ServiceUtils.messages.format("conf.0013", provider.getId(), null, ex.getLocalizedMessage()));
        }
        return props;
    }

    static Map<String, Class<? extends ContainerInfo>> containerTypes = new HashMap<String, Class<? extends ContainerInfo>>();

    static {
        containerTypes.put(Bean.STATELESS, StatelessSessionContainerInfo.class);
        containerTypes.put(Bean.STATEFUL, StatefulSessionContainerInfo.class);
        containerTypes.put(Bean.BMP_ENTITY, BmpEntityContainerInfo.class);
        containerTypes.put(Bean.CMP_ENTITY, CmpEntityContainerInfo.class);
        containerTypes.put(Bean.MESSAGE, MdbContainerInfo.class);
    }

    protected static Class<? extends ContainerInfo> getContainerInfoType(String ctype) {
        return containerTypes.get(ctype);
    }

    private List<String> parseConstructorArgs(ServiceProvider service) {
        String constructor = service.getConstructor();
        if (constructor == null) {
            return Collections.emptyList();
        }
        return Arrays.asList(constructor.split("[ ,]+"));
    }


    protected List<String> getConnectorIds() {
        List<String> connectorIds = new ArrayList<String>();

        OpenEjbConfiguration runningConfig = getRunningConfig();
        for (ConnectorInfo connectorInfo : runningConfig.facilities.connectors) {
            connectorIds.add(connectorInfo.id);
        }

        if (sys != null) {
            for (ConnectorInfo connectorInfo : sys.facilities.connectors) {
                connectorIds.add(connectorInfo.id);
            }

            // The only time we'd have one of these is if we were building
            // the above sys instance
            if (openejb != null) {
                for (Connector connector : openejb.getConnector()) {
                    connectorIds.add(connector.getId());
                }
            }
        }
        return connectorIds;
    }

    protected List<String> getContainerIds() {
        List<String> containerIds = new ArrayList<String>();

        OpenEjbConfiguration runningConfig = getRunningConfig();
        if (runningConfig != null){
            for (ContainerInfo containerInfo : runningConfig.containerSystem.containers) {
                containerIds.add(containerInfo.id);
            }
        }

        if (sys != null) {
            for (ContainerInfo containerInfo : sys.containerSystem.containers) {
                containerIds.add(containerInfo.id);
            }

            // The only time we'd have one of these is if we were building
            // the above sys instance
            if (openejb != null) {
                for (Container container : openejb.getContainer()) {
                    containerIds.add(container.getId());
                }
            }
        }

        return containerIds;
    }

    protected List<ContainerInfo> getContainerInfos() {
        List<ContainerInfo> containers = new ArrayList<ContainerInfo>();

        OpenEjbConfiguration runningConfig = getRunningConfig();
        for (ContainerInfo containerInfo : runningConfig.containerSystem.containers) {
            containers.add(containerInfo);
        }

        if (sys != null) {
            for (ContainerInfo containerInfo : sys.containerSystem.containers) {
                containers.add(containerInfo);
            }
        }
        return containers;
    }


    private OpenEjbConfiguration getRunningConfig() {
        return SystemInstance.get().getComponent(OpenEjbConfiguration.class);
    }

}
