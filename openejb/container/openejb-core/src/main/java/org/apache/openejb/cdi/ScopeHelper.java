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
package org.apache.openejb.cdi;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.ConversationScoped;
import javax.enterprise.context.RequestScoped;
import javax.enterprise.context.SessionScoped;
import javax.enterprise.context.spi.Context;
import javax.inject.Singleton;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpSession;
import org.apache.webbeans.context.ContextFactory;

// helper for embedded case
public final class ScopeHelper {
    private ScopeHelper() {
        // no-op
    }

    public static void startContexts(final ContextFactory contextFactory, final ServletContext servletContext, final HttpSession session) throws Exception {
        contextFactory.initSingletonContext(servletContext);
        contextFactory.initApplicationContext(servletContext);
        contextFactory.initSessionContext(session);
        contextFactory.initConversationContext(null);
        contextFactory.initRequestContext(null);
    }

    public static void stopContexts(final ContextFactory contextFactory, final ServletContext servletContext, final HttpSession session) throws Exception {
        if(isActive(contextFactory.getStandardContext(SessionScoped.class))) {
            contextFactory.destroySessionContext(session);
        }
        if (isActive(contextFactory.getStandardContext(ConversationScoped.class))) {
            contextFactory.destroyConversationContext();
        }
        if (isActive(contextFactory.getStandardContext(RequestScoped.class))) {
            contextFactory.destroyRequestContext(null);
        }
        if (isActive(contextFactory.getStandardContext(ApplicationScoped.class))) {
            contextFactory.destroyApplicationContext(servletContext);
        }
        if (isActive(contextFactory.getStandardContext(Singleton.class))) {
            contextFactory.destroySingletonContext(servletContext);
        }
    }

    private static boolean isActive(Context context) {
        return context != null && context.isActive();
    }
}
