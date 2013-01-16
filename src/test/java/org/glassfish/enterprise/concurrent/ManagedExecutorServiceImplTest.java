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

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.enterprise.concurrent.ManagedExecutorService;
import org.glassfish.enterprise.concurrent.AbstractManagedExecutorService.RejectPolicy;
import org.glassfish.enterprise.concurrent.AbstractManagedExecutorService.RunLocation;
import org.glassfish.enterprise.concurrent.spi.ContextSetupProvider;
import org.glassfish.enterprise.concurrent.test.BlockingRunnableImpl;
import org.glassfish.enterprise.concurrent.test.RunnableImpl;
import org.glassfish.enterprise.concurrent.test.TestContextService;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
    
    protected ManagedExecutorService createManagedExecutor(String name, 
            ContextSetupProvider contextCallback) {
        return new ManagedExecutorServiceImpl(name, null, 0, false,
                1, 1,  
                0, TimeUnit.SECONDS, 
                0, new TestContextService(contextCallback), 
                RejectPolicy.ABORT,
                RunLocation.LOCAL,
                true);
    }
}
