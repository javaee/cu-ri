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

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import javax.enterprise.concurrent.AbortedException;
import javax.enterprise.concurrent.ContextService;
import javax.enterprise.concurrent.ManagedTask;
import javax.enterprise.concurrent.ManagedTaskListener;
import org.glassfish.enterprise.concurrent.AbstractManagedExecutorService;
import org.glassfish.enterprise.concurrent.spi.ContextHandle;
import org.glassfish.enterprise.concurrent.spi.ContextSetupProvider;

/**
 * Future implementation to be returned by methods in ManagedExecutorSerivceImpl.
 * This class is responsible for saving and restoring thread context, as well
 * as invoking ManagedTaskListener methods.
 */
public class ManagedFutureTask<V> extends FutureTask<V> implements Future<V> {


    final protected AbstractManagedExecutorService executor;
    final protected ManagedTaskListener taskListener;
    protected ContextHandle contextHandleForSetup = null;
    protected ContextHandle contextHandleForReset = null;
    protected Object task;
    protected Throwable taskRunThrowable;
    protected TaskDoneCallback taskDoneCallback;
    final boolean isContextualCallback;
    IllegalStateException contextSetupException = null;
    
    public ManagedFutureTask(
            AbstractManagedExecutorService executor,
            Runnable runnable,
            V result) {
        super(runnable, result);
        this.task = runnable;
        this.executor = executor;
        this.taskListener = getManagedTaskListener(task);
        this.isContextualCallback = isTaskContextualCallback(task) || executor.isContextualCallback();
        captureContext(executor);
    }
    
    public ManagedFutureTask(
            AbstractManagedExecutorService executor,
            Callable callable) {
        super(callable);
        this.task = callable;
        this.executor = executor;
        this.taskListener = getManagedTaskListener(task);
        this.isContextualCallback = isTaskContextualCallback(task) || executor.isContextualCallback();
        captureContext (executor);
    }

    private ManagedTaskListener getManagedTaskListener(Object task) {
        if (task instanceof ManagedTask) {
            return ((ManagedTask) task).getManagedTaskListener();
        }
        return null;
    }

    private boolean isTaskContextualCallback(Object task) {
        // Contextual callback no longer specified through execution properties
        return false;
    }

    protected final void captureContext(AbstractManagedExecutorService executor) {
        ContextSetupProvider contextSetupProvider = executor.getContextSetupProvider();
        ContextService contextService = executor.getContextService();
        if (contextService != null && contextSetupProvider != null) {
            contextHandleForSetup = contextSetupProvider.saveContext(contextService);            
        }
    }
    
    public void setupContext() {
        ContextSetupProvider contextSetupProvider = executor.getContextSetupProvider();
        if (contextSetupProvider != null) {
            try {
                contextHandleForReset = contextSetupProvider.setup(contextHandleForSetup);
            } catch (IllegalStateException ex) {
                // context handle not in valid state. Do not run the task.
                contextSetupException = ex;
            }
        }
    }
    
    public void resetContext() {
        if (contextSetupException == null) {
            // only call reset() if setupContext() was called successfully
            ContextSetupProvider contextSetupProvider = executor.getContextSetupProvider();
            if (contextSetupProvider != null) {
                contextSetupProvider.reset(contextHandleForReset);
            }
        }
    }
 
    public void run() {
        if (contextSetupException == null) {
            super.run();
        }
        else {
            // context handle not in valid state, throws AbortedException and
            // do not run the task
            AbortedException ex = new AbortedException(contextSetupException.getMessage());
            setException(ex);
            if (taskListener != null) {
                // notify listener. No need to set context here as it wouldn't work
                // anyway
                taskListener.taskAborted(this, 
                            executor.getExecutorForTaskListener(),
                            task,
                            ex);
            }
        }
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        boolean result = super.cancel(mayInterruptIfRunning);
        if (result && taskListener != null) {
            try {
                if (isContextualCallback) {
                    setupContext();
                }
                taskListener.taskAborted(this, 
                        executor.getExecutorForTaskListener(),
                        task,
                        new CancellationException());
            }
            finally {
                if (isContextualCallback) {
                    resetContext();
                }
            }
        }
        return result;
    }
    
    public void submitted() {
        if (taskListener != null) {
            try {
                if (isContextualCallback) {
                    setupContext();
                }
                taskListener.taskSubmitted(this,  
                        executor.getExecutorForTaskListener(),
                        task);
            }
            finally {
                if (isContextualCallback) {
                    resetContext();
                }
            }
        }
    }

    @Override
    protected void done() { 
        // for calling taskDone for cancelled tasks
        super.done();
        if (taskDoneCallback != null) {
            taskDoneCallback.taskDone(this);
        }
        if (taskListener != null && isCancelled()) {
            try {
                if (isContextualCallback) {
                    setupContext();
                }
                taskListener.taskDone(this, 
                        executor.getExecutorForTaskListener(),
                        task,
                        new CancellationException());
            }
            finally {
                if (isContextualCallback) {
                    resetContext();
                }
            }
        }
    }

    @Override
    protected void setException(Throwable t) {
        super.setException(t);
        taskRunThrowable = t;
    }
    
    public void starting(Thread t) {
        if (executor.getManagedThreadFactory() != null) {
            executor.getManagedThreadFactory().taskStarting(t, this);
        }
        
        if (taskListener != null) {
            taskListener.taskStarting(this, 
                    executor.getExecutorForTaskListener(),
                    task);
        }
    }
    
    /**
     * Call by ThreadPoolExecutor after a task is done execution.
     * This is called on the thread where the task is run, so there is no
     * need to set up thread context before calling the ManagedTaskListener
     * callback method.
     * 
     * @param t any runtime exception encountered during executing the task. But
     * any Throwable thrown during executing of running the task would have
     * been caught by FutureTask and would have been set by setException(). So
     * t is ignored here.
     */
    public void done(Throwable t) {
        if (executor.getManagedThreadFactory() != null) {
            executor.getManagedThreadFactory().taskDone(Thread.currentThread());
        }
        
        if (taskListener != null) {
            taskListener.taskDone(this, 
                    executor.getExecutorForTaskListener(),
                    task,
                    t != null? t: taskRunThrowable);
        }
    }

    public void setTaskDoneCallback(TaskDoneCallback taskDoneCallback) {
        this.taskDoneCallback = taskDoneCallback;
    }

    public String getTaskIdentityName() {
        if (task instanceof ManagedTask) {
          Map<String, String> executionProperties = ((ManagedTask)task).getExecutionProperties();
          if (executionProperties != null) {
              String taskName = executionProperties.get(ManagedTask.IDENTITY_NAME);
              if (taskName != null) {
                  return taskName;
              }
          }
        }
        // if a name is not provided for the task, use toString() as the name
        return task.toString();
    }
    
}
