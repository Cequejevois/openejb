package org.apache.openejb.core.osgi.impl;

import org.osgi.framework.Bundle;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.Any;
import javax.enterprise.inject.Default;
import javax.enterprise.inject.spi.AfterBeanDiscovery;
import javax.enterprise.inject.spi.AnnotatedType;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.inject.spi.InjectionPoint;
import javax.enterprise.inject.spi.InjectionTarget;
import javax.enterprise.util.AnnotationLiteral;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class RegisterOSGIServicesExtension implements Extension {
    private static final Logger LOGGER = LoggerFactory.getLogger(RegisterOSGIServicesExtension.class);

    public void afterBeanDiscovery(@Observes final AfterBeanDiscovery abd, final BeanManager bm) {
        final Bundle[] bundles = OpenEJBBundleContextHolder.get().getBundles();
        for (Bundle bundle : bundles) {
            final ServiceReference[] services = bundle.getRegisteredServices();
            if (services != null) {
                for (ServiceReference service  : services) {
                    final Class<?> clazz = serviceClass(service);
                    final AnnotatedType<?> at = bm.createAnnotatedType(clazz);
                    final InjectionTarget<?> it = bm.createInjectionTarget(at);
                    abd.addBean(new OSGiServiceBean<Object>((InjectionTarget<Object>) it, service));
                    LOGGER.debug("added service {} as a CDI Application scoped bean", clazz.getName());
                }
            }
        }
    }

    private static Class<Object> serviceClass(ServiceReference service) {
        return (Class<Object>) service.getBundle().getBundleContext().getService(service).getClass();
    }

    public static class OSGiServiceBean<T> implements Bean<T> {
        private final ServiceReference service;
        private final InjectionTarget<T> injectiontarget;

        public OSGiServiceBean(final InjectionTarget<T> it, final ServiceReference srv) {
            injectiontarget = it;
            service = srv;
        }

        @Override
        public T create(CreationalContext<T> ctx) {
            final T instance = (T) service.getBundle().getBundleContext().getService(service);
            injectiontarget.inject(instance, ctx);
            injectiontarget.postConstruct(instance);
            return instance;
        }

        @Override
        public void destroy(T instance, CreationalContext<T> ctx) {
            injectiontarget.preDestroy(instance);
            ctx.release();
        }

        @Override
        public Set<Type> getTypes() {
            final Set<Type> types = new HashSet<Type>();
            for (String clazz : (String[]) service.getProperty(Constants.OBJECTCLASS)) {
                try {
                    types.add(service.getBundle().loadClass(clazz));
                } catch (ClassNotFoundException ignored) {
                    // no-op
                }
            }
            return types;
        }

        @Override
        public Set<Annotation> getQualifiers() {
            final Set<Annotation> qualifiers = new HashSet<Annotation>();
            qualifiers.add( new AnnotationLiteral<Default>() {} );
            qualifiers.add( new AnnotationLiteral<Any>() {} );
            return qualifiers;
        }

        @Override
        public Class<? extends Annotation> getScope() {
            return ApplicationScoped.class;
        }

        @Override
        public String getName() {
            return "OSGiService_" + service.getProperty(Constants.SERVICE_ID);
        }

        @Override
        public boolean isNullable() {
            return false;
        }

        @Override
        public Set<InjectionPoint> getInjectionPoints() {
            return injectiontarget.getInjectionPoints();
        }

        @Override
        public Class<?> getBeanClass() {
            return service.getBundle().getBundleContext().getService(service).getClass();
        }

        @Override
        public Set<Class<? extends Annotation>> getStereotypes() {
            return Collections.emptySet();
        }

        @Override
        public boolean isAlternative() {
            return true;
        }
    }
}