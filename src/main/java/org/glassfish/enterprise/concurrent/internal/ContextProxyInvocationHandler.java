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
package org.glassfish.enterprise.concurrent.internal;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import javax.enterprise.concurrent.ContextService;
import org.glassfish.enterprise.concurrent.ContextServiceImpl;
import org.glassfish.enterprise.concurrent.spi.ContextHandle;
import org.glassfish.enterprise.concurrent.spi.ContextSetupProvider;
import org.glassfish.enterprise.concurrent.spi.TransactionHandle;
import org.glassfish.enterprise.concurrent.spi.TransactionSetupProvider;

/**
 * InvocationHandler used by ContextServiceImpl
 */
public class ContextProxyInvocationHandler implements InvocationHandler {

    final protected ContextSetupProvider contextSetupProvider;
    final protected ContextService contextService;
    final protected TransactionSetupProvider transactionSetupProvider;
    final protected Object proxiedObject;
    protected Map<String, String> executionProperties;

    public ContextProxyInvocationHandler(ContextServiceImpl contextService, Object proxiedObject) {
        this.contextSetupProvider = contextService.getContextSetupProvider();
        this.proxiedObject = proxiedObject;
        this.contextService = contextService;
        this.transactionSetupProvider = contextService.getTransactionSetupProvider();
        this.executionProperties = null;
    }
    
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        // TODO supports USE_PARENT_TRANSACTION property
        Object result = null;
        Class methodDeclaringClass = method.getDeclaringClass();
        
        if (methodDeclaringClass == java.lang.Object.class) {
            // hashCode, equals, or toString method of java.lang.Object will
            // have java.lang.Object as declaring class (per java doc in
            // java.lang.reflect.Proxy). These methods would not be run
            // under creator's context
            result = method.invoke(proxiedObject, args);
        }
        else {
            // for all other methods, invoke under creator's context
            ContextHandle contextHandleForSetup =
                    contextSetupProvider.saveContext(contextService, contextService.getExecutionProperties(proxy));
            ContextHandle contextHandleForReset = contextSetupProvider.setup(contextHandleForSetup);
            // Ask TransactionSetupProvider to perform any transaction related
            // setup before running the proxy. For example, suspend current
            // transaction on current thread unless USE_PARENT_TRANSACTION property is set
            TransactionHandle txHandle = null;
            if (transactionSetupProvider != null) {
              txHandle = transactionSetupProvider.beforeProxyMethod(isUseTransactionPropertySet());
            }
            try {
                result = method.invoke(proxiedObject, args);
            }
            finally {
                contextSetupProvider.reset(contextHandleForReset);
                if (transactionSetupProvider != null) {
                    transactionSetupProvider.afterProxyMethod(txHandle, isUseTransactionPropertySet());
                }
            }
        }
        return result;
    }

    public Map<String, String> getExecutionProperties() {
        // returns a copy of the executionProperties
        if (executionProperties == null) {
            return null;
        }
        Map<String, String> copy = new HashMap<>();
        copy.putAll(executionProperties);
        return copy;
    }

    public void setExecutionProperties(Map<String, String> executionProperties) {
        this.executionProperties = executionProperties;
    }

    public ContextService getContextService() {
        return contextService;
    }
    
    protected boolean isUseTransactionPropertySet() {
      if (executionProperties != null && "true".equalsIgnoreCase(executionProperties.get(ContextService.USE_PARENT_TRANSACTION))) {
          return true;
      }
      return false;
    }
    
}
