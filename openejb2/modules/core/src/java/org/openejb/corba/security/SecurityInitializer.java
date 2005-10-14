/**
 * Redistribution and use of this software and associated documentation
 * ("Software"), with or without modification, are permitted provided
 * that the following conditions are met:
 *
 * 1. Redistributions of source code must retain copyright
 *    statements and notices.  Redistributions must also contain a
 *    copy of this document.
 *
 * 2. Redistributions in binary form must reproduce the
 *    above copyright notice, this list of conditions and the
 *    following disclaimer in the documentation and/or other
 *    materials provided with the distribution.
 *
 * 3. The name "OpenEJB" must not be used to endorse or promote
 *    products derived from this Software without prior written
 *    permission of The OpenEJB Group.  For written permission,
 *    please contact openejb-group@openejb.sf.net.
 *
 * 4. Products derived from this Software may not be called "OpenEJB"
 *    nor may "OpenEJB" appear in their names without prior written
 *    permission of The OpenEJB Group. OpenEJB is a registered
 *    trademark of The OpenEJB Group.
 *
 * 5. Due credit should be given to the OpenEJB Project
 *    (http://openejb.sf.net/).
 *
 * THIS SOFTWARE IS PROVIDED BY THE OPENEJB GROUP AND CONTRIBUTORS
 * ``AS IS'' AND ANY EXPRESSED OR IMPLIED WARRANTIES, INCLUDING, BUT
 * NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL
 * THE OPENEJB GROUP OR ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * Copyright 2001 (C) The OpenEJB Group. All Rights Reserved.
 *
 * $Id$
 */
package org.openejb.corba.security;

import java.security.Principal;
import javax.security.auth.Subject;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.omg.CORBA.LocalObject;
import org.omg.PortableInterceptor.ORBInitInfo;
import org.omg.PortableInterceptor.ORBInitInfoPackage.DuplicateName;
import org.omg.PortableInterceptor.ORBInitializer;

import org.apache.geronimo.common.GeronimoSecurityException;
import org.apache.geronimo.security.PrimaryRealmPrincipal;
import org.apache.geronimo.security.RealmPrincipal;
import org.apache.geronimo.security.util.ConfigurationUtil;


/**
 * @version $Revision$ $Date$
 */
public class SecurityInitializer extends LocalObject implements ORBInitializer {

    private final Log log = LogFactory.getLog(SecurityInitializer.class);

    public SecurityInitializer() {
        if (log.isDebugEnabled()) log.debug("SecurityInitializer.<init>");
    }

    /**
     * Called during ORB initialization.  If it is expected that initial
     * services registered by an interceptor will be used by other
     * interceptors, then those initial services shall be registered at
     * this point via calls to
     * <code>ORBInitInfo.register_initial_reference</code>.
     *
     * @param info provides initialization attributes and operations by
     *             which Interceptors can be registered.
     */
    public void pre_init(ORBInitInfo info) {
    }

    /**
     * Called during ORB initialization. If a service must resolve initial
     * references as part of its initialization, it can assume that all
     * initial references will be available at this point.
     * <p/>
     * Calling the <code>post_init</code> operations is not the final
     * task of ORB initialization. The final task, following the
     * <code>post_init</code> calls, is attaching the lists of registered
     * interceptors to the ORB. Therefore, the ORB does not contain the
     * interceptors during calls to <code>post_init</code>. If an
     * ORB-mediated call is made from within <code>post_init</code>, no
     * request interceptors will be invoked on that call.
     * Likewise, if an operation is performed which causes an IOR to be
     * created, no IOR interceptors will be invoked.
     *
     * @param info provides initialization attributes and
     *             operations by which Interceptors can be registered.
     */
    public void post_init(ORBInitInfo info) {

        try {
            if (log.isDebugEnabled()) log.debug("Registering interceptors and policy factories");

            Subject defaultSubject = null;
            String[] strings = info.arguments();
            for (int i = 0; i < strings.length; i++) {
                String arg = strings[i];
                if (arg.startsWith("default-principal::")) {
                    defaultSubject = generateDefaultSubject(arg);
                    break;
                }
            }

            if (log.isDebugEnabled()) log.debug("Default subject: " + defaultSubject);

            try {
                info.add_client_request_interceptor(new ClientSecurityInterceptor());
                info.add_server_request_interceptor(new ServerSecurityInterceptor(info.allocate_slot_id(), info.allocate_slot_id(), defaultSubject));
                info.add_ior_interceptor(new IORSecurityInterceptor());
            } catch (DuplicateName dn) {
                log.error("Error registering interceptor", dn);
            }

            info.register_policy_factory(ClientPolicyFactory.POLICY_TYPE, new ClientPolicyFactory());
            info.register_policy_factory(ServerPolicyFactory.POLICY_TYPE, new ServerPolicyFactory());
        } catch (RuntimeException re) {
            log.error("Error registering interceptor", re);
            throw re;
        }
    }

    private Subject generateDefaultSubject(String argument) {
        Subject defaultSubject = new Subject();

        String[] tokens = argument.substring(19).split(":");
        String realm = tokens[0];
        String className = tokens[1];
        String principalName = tokens[2];

        String loginDomain = null;
        Principal realmPrincipal = ConfigurationUtil.generateRealmPrincipal(className, principalName, loginDomain, realm);
        if (realmPrincipal == null) {
            throw new GeronimoSecurityException("Unable to create realm principal");
        }
        PrimaryRealmPrincipal primaryRealmPrincipal = ConfigurationUtil.generatePrimaryRealmPrincipal(className, principalName, realm);
        if (primaryRealmPrincipal == null) {
            throw new GeronimoSecurityException("Unable to create primary realm principal");
        }

        defaultSubject.getPrincipals().add(realmPrincipal);
        defaultSubject.getPrincipals().add(primaryRealmPrincipal);

        return defaultSubject;
    }
}
