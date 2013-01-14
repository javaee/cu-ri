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

import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import javax.enterprise.concurrent.ManagedExecutorService;
import javax.enterprise.concurrent.ManagedTaskListener;
import static junit.framework.Assert.*;

public class ManagedTaskListenerImpl implements ManagedTaskListener {

    public static final String SUBMITTED = "taskSubmitted", STARTING = "taskStarting",
            DONE = "taskDone", ABORTED = "taskAborted";
    private ConcurrentHashMap<Future, HashMap<String, CallbackParameters>> callParameters = 
            new ConcurrentHashMap<Future, HashMap<String, CallbackParameters>>();
    volatile Future<?> startingFuture = null, submittedFuture = null,
            abortedFuture = null, doneFuture = null;
        
    @Override
    public void taskSubmitted(Future<?> future, ManagedExecutorService executor) {
        update(SUBMITTED, future, executor, null);
        submittedFuture = future;
    }

    @Override
    public void taskAborted(Future<?> future, ManagedExecutorService executor, Throwable exception) {
        update(ABORTED, future, executor, exception);
        abortedFuture = future;
    }

    @Override
    public void taskDone(Future<?> future, ManagedExecutorService executor, Throwable exception) {
        update(DONE, future, executor, exception);
        doneFuture = future;
    }

    @Override
    public void taskStarting(Future<?> future, ManagedExecutorService executor) {
        update(STARTING, future, executor, null);
        startingFuture = future;
    }
    
    public boolean eventCalled(Future<?> future, String event) {
        HashMap<String, CallbackParameters> map = callParameters.get(future);
        if (map != null) {
            return map.containsKey(event);
        }
        return false;
    }
    
    private void update (String event,
            Future<?> future,
            ManagedExecutorService executor,
            Throwable exception) {
        HashMap<String, CallbackParameters> map = callParameters.get(future);
        if (map == null) {
            map = new HashMap<String, CallbackParameters>();
            callParameters.put(future, map);
        }
        CallbackParameters params = map.get(event);
        if (params != null) {
            params.incrementCount();
        }
        else {
            params = new CallbackParameters(executor, exception);
            map.put(event, params);
        }
    }
    
    /*package*/ CallbackParameters find(Future<?> future, String event) {
        CallbackParameters result = null;
        if (future == null) {
            System.out.println("future is null");
        }
        HashMap<String, CallbackParameters> map = callParameters.get(future);
        if (map != null) {
            result = map.get(event);
        }
        return result;
    }

    public int getCount(Future<?> future, String event) {
        int result = 0;
        CallbackParameters callbackParams = find(future, event);
        if (callbackParams != null) {
            result = callbackParams.getCount();
        }
        return result;
    }

    public Future<?> findFutureWithResult(String result) {
        Set<Future> allFutures = callParameters.keySet();
        for (Future f: allFutures) {
            try {
                if (result.equals(f.get())) {
                    return f;
                }
            } catch (InterruptedException ex) {
                // ignore
            } catch (ExecutionException ex) {
                // ignore
            } catch (CancellationException ex) {
                // ignore
            }
        }
        return null;
    }

    public void verifyCallback(String event, Future<?> future, ManagedExecutorService executor, Throwable exception) {
        verifyCallback(event, future, executor, exception, null);
    }
    
    public void verifyCallback(String event, Future<?> future, ManagedExecutorService executor, Throwable exception, 
            String classloaderName) {
        CallbackParameters result = find(future, event);
        assertNotNull("Callback: '" + event + "' not called", result);
        assertEquals(executor, result.getExecutor());
        if (exception == null) {
            assertNull(result.getException());
        }
        else {
            assertNotNull ("expecting exception " + exception + " but none is found", result.getException());
            assertEquals(exception.getMessage(), result.getException().getMessage());
            assertEquals(exception.getClass(), result.getException().getClass());
        }
        if (classloaderName != null) {
            ClassLoader classLoader = result.getClassLoader();
            assertTrue("expecting NamedClassLoader but was: " + classLoader, classLoader instanceof NamedClassLoader);
            assertEquals(classloaderName, ((NamedClassLoader)classLoader).getName());
        }
    }
    
    /*package*/ static class CallbackParameters {
        private ManagedExecutorService executor;
        private Throwable exception;
        private ClassLoader classLoader;
        private int count;

        public CallbackParameters(ManagedExecutorService executor, Throwable exception) {
            this.executor = executor;
            this.exception = exception;
            this.classLoader = Thread.currentThread().getContextClassLoader();
            this.count = 1;
        }

        public ClassLoader getClassLoader() {
            return classLoader;
        }

        public void setClassLoader(ClassLoader classLoader) {
            this.classLoader = classLoader;
        }

        public int getCount() {
            return count;
        }

        public void incrementCount() {
            this.count++;
        }

        public Throwable getException() {
            return exception;
        }

        public void setException(Throwable exception) {
            this.exception = exception;
        }

        public ManagedExecutorService getExecutor() {
            return executor;
        }

        public void setExecutor(ManagedExecutorService executor) {
            this.executor = executor;
        }
        
    }
}
