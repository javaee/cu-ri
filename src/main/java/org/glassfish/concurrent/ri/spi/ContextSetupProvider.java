/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2012 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.concurrent.ri.spi;

import java.io.Serializable;
import java.util.Map;
import javax.enterprise.concurrent.ContextService;

/**
 * To be implemented by application server for setting up proper execution
 * context before running a task, and also for resetting the execution context
 * after running a task.
 */
public interface ContextSetupProvider extends Serializable {
    
    /**
     * Called by ManagedExecutorService in the same thread that submits a
     * task to save the execution context of the submitting thread. 
     * 
     * @param contextService ContextService containing information on what
     * context should be saved
     * 
     * @return A ContextHandle that will be passed to the setup method
     * in the thread executing the task
     */
    public ContextHandle saveContext(ContextService contextService);
    
    /**
     * Called by ManagedExecutorService in the same thread that submits a
     * task to save the execution context of the submitting thread. 
     * 
     * @param contextService ContextService containing information on what
     * context should be saved
     * @param contextObjectProperties Additional properties specified for
     * for a context object when the ContextService object was created.
     * 
     * @return A ContextHandle that will be passed to the setup method
     * in the thread executing the task
     */
    public ContextHandle saveContext(ContextService contextService,
            Map<String, String> contextObjectProperties);
    
    /**
     * Called by ManagedExecutorService before executing a task to set up thread
     * context. It will be called in the thread that will be used for executing
     * the task.
     * 
     * @param contextHandle The ContextHandle object obtained from the call
     * to #saveContext
     * 
     * @return A ContextHandle that will be passed to the reset method
     * in the thread executing the task
     */
    public ContextHandle setup(ContextHandle contextHandle);
    
    /**
     * Called by ManagedExecutorService after executing a task to clean up and
     * reset thread context. It will be called in the thread that was used
     * for executing the task.
     * 
     * @param contextHandle The ContextHandle object obtained from the call
     * to #setup
     */
    public void reset(ContextHandle contextHandle);
    
}
