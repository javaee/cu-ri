/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2018 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://oss.oracle.com/licenses/CDDL+GPL-1.1
 * or LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at LICENSE.txt.
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

package org.glassfish.enterprise.concurrent.spi;

import java.io.Serializable;
import javax.enterprise.concurrent.ContextService;
import javax.enterprise.concurrent.ManagedTask;

/**
 * To be implemented by application server for performing proper transaction
 * setup before invoking a proxy method of a contextual proxy object created by 
 * various {@code createContextualProxy} methods in {@link ContextService} 
 * and after the proxy method has finished running.
 */
public interface TransactionSetupProvider extends Serializable {

    /**
     * Method to be called before invoking the proxy method to allow the
     * Java EE Product Provider to perform any transaction-related setup.
     * 
     * @param transactionExecutionProperty The value of the {@link ManagedTask#TRANSACTION}
     *          execution property for the ContextService that creates the
     *          contextual proxy object.
     * @return A TransactionHandle that will be passed back to the 
     *         {@link #afterProxyMethod(org.glassfish.enterprise.concurrent.spi.TransactionHandle, java.lang.String) }
     *         after the proxy method returns.
     */
    public TransactionHandle beforeProxyMethod(String transactionExecutionProperty);
    

    /**
     * Method to be called after invoking the proxy method to allow the 
     * Java EE Product Provider to perform any transaction-related cleanup.
     * 
     * @param handle The TransactionHandle that was returned in the 
     *               {@link #beforeProxyMethod(java.lang.String) } call.
     * @param transactionExecutionProperty The value of the {@link ManagedTask#TRANSACTION}
     *          execution property for the ContextService that creates the
     *          contextual proxy object.
     */
    public void afterProxyMethod(TransactionHandle handle, String transactionExecutionProperty);
}
