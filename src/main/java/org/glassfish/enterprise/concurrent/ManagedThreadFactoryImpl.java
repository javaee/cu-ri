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

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import javax.enterprise.concurrent.ManageableThread;
import javax.enterprise.concurrent.ManagedThreadFactory;
import org.glassfish.enterprise.concurrent.internal.ManagedFutureTask;
import org.glassfish.enterprise.concurrent.spi.ContextHandle;
import org.glassfish.enterprise.concurrent.spi.ContextSetupProvider;

/**
 * Implementation of ManagedThreadFactory interface.
 */
public class ManagedThreadFactoryImpl implements ManagedThreadFactory {

    private List<ManagedThread> threads;
    private boolean stopped = false;
    private Lock lock; // protects threads and stopped

    private String name;
    final private ContextSetupProvider contextSetupProvider;
    // A non-null ContextService should be provided if thread context should
    // be setup before running the Runnable passed in through the newThread
    // method.
    // Context service could be null if the ManagedThreadFactoryImpl is
    // used for creating threads for ManagedExecutorService, where it is
    // not necessary to set up thread context at thread creation time. In that
    // case, thread context is set up before running each task.
    final private ContextServiceImpl contextService;
    private int priority;
    private long hungTaskThreshold = 0L; // in milliseconds
    private boolean longRunningTasks;
    private AtomicInteger threadIdSequence = new AtomicInteger();


    public ManagedThreadFactoryImpl(String name) {
        this(name, null, Thread.NORM_PRIORITY, false);
    }

    public ManagedThreadFactoryImpl(String name, ContextServiceImpl contextService) {
        this(name, contextService, Thread.NORM_PRIORITY, false);
    }

    public ManagedThreadFactoryImpl(String name,
                                    ContextServiceImpl contextService,
                                    int priority,
                                    boolean longRunningTasks) {
        this.name = name;
        this.contextService = contextService;
        this.contextSetupProvider = contextService != null? contextService.getContextSetupProvider(): null;
        this.priority = priority;
        this.longRunningTasks = longRunningTasks;
        threads = new ArrayList<>();
        lock = new ReentrantLock();
    }

    public String getName() {
        return name;
    }

    public long getHungTaskThreshold() {
        return hungTaskThreshold;
    }

    public void setHungTaskThreshold(long hungTaskThreshold) {
        this.hungTaskThreshold = hungTaskThreshold;
    }
    
    @Override
    public Thread newThread(Runnable r) {
        lock.lock();
        try {
            if (stopped) {
                // do not create new thread if stopped
                return null;
            }
            ContextHandle contextHandleForSetup = null;
            if (contextSetupProvider != null) {
                contextHandleForSetup = contextSetupProvider.saveContext(contextService);
            }
            ManagedThread newThread = createThread(r, contextHandleForSetup);
            newThread.setPriority(priority);
            if (longRunningTasks) {
                newThread.setDaemon(true);
            }
            threads.add(newThread);
            return newThread;
        }
        finally {
            lock.unlock();
        }
    }

    protected ManagedThread createThread(final Runnable r, final ContextHandle contextHandleForSetup) {
        if (System.getSecurityManager() == null) {
            return new ManagedThread(r, contextHandleForSetup);
        } else {
            return (ManagedThread) AccessController.doPrivileged(
              new PrivilegedAction() {
                @Override
                public Object run() {
                    return new ManagedThread(r, contextHandleForSetup);
                }
              });
        }
    }
    
    protected void removeThread(ManagedThread t) {
        lock.lock();
        try {
            threads.remove(t);
        }
        finally {
            lock.unlock();
        }
    }
    
    public void taskStarting(Thread t, ManagedFutureTask task) {
        if (t instanceof ManagedThread) {
            ManagedThread mt = (ManagedThread) t;
            // called in thread t, so no need to worry about synchronization
            mt.taskStartTime = System.currentTimeMillis();
            mt.task = task;
        }
    }
    
    public void taskDone(Thread t) {
        if (t instanceof ManagedThread) {
            ManagedThread mt = (ManagedThread) t;
            // called in thread t, so no need to worry about synchronization
            mt.taskStartTime = 0L;
            mt.task = null;
        }
    }


    /**
     * Stop the ManagedThreadFactory instance. This should be used by the
     * component that creates the ManagedThreadFactory when the component is
     * stopped. All threads that this ManagedThreadFactory has created using
     * the #newThread() method are interrupted.
     */
    public void stop() {
      lock.lock();
      try {
        stopped = true;
        // interrupt all the threads created by this factory
        Iterator<ManagedThread> iter = threads.iterator();
        while(iter.hasNext()) {
            ManagedThread t = iter.next();
            try {
               t.shutdown(); // mark threads as shutting down
               t.interrupt();
            } catch (SecurityException ignore) {                
            }
        }
      }
      finally {
          lock.unlock();
      }      
    }
    
    /**
     * ManageableThread to be returned by {@code ManagedThreadFactory.newThread()}
     */
    public class ManagedThread extends Thread implements ManageableThread {

        volatile long taskStartTime = 0L;
        volatile ManagedFutureTask task = null;
        volatile boolean shutdown = false;
        final ContextHandle contextHandleForSetup;
        
        public ManagedThread(Runnable target, ContextHandle contextHandleForSetup) {
            super(target);
            setName(name + "-Thread-" + threadIdSequence.incrementAndGet());
            this.contextHandleForSetup = contextHandleForSetup;
        }

        @Override
        public void run() {
            ContextHandle handle = null;
            try {
                if (contextHandleForSetup != null) {
                    handle = contextSetupProvider.setup(contextHandleForSetup);
                }
                super.run();
            } catch (Throwable t) {
                t.printStackTrace();
            } finally {
                if (handle != null) {
                    contextSetupProvider.reset(handle);
                }
                removeThread(this);
            }
        }
        
        long getTaskRunTime(long now) {
            if (taskStartTime > 0) {
                long taskRunTime = now - taskStartTime;
                return taskRunTime > 0? taskRunTime: 0;
            }
            return 0;
        }
        
        boolean isTaskHung(long now) {
            if (hungTaskThreshold > 0) {
                return getTaskRunTime(now) - hungTaskThreshold > 0;
            }
            return false;
        }
        
        boolean cancelTask() {
            if (task != null) {
                return task.cancel(true);
            }
            return false;
        }
        
        String getTaskIdentityName() {
            if (task != null) {
                return task.getTaskIdentityName();
            }
            return "null";
        }
        
        /**
         * Marks the thread for shutdown so application components could 
         * check the status of this thread and finish any work as soon
         * as possible.
         */
        public void shutdown() {
            shutdown = true;
        }
        
        @Override
        public boolean isShutdown() {
            return shutdown;
        }
    }
}
