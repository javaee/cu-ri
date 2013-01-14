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
package org.glassfish.concurrent.ri.test;

import java.util.concurrent.Future;
import javax.enterprise.concurrent.ManagedExecutorService;
import static junit.framework.Assert.*;

public class TaskRunEnvironmentTracker {
    protected final ManagedTaskListenerImpl taskListener;
    protected int taskStartingCount = 0, taskSubmittedCount = 0;
    public ClassLoader taskRunClassLoader = null;
    protected Future<?> taskStartingFuture = null, taskSubmittedFuture = null,
            taskAbortedFuture = null, taskDoneFuture = null;
    protected ManagedExecutorService taskStartingExecutor = null, 
            taskSubmittedExecutor = null;
    protected int threadPriority = 0;
    protected boolean daemonThread = false;

    public TaskRunEnvironmentTracker(ManagedTaskListenerImpl taskListener) {
        this.taskListener = taskListener;
    }

    public void verifyAfterRun(String classloaderName, int priority, boolean daemon) {
        assertEquals(priority, threadPriority);
        assertEquals(daemon, daemonThread);
        verifyAfterRun(classloaderName);
    }

    public void verifyAfterRun(String classloaderName) {
        verifyAfterRun(classloaderName, true);
    }
    
    public void verifyAfterRun(String classloaderName, boolean checksTaskListener) {
        // verify taskListener counters taken within the run() method
        if (checksTaskListener && taskListener != null) {
            assertEquals(1, taskStartingCount);
            assertEquals(1, taskSubmittedCount);
            assertNull(taskDoneFuture);
            assertNull(taskAbortedFuture);
        }
        assertTrue(taskRunClassLoader instanceof NamedClassLoader);
        assertEquals(classloaderName, ((NamedClassLoader) taskRunClassLoader).getName());
    }

    protected void captureThreadContexts() {
        taskRunClassLoader = Thread.currentThread().getContextClassLoader();
        threadPriority = Thread.currentThread().getPriority();
        daemonThread = Thread.currentThread().isDaemon();
        if (taskListener != null) {
            
            taskStartingFuture = taskListener.startingFuture;
            if (taskStartingFuture != null) {
                ManagedTaskListenerImpl.CallbackParameters starting = 
                        taskListener.find(taskStartingFuture, taskListener.STARTING);
                taskStartingExecutor = starting.getExecutor();
                taskStartingCount = starting.getCount();
            }
            
            taskSubmittedFuture = taskListener.submittedFuture;
            if (taskSubmittedFuture != null) {
                ManagedTaskListenerImpl.CallbackParameters submitting = 
                        taskListener.find(taskStartingFuture, taskListener.SUBMITTED);
                taskSubmittedExecutor = submitting.getExecutor();
                taskSubmittedCount = submitting.getCount();
            }
            
            taskDoneFuture = taskListener.doneFuture;            
            taskAbortedFuture = taskListener.abortedFuture;            
        }
    }

}
