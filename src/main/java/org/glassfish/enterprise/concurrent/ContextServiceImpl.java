/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2013 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://glassfish.dev.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */
package org.glassfish.enterprise.concurrent;

import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.Enumeration;
import java.util.Map;
import javax.enterprise.concurrent.ContextService;
import org.glassfish.enterprise.concurrent.internal.ContextProxyInvocationHandler;
import org.glassfish.enterprise.concurrent.spi.ContextSetupProvider;
import org.glassfish.enterprise.concurrent.spi.TransactionSetupProvider;

/**
 * Implementation of ContextService interface
 */
public class ContextServiceImpl implements ContextService, Serializable {

    static final long serialVersionUID = -386695836029966433L;
    
    protected final ContextSetupProvider contextSetupProvider;
    protected final TransactionSetupProvider transactionSetupProvider;
    protected final String name;
    
    final private static String INVALID_PROXY = 
            "contextObject is not a valid contextual object proxy created with the createContextualProxy method";
    final private static String NULL_CONTEXTPROPERTIES = 
            "contextProperties cannot be null";
    final private static String NULL_INSTANCE =
            "instance cannot be null";
    final private static String NO_INTERFACES =
            "No interfaces is provided in the method argument";
    final private static String CLASS_DOES_NOT_IMPLEMENT_INTERFACES =
            "Class does not implement at least one of the provided interfaces";
    final private static String DIFFERENT_CONTEXTSERVICE =
            "Proxy is created by a different ContextService object";
    
    public ContextServiceImpl(String name, ContextSetupProvider contextSetupProvider) {
        this(name, contextSetupProvider, null);
    }

    public ContextServiceImpl(String name, ContextSetupProvider contextSetupProvider, 
            TransactionSetupProvider transactionSetupProvider) {
        this.name = name;
        this.contextSetupProvider = contextSetupProvider;
        this.transactionSetupProvider = transactionSetupProvider;
    }

    public String getName() {
        return name;
    }

    public ContextSetupProvider getContextSetupProvider() {
        return contextSetupProvider;
    }
    
    public TransactionSetupProvider getTransactionSetupProvider() {
        return transactionSetupProvider;
    }
    
    @Override
    public Object createContextualProxy(Object instance, Class<?>... interfaces) {
        return createContextualProxy(instance, null, interfaces);
    }

    @Override
    public Object createContextualProxy(Object instance, Map<String, String> executionProperties, Class<?>... interfaces) {
        if (instance == null) {
            throw new IllegalArgumentException(NULL_INSTANCE); 
        }
        if (interfaces == null || interfaces.length == 0) {
            throw new IllegalArgumentException(NO_INTERFACES);
        }
        Class instanceClass = instance.getClass();
        for (Class thisInterface: interfaces) {
            if (! thisInterface.isAssignableFrom(instanceClass)) {
                throw new IllegalArgumentException(CLASS_DOES_NOT_IMPLEMENT_INTERFACES);
            }  
        }
        ContextProxyInvocationHandler handler = new ContextProxyInvocationHandler(this, instance, executionProperties);
        Object proxy = Proxy.newProxyInstance(instance.getClass().getClassLoader(), interfaces, handler);
        return proxy;
    }

    @Override
    public <T> T createContextualProxy(T instance, Class<T> intf) {
        return createContextualProxy(instance, null, intf);
    }
    
    @Override
    public <T> T createContextualProxy(T instance, Map<String, String> executionProperties, Class<T> intf) {
        if (instance == null) {
            throw new IllegalArgumentException(NULL_INSTANCE); 
        }
        if (intf == null) {
            throw new IllegalArgumentException(NO_INTERFACES);
        }
        ContextProxyInvocationHandler handler = new ContextProxyInvocationHandler(this, instance, executionProperties);
        Object proxy = Proxy.newProxyInstance(instance.getClass().getClassLoader(), new Class[]{intf}, handler);
        return (T) proxy;
    }
    
    @Override
    public Map<String, String> getExecutionProperties(Object contextObject) {
        ContextProxyInvocationHandler handler = verifyHandler(contextObject);
        return handler.getExecutionProperties();
    }
    
    protected void verifyStringValue(Enumeration e) throws ClassCastException {
        while (e.hasMoreElements()) {
            String value = (String)e.nextElement();
        }
    }

    protected ContextProxyInvocationHandler verifyHandler(Object contextObject) {
        InvocationHandler handler = Proxy.getInvocationHandler(contextObject);
        if (handler instanceof ContextProxyInvocationHandler) {
            ContextProxyInvocationHandler cpih = (ContextProxyInvocationHandler) handler;
            if (cpih.getContextService() != this) {
                throw new IllegalArgumentException(DIFFERENT_CONTEXTSERVICE);
            }
            return cpih;
        }
        throw new IllegalArgumentException(INVALID_PROXY);
    }
}
