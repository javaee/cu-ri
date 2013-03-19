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

import javax.enterprise.concurrent.ManageableThread;

/**
 * Abstract base class for threads to be returned by 
 * {@link ManagedThreadFactoryImpl#createThread(java.lang.Runnable, org.glassfish.enterprise.concurrent.spi.ContextHandle) }
 * @author alai
 */
public abstract class AbstractManagedThread extends Thread implements ManageableThread {
    volatile boolean shutdown = false;
    final long threadStartTime = System.currentTimeMillis();

    public AbstractManagedThread(Runnable target) {
        super(target);
    }

    @Override
    public boolean isShutdown() {
        return shutdown;
    }

    /**
     * Marks the thread for shutdown so application components could
     * check the status of this thread and finish any work as soon
     * as possible.
     */
    public void shutdown() {
        shutdown = true;
    }

    abstract boolean cancelTask();

    /**
     * Return the identity name of the task that is being run on this thread.
     * 
     * @return The identity name of the task that is being run on this thread, or
     * "null" if there is none.
     */
    abstract public String getTaskIdentityName();

    /**
     * Return the time in millisecond since the task has started.
     * 
     * @param now The current time in milliseconds, which is typically obtained
     *            by calling {@link System#currentTimeMillis() }
     * 
     * @return The time since the task has started in milliseconds.
     */
    abstract public long getTaskRunTime(long now);

    /**
     * Return the time that the thread was started, measured in milliseconds, 
     * between the current time and midnight, January 1, 1970 UTC.
     * 
     * @return The time that the thread was started, in milliseconds. 
     */
    public abstract long getThreadStartTime();

    abstract boolean isTaskHung(long now);

}
