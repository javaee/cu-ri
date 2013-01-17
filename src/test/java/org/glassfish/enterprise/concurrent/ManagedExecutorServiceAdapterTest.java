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

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.glassfish.enterprise.concurrent;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.enterprise.concurrent.ManagedExecutorService;
import org.glassfish.enterprise.concurrent.AbstractManagedExecutorService.RejectPolicy;
import org.glassfish.enterprise.concurrent.AbstractManagedExecutorService.RunLocation;
import org.glassfish.enterprise.concurrent.internal.ManagedFutureTask;
import org.glassfish.enterprise.concurrent.spi.ContextSetupProvider;
import org.glassfish.enterprise.concurrent.test.BlockingRunnableImpl;
import org.glassfish.enterprise.concurrent.test.CallableImpl;
import org.glassfish.enterprise.concurrent.test.ClassloaderContextSetupProvider;
import org.glassfish.enterprise.concurrent.test.ManagedBlockingRunnableTask;
import org.glassfish.enterprise.concurrent.test.ManagedCallableTask;
import org.glassfish.enterprise.concurrent.test.ManagedRunnableTask;
import org.glassfish.enterprise.concurrent.test.ManagedTaskListenerImpl;
import org.glassfish.enterprise.concurrent.test.RunnableImpl;
import org.glassfish.enterprise.concurrent.test.TestContextService;
import org.glassfish.enterprise.concurrent.test.Util;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.Test;

public class ManagedExecutorServiceAdapterTest  {
    
    
    /**
     * Test of execute method, of ManagedExecutorServiceAdapter.
     * Verify context callback are called to setup context
     */
    @Test
    public void testExecute() {
        debug("execute");
        
        final String classloaderName = "testExcute" + new Date(System.currentTimeMillis());
        ClassloaderContextSetupProvider contextCallback = new ClassloaderContextSetupProvider(classloaderName);
        final RunnableImpl task = new RunnableImpl(null);
        ManagedExecutorService instance = 
                createManagedExecutor("execute", contextCallback);
        instance.execute(task);
        assertTrue("timeout waiting for task run being called", Util.waitForTaskComplete(task, getLoggerName()));
        task.verifyAfterRun(classloaderName); // verify context is setup for task
        
        debug("execute done");
    }

    public String getLoggerName() {
        return ManagedExecutorServiceAdapterTest.class.getName();
    }
    
    /**
     * Test of submit method, of class ManagedExecutorServiceImpl.
     * Verify context callback are called to setup context
     * Verify listener callback methods are called
     */
    @Test
    public void testSubmit_Runnable() {
        debug("submit_Runnable");
        
        final String classloaderName = "testSubmit" + new Date(System.currentTimeMillis());
        ClassloaderContextSetupProvider contextCallback = new ClassloaderContextSetupProvider(classloaderName);
        RunnableImpl task = new RunnableImpl(null);
        ManagedExecutorService instance = 
                createManagedExecutor("submit", contextCallback);
        Future future = instance.submit(task);
        try {
            assertNull(future.get());
        } catch (InterruptedException | ExecutionException ex) {
            Logger.getLogger(ManagedExecutorServiceAdapterTest.class.getName()).log(Level.SEVERE, null, ex);
        }
        assertTrue(future.isDone());
        assertFalse(future.isCancelled());
        task.verifyAfterRun(classloaderName); // verify context is setup for task
        
        // save original classloader prior to previous run
        ClassLoader cl = contextCallback.classloaderBeforeSetup;

        // reset classloaderBeforeSetup for testing purpose.
        contextCallback.classloaderBeforeSetup = null; 

        // submit a second task to verify that 
        // - the ContextHandler.reset is called 

        RunnableImpl task2 = new RunnableImpl(null);
        future = instance.submit(task2);
        try {
            future.get();
        } catch (InterruptedException | ExecutionException ex) {
            Logger.getLogger(ManagedExecutorServiceAdapterTest.class.getName()).log(Level.SEVERE, null, ex);
        }
        verify_testSubmit_Runnable(cl, contextCallback);        
    }
    
    protected void verify_testSubmit_Runnable(ClassLoader originalClassLoader,
            final ClassloaderContextSetupProvider contextCallback) {
        assertEquals(originalClassLoader, contextCallback.classloaderBeforeSetup);
        // reset() should be called at least once for the first task
        // reset() may or may not be called for the second task due to timing
        // issue when this check is performed.
        assertTrue(Util.waitForBoolean(
            new Util.BooleanValueProducer() {
              public boolean getValue() {
                return contextCallback.numResetCalled > 1;   
              }
            }, true, getLoggerName()));
    }

    /**
     * Test of submit method, of class ManagedExecutorServiceImpl.
     * Verify context callback are called to setup context
     * Verify listener callback methods are called
     */
    @Test
    public void testSubmit_Runnable_withListener() {
        debug("submit_Runnable_withListener");
        
        final String classloaderName = "testSubmit" + new Date(System.currentTimeMillis());
        ClassloaderContextSetupProvider contextCallback = new ClassloaderContextSetupProvider(classloaderName);
        ManagedTaskListenerImpl taskListener = new ManagedTaskListenerImpl();
        RunnableImpl task = new ManagedRunnableTask(taskListener);
        ManagedExecutorService instance = 
                createManagedExecutor("submit", contextCallback);
        Future future = instance.submit(task);
        try {
            assertNull(future.get());
        } catch (InterruptedException | ExecutionException ex) {
            Logger.getLogger(ManagedExecutorServiceAdapterTest.class.getName()).log(Level.SEVERE, null, ex);
        }
        assertTrue(future.isDone());
        assertFalse(future.isCancelled());
        task.verifyAfterRun(classloaderName); // verify context is setup for task
        taskListener.verifyCallback(ManagedTaskListenerImpl.STARTING, future, instance, null, classloaderName);
        taskListener.verifyCallback(ManagedTaskListenerImpl.SUBMITTED, future, instance, null);
    }
    
    protected void verify_testSubmit_Runnable_withListener(
            Future<?> future,
            ClassLoader originalClassLoader,
            ClassloaderContextSetupProvider contextSetupProvider,
            ManagedTaskListenerImpl taskListener) {
        assertEquals(originalClassLoader, contextSetupProvider.classloaderBeforeSetup);
        // taskAborted should not be called
        assertFalse(taskListener.eventCalled(future, ManagedTaskListenerImpl.ABORTED));

        // should be called for at least the first task
        assertTrue(contextSetupProvider.numResetCalled > 0);
        
        // taskDone chould be called for the first task
        assertTrue(taskListener.eventCalled(future, ManagedTaskListenerImpl.DONE));
    }    

    /**
     * Test of submit method, of class ManagedExecutorServiceImpl.
     * Verify context callback are called to setup context
     * Verify listener callback methods are called
     */
    @Test
    public void testSubmit_Runnable_result() {
        debug("submit_Runnable");
        
        final String classloaderName = "testSubmit" + new Date(System.currentTimeMillis());
        ClassloaderContextSetupProvider contextCallback = new ClassloaderContextSetupProvider(classloaderName);
        RunnableImpl task = new RunnableImpl(null);
        ManagedExecutorService instance = 
                createManagedExecutor("submit", contextCallback);
        final String result = "result" + new Date(System.currentTimeMillis());
        Future future = instance.submit(task, result);
        try {
            assertEquals(result, future.get());
        } catch (InterruptedException | ExecutionException ex) {
            Logger.getLogger(ManagedExecutorServiceAdapterTest.class.getName()).log(Level.SEVERE, null, ex);
        }
        assertTrue(future.isDone());
        assertFalse(future.isCancelled());
        task.verifyAfterRun(classloaderName); // verify context is setup for task
        
        // save original classloader prior to previous run
        ClassLoader cl = contextCallback.classloaderBeforeSetup;

        // reset classloaderBeforeSetup for testing purpose.
        contextCallback.classloaderBeforeSetup = null; 

        // submit a second task to verify that the ContextHandler.reset is called

        RunnableImpl task2 = new RunnableImpl(null);
        future = instance.submit(task2);
        try {
            future.get();
        } catch (InterruptedException | ExecutionException ex) {
            Logger.getLogger(ManagedExecutorServiceAdapterTest.class.getName()).log(Level.SEVERE, null, ex);
        }
        verify_testSubmit_Runnable(cl, contextCallback);        
    }
    
    /**
     * Test of submit method, of class ManagedExecutorServiceImpl.
     * Verify context callback are called to setup context
     * Verify listener callback methods are called
     */
    @Test
    public void testSubmit_Runnable_result_withListener() {
        debug("submit_Runnable_result_withListener");
        
        final String classloaderName = "testSubmit" + new Date(System.currentTimeMillis());
        ClassloaderContextSetupProvider contextCallback = new ClassloaderContextSetupProvider(classloaderName);
        ManagedTaskListenerImpl taskListener = new ManagedTaskListenerImpl();
        RunnableImpl task = new ManagedRunnableTask(taskListener);
        ManagedExecutorService instance = 
                createManagedExecutor("submit", contextCallback);
        final String result = "result" + new Date(System.currentTimeMillis());
        Future future = instance.submit(task, result);
        try {
            assertEquals(result, future.get());
        } catch (InterruptedException | ExecutionException ex) {
            Logger.getLogger(ManagedExecutorServiceAdapterTest.class.getName()).log(Level.SEVERE, null, ex);
        }
        assertTrue(future.isDone());
        assertFalse(future.isCancelled());
        task.verifyAfterRun(classloaderName); // verify context is setup for task
        taskListener.verifyCallback(ManagedTaskListenerImpl.STARTING, future, instance, null, classloaderName);
        taskListener.verifyCallback(ManagedTaskListenerImpl.SUBMITTED, future, instance, null);
    }

    /**
     * Test of submit method, of class ManagedExecutorServiceImpl.
     * Verify context callback are called to setup context
     * Verify listener callback methods are called
     */
    @Test
    public void testSubmit_Runnable_exception_withListener() {
        debug("submit_Runnable_result_exception_withListener");
        
        final String classloaderName = "testSubmit" + new Date(System.currentTimeMillis());
        ClassloaderContextSetupProvider contextCallback = new ClassloaderContextSetupProvider(classloaderName);
        ManagedTaskListenerImpl taskListener = new ManagedTaskListenerImpl();
        final RuntimeException result = new RuntimeException("result" + new Date(System.currentTimeMillis()));
        RunnableImpl task = new ManagedRunnableTask(taskListener, result);
        ManagedExecutorService instance = 
                createManagedExecutor("submit", contextCallback);
        Future future = instance.submit(task, result);
        ExecutionException executionException = null;
        try {
            future.get();
        } catch (InterruptedException ex) {
            Logger.getLogger(ManagedExecutorServiceAdapterTest.class.getName()).log(Level.SEVERE, null, ex);
        } catch (ExecutionException ex) {
            executionException = ex;
        }
        assertNotNull(executionException);
        assertEquals(result, executionException.getCause());
        assertTrue(future.isDone());
        assertFalse(future.isCancelled());
        task.verifyAfterRun(classloaderName); // verify context is setup for task
        taskListener.verifyCallback(ManagedTaskListenerImpl.STARTING, future, instance, null, classloaderName);
        taskListener.verifyCallback(ManagedTaskListenerImpl.SUBMITTED, future, instance, null);
    }

    /**
     * Test of submit method, of class ManagedExecutorServiceImpl.
     * Verify context callback are called to setup context
     * Verify listener callback methods are called
     */
    @Test
    public void testSubmit_Callable() {
        debug("submit_Callable");
        
        final String classloaderName = "testSubmit" + new Date(System.currentTimeMillis());
        ClassloaderContextSetupProvider contextCallback = new ClassloaderContextSetupProvider(classloaderName);
        final String result = "result" + new Date(System.currentTimeMillis());        
        CallableImpl<String> task = new CallableImpl<>(result, null);
        ManagedExecutorService instance = 
                createManagedExecutor("submit", contextCallback);
        Future<String> future = instance.submit(task);
        try {
            assertEquals(result, future.get());
        } catch (InterruptedException | ExecutionException ex) {
            Logger.getLogger(ManagedExecutorServiceAdapterTest.class.getName()).log(Level.SEVERE, null, ex);
        }
        assertTrue(future.isDone());
        assertFalse(future.isCancelled());        
        task.verifyAfterRun(classloaderName); // verify context is setup for task
        
        // save original classloader prior to previous run
        ClassLoader cl = contextCallback.classloaderBeforeSetup;

        // reset classloaderBeforeSetup for testing purpose.
        contextCallback.classloaderBeforeSetup = null; 

        // submit a second task to verify that the ContextHandler.reset is called

        CallableImpl<String> task2 = new CallableImpl("result");
        future = instance.submit(task2);
        try {
            future.get();
        } catch (InterruptedException | ExecutionException ex) {
            Logger.getLogger(ManagedExecutorServiceAdapterTest.class.getName()).log(Level.SEVERE, null, ex);
        }
        verify_testSubmit_Runnable(cl, contextCallback);        
    }
    
    /**
     * Test of submit method, of class ManagedExecutorServiceImpl.
     * Verify context callback are called to setup context
     * Verify listener callback methods are called
     */
    @Test
    public void testSubmit_Callable_withListener() {
        debug("submit_Callable_withListener");
        
        final String classloaderName = "testSubmit" + new Date(System.currentTimeMillis());
        ClassloaderContextSetupProvider contextCallback = new ClassloaderContextSetupProvider(classloaderName);
        ManagedTaskListenerImpl taskListener = new ManagedTaskListenerImpl();
        final String result = "result" + new Date(System.currentTimeMillis());
        CallableImpl<String> task = new ManagedCallableTask<>(result, taskListener);
        ManagedExecutorService instance = 
                createManagedExecutor("submit", contextCallback);
        Future<String> future = instance.submit(task);
        try {
            assertEquals(result, future.get());
        } catch (InterruptedException | ExecutionException ex) {
            Logger.getLogger(ManagedExecutorServiceAdapterTest.class.getName()).log(Level.SEVERE, null, ex);
        }
        assertTrue(future.isDone());
        assertFalse(future.isCancelled());        
        task.verifyAfterRun(classloaderName); // verify context is setup for task
        taskListener.verifyCallback(ManagedTaskListenerImpl.STARTING, future, instance, null, classloaderName);
        taskListener.verifyCallback(ManagedTaskListenerImpl.SUBMITTED, future, instance, null);
        debug("submit_Callable_withListener done");
}

    /**
     * Test of submit method
     */
    @Test
    public void testSubmit_Callable_exception_withListener() {
        debug("submit_Callable_exception_withListener");
        
        final String classloaderName = "testSubmit_Callable_exception_withListener" + new Date(System.currentTimeMillis());
        ClassloaderContextSetupProvider contextCallback = new ClassloaderContextSetupProvider(classloaderName);
        ManagedTaskListenerImpl taskListener = new ManagedTaskListenerImpl();
        final String result = "result" + new Date(System.currentTimeMillis());
        CallableImpl<String> task = new ManagedCallableTask<>(result, taskListener);
        task.setThrowsException(true);
        ManagedExecutorService instance = 
                createManagedExecutor("submit", contextCallback);
        Future<String> future = instance.submit(task);
        ExecutionException executionException = null;
        try {
            future.get();
        } catch (InterruptedException ex) {
            Logger.getLogger(ManagedExecutorServiceAdapterTest.class.getName()).log(Level.SEVERE, null, ex);
        } catch (ExecutionException ex) {
            executionException = ex;
        }
        assertNotNull(executionException);
        assertEquals(result, executionException.getCause().getMessage());
        assertTrue(future.isDone());
        assertFalse(future.isCancelled());        
        task.verifyAfterRun(classloaderName); // verify context is setup for task
        taskListener.verifyCallback(ManagedTaskListenerImpl.STARTING, future, instance, null, classloaderName);
        taskListener.verifyCallback(ManagedTaskListenerImpl.SUBMITTED, future, instance, null);

        debug("submit_Callable_exception_withListener done");
    }

    /**
     * Test of invokeAny method.
     */
    @Test
    public void testInvokeAny() {
        debug("invokeAny");
        
        final String classloaderName = "testInvokeAny" + new Date(System.currentTimeMillis());
        ClassloaderContextSetupProvider contextCallback = new ClassloaderContextSetupProvider(classloaderName);
        ArrayList<CallableImpl<String>> tasks = new ArrayList<>();
        CallableImpl<String> goodTask = null;
        String expectedResult = null;
        for (int i=0; i<10; i++) {
            // set up 10 tasks. 9 of which throws leaving one returning good result.
            String result = "result" + new Date(System.currentTimeMillis());        
            CallableImpl<String> task = new CallableImpl<>(result, null);
            tasks.add(task);
            if (i == 5) {
                goodTask = task;
                expectedResult = result;
            } else {
                task.setThrowsException(true);
            }
        }
        ManagedExecutorService instance = 
                createManagedExecutor("testInvokeAny", contextCallback);
        try {
            String result = instance.invokeAny(tasks);
            assertEquals(expectedResult, result);
            goodTask.verifyAfterRun(classloaderName); // verify context is setup for task
        } catch (InterruptedException | ExecutionException ex) {
            fail(ex.toString());
        }
    }
    
    /**
     * Test of invokeAny method where all tasks throw exception
     * 
     */
    @Test(expected = ExecutionException.class)
    public void testInvokeAny_exception() throws Exception {
        debug("invokeAny_exception");
        
        final String classloaderName = "testInvokeAny_exception" + new Date(System.currentTimeMillis());
        ClassloaderContextSetupProvider contextCallback = new ClassloaderContextSetupProvider(classloaderName);
        ArrayList<CallableImpl<String>> tasks = new ArrayList<>();
        for (int i=0; i<10; i++) {
            // set up 10 tasks. 9 of which throws leaving one returning good result.
            String result = "result" + new Date(System.currentTimeMillis());        
            CallableImpl<String> task = new CallableImpl<>(result, null);
            tasks.add(task);
            task.setThrowsException(true);
        }
        ManagedExecutorService instance = 
                createManagedExecutor("testInvokeAny_exception", contextCallback);
        String result = instance.invokeAny(tasks); // expecting ExecutionException
    }
    
    /**
     * Test of invokeAny method with TaskListener
     */
    //@Ignore
    @Test
    public void testInvokeAny_withListener() {
        debug("invokeAny_withListener");
        
        final String classloaderName = "testInvokeAny_withListener" + new Date(System.currentTimeMillis());
        ClassloaderContextSetupProvider contextCallback = new ClassloaderContextSetupProvider(classloaderName);
        ArrayList<CallableImpl<String>> tasks = new ArrayList<>();
        ArrayList<String>results = new ArrayList<>();
        CallableImpl<String> goodTask = null;
        ManagedTaskListenerImpl listenerForGoodTask = null;
        
        String expectedResult = null;
        for (int i=0; i<10; i++) {
            // set up 10 tasks. 9 of which throws leaving one returning good result.
            String result = "result" + new Date(System.currentTimeMillis());        
            ManagedTaskListenerImpl taskListener = new ManagedTaskListenerImpl();
            CallableImpl<String> task = new ManagedCallableTask<>(result, taskListener);
            tasks.add(task);
            if (i == 5) {
                goodTask = task;
                listenerForGoodTask = taskListener;
                expectedResult = result;
            } else {
                task.setThrowsException(true);
            }
        }
        ManagedExecutorService instance = 
                createManagedExecutor("testInovokeAny_withListener", contextCallback);
        try {
            String result = instance.invokeAny(tasks);
            assertEquals(expectedResult, result);
            
            Future<?> future = listenerForGoodTask.findFutureWithResult(result);
            listenerForGoodTask.verifyCallback(ManagedTaskListenerImpl.STARTING, future, instance, null, classloaderName);
            listenerForGoodTask.verifyCallback(ManagedTaskListenerImpl.SUBMITTED, future, instance, null, classloaderName);
            Util.waitForTaskDone(future, listenerForGoodTask, getLoggerName());
            listenerForGoodTask.verifyCallback(ManagedTaskListenerImpl.DONE, future, instance, null);
            assertFalse(listenerForGoodTask.eventCalled(future, ManagedTaskListenerImpl.ABORTED));
            goodTask.verifyAfterRun(classloaderName, false); // verify context is setup for task
        } catch (InterruptedException | ExecutionException ex) {
            fail(ex.toString());
        }
    }

    /**
     * Test of invokeAny method with TaskListener where all tasks throw exception
     * 
     */
    @Test(expected = ExecutionException.class)
    public void testInvokeAny_exception_withListener() throws Exception {
        debug("invokeAny_exception_withListener");
        
        final String classloaderName = "testInvokeAny_exception_withListener" + new Date(System.currentTimeMillis());
        ClassloaderContextSetupProvider contextCallback = new ClassloaderContextSetupProvider(classloaderName);
        ArrayList<CallableImpl<String>> tasks = new ArrayList<>();
        ManagedTaskListenerImpl taskListener = new ManagedTaskListenerImpl();
        for (int i=0; i<10; i++) {
            // set up 10 tasks. 9 of which throws leaving one returning good result.
            String result = "result" + new Date(System.currentTimeMillis());        
            CallableImpl<String> task = new ManagedCallableTask<>(result, taskListener);
            tasks.add(task);
            task.setThrowsException(true);
        }
        ManagedExecutorService instance = 
                createManagedExecutor("testInvokeAny_exception_withListener", contextCallback);
        String result = instance.invokeAny(tasks); // expecting ExecutionException
    }

    /**
     * Test of invokeAll method
     * Verify context callback are called to setup context
     * Verify listener callback methods are called
     */
    @Test
    public void testInvokeAll() {
        debug("invokeAll");
        
        final String classloaderName = "testInvokeAll" + new Date(System.currentTimeMillis());
        ClassloaderContextSetupProvider contextCallback = new ClassloaderContextSetupProvider(classloaderName);
        ArrayList<CallableImpl<String>> tasks = new ArrayList<>();
        ArrayList<String>results = new ArrayList<>();
        for (int i=0; i<10; i++) {
            String result = "result" + new Date(System.currentTimeMillis());        
            CallableImpl<String> task = new CallableImpl<>(result, null);
            tasks.add(task);
            results.add(result);
        }
        ManagedExecutorService instance = 
                createManagedExecutor("testInvokeAll", contextCallback);
        try {
            int resultsIndex = 0;
            List<Future<String>> futures = instance.invokeAll(tasks);
            for (Future<String> future : futures) {
                assertTrue(future.isDone());
                assertFalse(future.isCancelled());
                String result = future.get();
                assertEquals(results.get(resultsIndex++), result);
            }
            for (CallableImpl<String> task : tasks) {
                task.verifyAfterRun(classloaderName); // verify context is setup for task
            }
        } catch (InterruptedException | ExecutionException ex) {
            fail(ex.toString());
        }
    }
    
    /**
     * Test of invokeAll method, of class ManagedExecutorServiceImpl.
     * Verify context callback are called to setup context
     * Verify listener callback methods are called
     */
    @Test
    public void testInvokeAll_withListener() {
        debug("invokeAll_withListener");
        
        final String classloaderName = "testInvokeAll_withListener" + new Date(System.currentTimeMillis());
        ClassloaderContextSetupProvider contextCallback = new ClassloaderContextSetupProvider(classloaderName);
        ArrayList<CallableImpl<String>> tasks = new ArrayList<>();
        ArrayList<String>results = new ArrayList<>();
        ManagedTaskListenerImpl taskListener = new ManagedTaskListenerImpl();
        for (int i=0; i<10; i++) {
            String result = "result" + new Date(System.currentTimeMillis());        
            CallableImpl<String> task = new ManagedCallableTask<>(result, taskListener);
            tasks.add(task);
            results.add(result);
        }
        ManagedExecutorService instance = 
                createManagedExecutor("testInovokeAll_withListener", contextCallback);
        try {
            int resultsIndex = 0;
            List<Future<String>> futures = instance.invokeAll(tasks);
            for (Future<String> future : futures) {
                assertTrue(future.isDone());
                assertFalse(future.isCancelled());
                String result = future.get();
                assertEquals(results.get(resultsIndex++), result);
                taskListener.verifyCallback(ManagedTaskListenerImpl.STARTING, future, instance, null, classloaderName);
                taskListener.verifyCallback(ManagedTaskListenerImpl.SUBMITTED, future, instance, null, classloaderName);
                Util.waitForTaskDone(future, taskListener, getLoggerName());
                taskListener.verifyCallback(ManagedTaskListenerImpl.DONE, future, instance, null);
                assertFalse(taskListener.eventCalled(future, ManagedTaskListenerImpl.ABORTED));
            }
            for (CallableImpl<String> task : tasks) {
                task.verifyAfterRun(classloaderName, false); // verify context is setup for task
            }
        } catch (InterruptedException | ExecutionException ex) {
            fail(ex.toString());
        }
    }
    
    /**
     * Test of invokeAll method, of class ManagedExecutorServiceImpl.
     * Verify context callback are called to setup context
     * Verify listener callback methods are called
     */
    @Test
    public void testInvokeAll_exception() {
        debug("invokeAll_exception");
        
        final String classloaderName = "testInvokeAll_exception" + new Date(System.currentTimeMillis());
        ClassloaderContextSetupProvider contextCallback = new ClassloaderContextSetupProvider(classloaderName);
        ArrayList<CallableImpl<String>> tasks = new ArrayList<>();
        ArrayList<String>results = new ArrayList<>();
        for (int i=0; i<10; i++) {
            // Configure 10 tasks. Half of which throws
            String result = "result" + new Date(System.currentTimeMillis());        
            CallableImpl<String> task = new CallableImpl<>(result, null);
            if (i % 2 == 0) {
                task.setThrowsException(true);
            }
            tasks.add(task);
            results.add(result);
        }
        ManagedExecutorService instance = 
                createManagedExecutor("testInovokeAll_exception", contextCallback);
        try {
            int resultsIndex = 0;
            List<Future<String>> futures = instance.invokeAll(tasks);
            for (Future<String> future : futures) {
                assertTrue(future.isDone());
                assertFalse(future.isCancelled());
                try {
                    String result = future.get();
                    if (resultsIndex % 2 == 0) {
                        fail("Expected exception not thrown for results[" + resultsIndex + "] but got " + result);
                    }
                    assertEquals(results.get(resultsIndex), result);
                }
                catch (ExecutionException ex) {
                    if (resultsIndex % 2 != 0) {
                        fail("exception not expected at results[" + resultsIndex + "]");
                    }
                    assertEquals(results.get(resultsIndex), ex.getCause().getMessage());
                }
                resultsIndex++;
            }
            for (CallableImpl<String> task : tasks) {
                task.verifyAfterRun(classloaderName); // verify context is setup for task
            }
        } catch (InterruptedException ex) {
            fail(ex.toString());
        }
    }

    /**
     * Test of invokeAll method, of class ManagedExecutorServiceImpl.
     * Verify context callback are called to setup context
     * Verify listener callback methods are called
     */
    @Test
    public void testInvokeAll_exception_withListener() {
        debug("invokeAll_exception_withListener");
        
        final String classloaderName = "testInvokeAll_exception_withListener" + new Date(System.currentTimeMillis());
        ClassloaderContextSetupProvider contextCallback = new ClassloaderContextSetupProvider(classloaderName);
        ArrayList<CallableImpl<String>> tasks = new ArrayList<>();
        ArrayList<String>results = new ArrayList<>();
        ManagedTaskListenerImpl taskListener = new ManagedTaskListenerImpl();
        for (int i=0; i<10; i++) {
            String result = "result" + new Date(System.currentTimeMillis());        
            CallableImpl<String> task = new ManagedCallableTask<>(result, taskListener);
            if (i % 2 == 0) {
                task.setThrowsException(true);
            }
            tasks.add(task);
            results.add(result);
        }
        ManagedExecutorService instance = 
                createManagedExecutor("testInovokeAll_withListener", contextCallback);
        try {
            int resultsIndex = 0;
            List<Future<String>> futures = instance.invokeAll(tasks);
            for (Future<String> future : futures) {
                debug("resultsIndex is " + resultsIndex);
                assertTrue(future.isDone());
                assertFalse(future.isCancelled());
                try {
                    String result = future.get();
                    if (resultsIndex % 2 == 0) {
                        fail("Expected exception not thrown for results[" + resultsIndex + "] but got " + result);
                    }
                    assertEquals(results.get(resultsIndex), result);
                    taskListener.verifyCallback(ManagedTaskListenerImpl.STARTING, future, instance, null, classloaderName);
                    taskListener.verifyCallback(ManagedTaskListenerImpl.SUBMITTED, future, instance, null, classloaderName);
                    Util.waitForTaskDone(future, taskListener, getLoggerName());
                    taskListener.verifyCallback(ManagedTaskListenerImpl.DONE, future, instance, null);
                }
                catch (ExecutionException ex) {
                    if (resultsIndex % 2 != 0) {
                        fail("exception not expected at results[" + resultsIndex + "]");
                    }
                    taskListener.verifyCallback(ManagedTaskListenerImpl.STARTING, future, instance, null, classloaderName);
                    taskListener.verifyCallback(ManagedTaskListenerImpl.SUBMITTED, future, instance, null, classloaderName);
                    Util.waitForTaskDone(future, taskListener, getLoggerName());
                    taskListener.verifyCallback(ManagedTaskListenerImpl.DONE,
                            future, instance, new Exception(results.get(resultsIndex)));
                }
                assertFalse(taskListener.eventCalled(future, ManagedTaskListenerImpl.ABORTED));
                resultsIndex++;
            }
            for (CallableImpl<String> task : tasks) {
                task.verifyAfterRun(classloaderName, false); // verify context is setup for task
            }
        } catch (InterruptedException ex) {
            fail(ex.toString());
        }
    }

    /**
     * Test of cancel method, of class ManagedExecutorServiceImpl.
     * Verify context callback are called to setup context
     * Verify listener callback methods are called
     */
    @Test
    public void testCancel() {
        debug("cancel");
        
        final String classloaderName = "testCancel" + new Date(System.currentTimeMillis());
        ClassloaderContextSetupProvider contextCallback = new ClassloaderContextSetupProvider(classloaderName);
        RunnableImpl task = new BlockingRunnableImpl(null, 60 * 1000L);
        ManagedExecutorService instance = 
                createManagedExecutor("submit", contextCallback);
        Future future = instance.submit(task);
        assertFalse(future.isDone());
        assertFalse(future.isCancelled());
        
        assertTrue( future.cancel(true) );
        
        assertTrue(future.isDone());
        assertTrue(future.isCancelled());        
    }
    
    /**
     * Test of cancel method, of class ManagedExecutorServiceImpl.
     * Verify context callback are called to setup context
     * Verify listener callback methods are called
     */
    @Test
    public void testCancel_withListener() {
        debug("cancel_withListener");
        
        final String classloaderName = "testCancel" + new Date(System.currentTimeMillis());
        ClassloaderContextSetupProvider contextCallback = new ClassloaderContextSetupProvider(classloaderName);
        ManagedTaskListenerImpl taskListener = new ManagedTaskListenerImpl();
        RunnableImpl task = new ManagedBlockingRunnableTask(taskListener, 60 * 1000L);
        ManagedExecutorService instance = 
                createManagedExecutor("submit", contextCallback);
        Future future = instance.submit(task);
        assertFalse(future.isDone());
        assertFalse(future.isCancelled());
        
        assertTrue( future.cancel(true) );
        
        assertTrue(future.isDone());
        assertTrue(future.isCancelled());        

        assertTrue("timeout waiting for taskAborted call", Util.waitForTaskAborted(future, taskListener, getLoggerName()));
        taskListener.verifyCallback(ManagedTaskListenerImpl.ABORTED, future, instance, 
                new CancellationException(), classloaderName);
    }

    @Test (expected = IllegalStateException.class)
    public void testShutdown() {
        ManagedExecutorService mes = 
                createManagedExecutor("testShutdown", null);
        mes.shutdown();
    }
    
    @Test (expected = IllegalStateException.class)
    public void testIsShutdown() {
        ManagedExecutorService mes = 
                createManagedExecutor("testShutdown", null);
        mes.isShutdown();
    }
    
    @Test (expected = IllegalStateException.class)
    public void testIsShutdownNow() {
        ManagedExecutorService mes = 
                createManagedExecutor("testShutdownNow", null);
        mes.shutdownNow();
    }
    
    @Test (expected = IllegalStateException.class)
    public void testIsTerminated() {
        ManagedExecutorService mes = 
                createManagedExecutor("isTerminated", null);
        mes.isTerminated();
    }
    
    @Test (expected = IllegalStateException.class)
    public void testAwaitTermination() throws InterruptedException {
        ManagedExecutorService mes = 
                createManagedExecutor("awaitTermination", null);
        mes.awaitTermination(10, TimeUnit.MILLISECONDS);
    }
    
    protected ManagedExecutorService createManagedExecutor(String name, 
            ContextSetupProvider contextSetupProvider) {
        ManagedExecutorServiceImpl mes = 
                new ManagedExecutorServiceImpl(name, null, 0, false,
                    1, 1,  
                    0, TimeUnit.SECONDS, 
                    0, new TestContextService(contextSetupProvider), 
                    RejectPolicy.ABORT,
                    RunLocation.LOCAL,
                    true);
        return mes.getAdapter();
    }
    
    final static boolean DEBUG = false;
    
    protected void debug(String msg) {
        if (DEBUG) {
            System.out.println(msg);
        }
    }
}
