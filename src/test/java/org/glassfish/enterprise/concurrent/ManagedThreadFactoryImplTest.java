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

package org.glassfish.enterprise.concurrent;

import javax.enterprise.concurrent.ManageableThread;
import org.glassfish.enterprise.concurrent.spi.ContextSetupProvider;
import org.glassfish.enterprise.concurrent.test.ClassloaderContextSetupProvider;
import org.glassfish.enterprise.concurrent.test.RunnableImpl;
import org.glassfish.enterprise.concurrent.test.TestContextService;
import org.glassfish.enterprise.concurrent.test.Util;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class ManagedThreadFactoryImplTest {

    @Test
    public void testNewThread_default() throws Exception {
        ManagedThreadFactoryImpl factory = new ManagedThreadFactoryImpl("test1");
        TestRunnable r = new TestRunnable();
        Thread newThread = factory.newThread(r);
        verifyThreadProperties(newThread, true, Thread.NORM_PRIORITY);
        newThread.start();
        newThread.join();
        assertFalse(r.isInterrupted);
    }

    @Test
    public void testNewThread_priority_daemon() throws Exception {
        final int PRIORITY = 7;
        ContextSetupProvider callback = new ClassloaderContextSetupProvider("ManagedThreadFactoryImplTest");
        ContextServiceImpl contextService = new TestContextService(callback);
        ManagedThreadFactoryImpl factory = new ManagedThreadFactoryImpl("test1", contextService, PRIORITY);
        Runnable r = new RunnableImpl(null);
        Thread newThread = factory.newThread(r);
        verifyThreadProperties(newThread, true, PRIORITY);

        ManagedThreadFactoryImpl factory2 = new ManagedThreadFactoryImpl("test1", contextService, Thread.MIN_PRIORITY);
        newThread = factory2.newThread(r);
        verifyThreadProperties(newThread, true, Thread.MIN_PRIORITY);
    }

    @Test
    public void testNewThread_context() throws Exception {
        final String CLASSLOADER_NAME = "ManagedThreadFactoryImplTest:" + new java.util.Date(System.currentTimeMillis());
        ContextSetupProvider contextSetupProvider = new ClassloaderContextSetupProvider(CLASSLOADER_NAME);
        ContextServiceImpl contextService = new TestContextService(contextSetupProvider);
        ManagedThreadFactoryImpl factory = new ManagedThreadFactoryImpl("test1", contextService);
        RunnableImpl r = new RunnableImpl(null);
        Thread newThread = factory.newThread(r);
        newThread.start();
        Util.waitForTaskComplete(r, getLoggerName());
        r.verifyAfterRun(CLASSLOADER_NAME);
    }

    @Test (expected = IllegalStateException.class)
    public void testNewThread_shutdown() throws Exception {
        ManagedThreadFactoryImpl factory = new ManagedThreadFactoryImpl("testNewThread_shutdown");
        Runnable r = new RunnableImpl(null);
        factory.stop();
        Thread newThread = factory.newThread(r);
    }

    @Test
    public void testNewThread_start_aftershutdown() throws Exception {
        ManagedThreadFactoryImpl factory = new ManagedThreadFactoryImpl("testNewThread_start_aftershutdown");
        TestRunnable r = new TestRunnable();
        Thread newThread = factory.newThread(r);
        assertFalse(((ManageableThread)newThread).isShutdown());
        factory.stop();
        assertTrue(((ManageableThread)newThread).isShutdown());
        newThread.start();
        newThread.join();
        assertTrue(r.isInterrupted);
    }

    private void verifyThreadProperties(Thread thread, boolean isDaemon, int priority) {
        assertEquals(isDaemon, thread.isDaemon());
        assertEquals(priority, thread.getPriority());
    }

    private String getLoggerName() {
        return ManagedThreadFactoryImplTest.class.getName();
    }
    
    static class TestRunnable implements Runnable {
        boolean isInterrupted = false;
        
        public void run() {
          isInterrupted = Thread.currentThread().isInterrupted();  
        }
    }
}
