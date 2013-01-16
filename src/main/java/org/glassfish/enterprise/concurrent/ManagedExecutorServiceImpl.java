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

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import javax.enterprise.concurrent.ManagedExecutorService;
import org.glassfish.enterprise.concurrent.internal.ManagedFutureTask;
import org.glassfish.enterprise.concurrent.internal.ManagedThreadPoolExecutor;

/**
 * Implementation of ManagedExecutorService interface. See {@code AbstractManagedExecutorService}.
 */
public class ManagedExecutorServiceImpl extends AbstractManagedExecutorService {
    
    protected final ThreadPoolExecutor threadPoolExecutor;

    // The adapter to be returned to the caller needs to have all the lifecycle 
    // methods disabled
    protected final ManagedExecutorServiceAdapter adapter;

    public ManagedExecutorServiceImpl(String name,
            ManagedThreadFactoryImpl managedThreadFactory,
            long hungTaskThreshold,
            boolean longRunningTasks,
            int corePoolSize, int maxPoolSize, int keepAliveTime, 
            TimeUnit keepAliveTimeUnit, int queueCapacity,
            ContextServiceImpl contextService,
            RejectPolicy rejectPolicy,
            RunLocation runLocation,
            boolean contextualCallback) {
        super(name, managedThreadFactory, hungTaskThreshold, longRunningTasks,
                contextService,
                contextService != null? contextService.getContextSetupProvider(): null,
                rejectPolicy,
                runLocation,
                contextualCallback);

        BlockingQueue<Runnable> queue;
        if (queueCapacity <= 0) {
            // unbounded queue
            queue = new LinkedBlockingQueue<>();
        } else {
            queue = new ArrayBlockingQueue<>(queueCapacity); 
        }

        threadPoolExecutor = new ManagedThreadPoolExecutor(corePoolSize, maxPoolSize, 
                keepAliveTime, keepAliveTimeUnit, queue, 
                this.managedThreadFactory);
        adapter = new ManagedExecutorServiceAdapter(this);
    }
 
    @Override
    public void execute(Runnable command) {
        ManagedFutureTask<Void> task = getNewTaskFor(command, null);
        task.submitted();
        threadPoolExecutor.execute(task);
    }

    /**
     * Returns an adapter for ManagedExecutorService instance which has its
     * life cycle operations disabled.
     * 
     * @return The ManagedExecutorService instance with life cycle operations
     *         disabled for use by application components.
     **/
    public ManagedExecutorServiceAdapter getAdapter() {
        return adapter;
    }

    @Override
    protected ExecutorService getThreadPoolExecutor() {
        return threadPoolExecutor;
    }

    @Override
    public ManagedExecutorService getExecutorForTaskListener() {
        return adapter;
    }

    @Override
    protected <V> ManagedFutureTask<V> getNewTaskFor(Runnable r, V result) {
        return new ManagedFutureTask<>(this, r, result);
    }
    
    @Override
    protected ManagedFutureTask getNewTaskFor(Callable callable) {
        return new ManagedFutureTask(this, callable);
    }
}
