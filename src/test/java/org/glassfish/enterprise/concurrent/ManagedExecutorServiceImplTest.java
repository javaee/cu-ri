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

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.enterprise.concurrent.ManagedExecutorService;
import org.glassfish.enterprise.concurrent.AbstractManagedExecutorService.RejectPolicy;
import org.glassfish.enterprise.concurrent.spi.ContextSetupProvider;
import org.glassfish.enterprise.concurrent.test.BlockingRunnableImpl;
import org.glassfish.enterprise.concurrent.test.RunnableImpl;
import org.glassfish.enterprise.concurrent.test.TestContextService;
import org.glassfish.enterprise.concurrent.test.Util;
import org.glassfish.enterprise.concurrent.test.Util.BooleanValueProducer;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * Tests for Life cycle APIs in ManagedExecutorServiceImpl
 * 
 */
public class ManagedExecutorServiceImplTest {

    /**
     * Test for shutdown and isShutdown to verify that we do not regress Java SE
     * ExecutorService functionality
     */
    @Test
    public void testShutdown() {
        ManagedExecutorService mes = 
                createManagedExecutor("testShutdown", null);
        assertFalse(mes.isShutdown());
        mes.shutdown();
        assertTrue(mes.isShutdown());
    }

    /**
     * Test for shutdownNow to verify that we do not regress Java SE
     * ExecutorService functionality
     */
    @Test
    public void testShutdownNow() {
        ManagedExecutorService mes = 
                createManagedExecutor("testShutdownNow", null);
        assertFalse(mes.isShutdown());
        List<Runnable> tasks = mes.shutdownNow();
        assertTrue(tasks.isEmpty());
        assertTrue(mes.isShutdown());
        assertTrue(mes.isTerminated());
    }

    /**
     * Test for shutdownNow with unfinished task
     * to verify that we do not regress Java SE
     * ExecutorService functionality
     */
    @Test
    public void testShutdownNow_unfinishedTask() {
        ManagedExecutorService mes = 
                createManagedExecutor("testShutdown_unfinishedTask", null);
        assertFalse(mes.isShutdown());
        BlockingRunnableImpl task1 = new BlockingRunnableImpl(null, 0L);
        mes.submit(task1);
        RunnableImpl task2 = new RunnableImpl(null);
        mes.submit(task2); // this task cannot start until task1 has finished
        List<Runnable> tasks = mes.shutdownNow();
        assertFalse(mes.isTerminated());

        assertTrue(tasks.size() > 0);
        task1.stopBlocking();
        assertTrue(mes.isShutdown());
    }

    /**
     * Test for awaitsTermination with unfinished task
     * to verify that we do not regress Java SE
     * ExecutorService functionality
     */
    @Test
    public void testAwaitsTermination() throws Exception {
        ManagedExecutorService mes = 
                createManagedExecutor("testAwaitsTermination", null);
        assertFalse(mes.isShutdown());        
        BlockingRunnableImpl task = new BlockingRunnableImpl(null, 0L);
        mes.submit(task);
        mes.shutdown();
        try {
            assertFalse(mes.awaitTermination(1, TimeUnit.SECONDS));
        } catch (InterruptedException ex) {
            Logger.getLogger(ManagedExecutorServiceImplTest.class.getName()).log(Level.SEVERE, null, ex);
        }
        task.stopBlocking();
        try {
            assertTrue(mes.awaitTermination(10, TimeUnit.SECONDS));
        } catch (InterruptedException ex) {
            Logger.getLogger(ManagedExecutorServiceImplTest.class.getName()).log(Level.SEVERE, null, ex);
        }
        assertTrue(mes.isTerminated());
    }
    
    @Test
    public void testQueueCreation() throws Exception {
        // Case 1: corePoolSize of 0, queue size of Integer.MAX_VALUE
        //         A SynchronousQueue should be created
        ManagedExecutorServiceImpl mes1 = createManagedExecutor("mes1", 0, 1, Integer.MAX_VALUE);
        BlockingQueue<Runnable> queue = getQueue(mes1);
        assertTrue(queue instanceof SynchronousQueue);
        assertEquals(0, queue.remainingCapacity());
        
        // Case 2: corePoolSize of non-zero, queue size of Integer.MAX_VALUE
        //         An unbounded queue should be created
        ManagedExecutorServiceImpl mes2 = createManagedExecutor("mes2", 1, 1, Integer.MAX_VALUE);
        queue = getQueue(mes2);
        assertEquals(Integer.MAX_VALUE, queue.remainingCapacity());
        
        // Case 3: queue size of 0, A SynchronousQueue should be created 
        ManagedExecutorServiceImpl mes3 = createManagedExecutor("mes3", 0, 1, 0);
        queue = getQueue(mes3);
        assertTrue(queue instanceof SynchronousQueue);
        assertEquals(0, queue.remainingCapacity());
        
        // Case 4: queue size not 0 or Integer.MAX_VALUE, 
        //         A queue with specified capacity should be created
        final int QUEUE_SIZE = 1234;
        ManagedExecutorServiceImpl mes4 = createManagedExecutor("mes4", 0, 1, QUEUE_SIZE);
        queue = getQueue(mes4);
        assertEquals(QUEUE_SIZE, queue.remainingCapacity());
        
        ManagedExecutorServiceImpl mes5 = createManagedExecutor("mes5", 1, 10, QUEUE_SIZE);
        queue = getQueue(mes5);
        assertEquals(QUEUE_SIZE, queue.remainingCapacity());        
    }
    
    @Test
    public void testConstructorWithGivenQueue() {
        final int QUEUE_SIZE = 8765;
        BlockingQueue<Runnable> queue = new ArrayBlockingQueue<>(QUEUE_SIZE);
        ManagedExecutorServiceImpl mes = 
                new ManagedExecutorServiceImpl("mes", null, 0, false,
                1, 10, 0, TimeUnit.SECONDS, 0L,
                new TestContextService(null), RejectPolicy.ABORT,
                queue);
        assertEquals(queue, getQueue(mes));
        assertEquals(QUEUE_SIZE, getQueue(mes).remainingCapacity());
    }
    
    @Test
    public void testTaskCounters() {
        final AbstractManagedExecutorService mes = 
                (AbstractManagedExecutorService) createManagedExecutor("testTaskCounters", null);
        assertEquals(0, mes.getTaskCount());
        assertEquals(0, mes.getCompletedTaskCount());
        RunnableImpl task = new RunnableImpl(null);
        Future future = mes.submit(task);
        try {
            future.get();
        } catch (InterruptedException | ExecutionException ex) {
            Logger.getLogger(ManagedExecutorServiceAdapterTest.class.getName()).log(Level.SEVERE, null, ex);
        }
        assertTrue(future.isDone());
        Util.waitForBoolean(new BooleanValueProducer() {
            @Override
            public boolean getValue() {
                return (mes.getTaskCount() > 0) && (mes.getCompletedTaskCount() > 0);
            }
          }
                , true, getLoggerName()
        );

        assertEquals(1, mes.getTaskCount());
        assertEquals(1, mes.getCompletedTaskCount()); 
    }
    
    @Test
    public void testThreadLifeTime() {
        final AbstractManagedExecutorService mes = 
                createManagedExecutor("testThreadLifeTime", 
                1, 2, 0, 3L, 0L, false);
        
        Collection<AbstractManagedThread> threads = mes.getThreads();
        assertNull(threads);
        
        RunnableImpl runnable = new RunnableImpl(null);
        Future f = mes.submit(runnable);
        try {
            f.get();
        } catch (Exception ex) {
            Logger.getLogger(ManagedExecutorServiceImplTest.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        threads = mes.getThreads();
        assertEquals(1, threads.size());
        System.out.println("Waiting for threads to expire due to threadLifeTime");
        Util.waitForBoolean(new BooleanValueProducer() {
            public boolean getValue() {
                // wait for all threads get expired
                return mes.getThreads() == null;
            }
        }, true, getLoggerName());
        
    }
        
    @Test
    public void testHungThreads() {
        final AbstractManagedExecutorService mes = 
                createManagedExecutor("testThreadLifeTime", 
                1, 2, 0, 0L, 1L, false);
        
        Collection<AbstractManagedThread> threads = mes.getHungThreads();
        assertNull(threads);
        
        BlockingRunnableImpl runnable = new BlockingRunnableImpl(null, 0L);
        Future f = mes.submit(runnable);
        try {
            Thread.sleep(1000); // sleep for 1 second
        } catch (InterruptedException ex) {
        }
        
        // should get one hung thread
        threads = mes.getHungThreads();
        assertEquals(1, threads.size());
        
        // tell task to stop waiting
        runnable.stopBlocking();
        Util.waitForTaskComplete(runnable, getLoggerName());

        // should not have any more hung threads
        threads = mes.getHungThreads();
        assertNull(threads);
    }
        
    @Test
    public void testHungThreads_LongRunningTasks() {
        final AbstractManagedExecutorService mes = 
                createManagedExecutor("testThreadLifeTime", 
                1, 2, 0, 0L, 1L, true);
        
        Collection<AbstractManagedThread> threads = mes.getHungThreads();
        assertNull(threads);
        
        BlockingRunnableImpl runnable = new BlockingRunnableImpl(null, 0L);
        Future f = mes.submit(runnable);
        try {
            Thread.sleep(1000); // sleep for 1 second
        } catch (InterruptedException ex) {
        }
        
        // should not get any hung thread because longRunningTasks is true
        threads = mes.getHungThreads();
        assertNull(threads);
        
        // tell task to stop waiting
        runnable.stopBlocking();
        Util.waitForTaskComplete(runnable, getLoggerName());

        // should not have any more hung threads
        threads = mes.getHungThreads();
        assertNull(threads);
    }
        
    protected ManagedExecutorService createManagedExecutor(String name, 
            ContextSetupProvider contextCallback) {
        return new ManagedExecutorServiceImpl(name, null, 0, false,
                1, 1,  
                0, TimeUnit.SECONDS,
                0L,
                Integer.MAX_VALUE, 
                new TestContextService(contextCallback), 
                RejectPolicy.ABORT);
    }

    protected ManagedExecutorServiceImpl createManagedExecutor(String name,
        int corePoolSize, int maxPoolSize, int queueSize) {
        return createManagedExecutor(name, corePoolSize, maxPoolSize,
                queueSize, 0L, 0L, false);
    }

    protected ManagedExecutorServiceImpl createManagedExecutor(String name,
            int corePoolSize, int maxPoolSize, int queueSize, long threadLifeTime, 
            long hungTask, boolean longRunningTasks) {
        return new ManagedExecutorServiceImpl(name, null, hungTask,
                longRunningTasks,
                corePoolSize, maxPoolSize,  
                0, TimeUnit.SECONDS,
                threadLifeTime,
                queueSize, 
                new TestContextService(null), 
                RejectPolicy.ABORT);
    }
    
    BlockingQueue<Runnable> getQueue(ManagedExecutorServiceImpl mes) {
        ThreadPoolExecutor executor = (ThreadPoolExecutor) mes.getThreadPoolExecutor();
        return executor.getQueue();
    }

    public String getLoggerName() {
        return ManagedExecutorServiceImplTest.class.getName();
    }

}
