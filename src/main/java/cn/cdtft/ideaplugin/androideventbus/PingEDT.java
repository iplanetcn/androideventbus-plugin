/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cn.cdtft.ideaplugin.androideventbus;

import com.intellij.openapi.util.Condition;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * PingEDT
 *
 * @author john
 * @since 2019-04-13
 */
class PingEDT {
    private final Runnable pingAction;
    private volatile boolean stopped;
    private volatile boolean pinged;
    private final Condition<?> myShutUpCondition;
    /** -1 means indefinite */
    private final int myMaxUnitOfWorkThresholdMs;

    private final AtomicBoolean invokeLaterScheduled = new AtomicBoolean();
    private final Runnable myUpdateRunnable = new Runnable() {
        @Override
        public void run() {
            boolean b = invokeLaterScheduled.compareAndSet(true, false);
            assert b;
            if (stopped || myShutUpCondition.value(null)) {
                stop();
                return;
            }
            long start = System.currentTimeMillis();
            while (true) {
                if (!processNext()) {
                    break;
                }
                long finish = System.currentTimeMillis();
                if (myMaxUnitOfWorkThresholdMs != -1 && finish - start > myMaxUnitOfWorkThresholdMs) {
                    break;
                }
            }
            if (!isEmpty()) {
                scheduleUpdate();
            }
        }
    };

    public PingEDT(@NotNull Condition<?> shutUpCondition,
                   int maxUnitOfWorkThresholdMs, @NotNull Runnable pingAction) {
        myShutUpCondition = shutUpCondition;
        myMaxUnitOfWorkThresholdMs = maxUnitOfWorkThresholdMs;
        this.pingAction = pingAction;
    }

    private boolean isEmpty() {
        return !pinged;
    }

    private boolean processNext() {
        pinged = false;
        pingAction.run();
        return pinged;
    }

    /** returns true if invokeLater was called */
    void ping() {
        pinged = true;
        scheduleUpdate();
    }

    /** returns true if invokeLater was called */
    private void scheduleUpdate() {
        if (!stopped && invokeLaterScheduled.compareAndSet(false, true)) {
            SwingUtilities.invokeLater(myUpdateRunnable);
        }
    }

    private void stop() {
        stopped = true;
    }
}
