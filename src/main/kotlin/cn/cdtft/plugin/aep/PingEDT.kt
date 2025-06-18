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
package cn.cdtft.plugin.aep

import com.intellij.openapi.util.Condition
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.SwingUtilities
import kotlin.concurrent.Volatile

/**
 * PingEDT
 *
 * @author john
 * @since 2019-04-13
 */
internal class PingEDT(
    private val myShutUpCondition: Condition<*>,
    private val myMaxUnitOfWorkThresholdMs: Int, 
    private val pingAction: Runnable
) {
    @Volatile
    private var stopped = false

    @Volatile
    private var pinged = false

    private val invokeLaterScheduled = AtomicBoolean()
    private val myUpdateRunnable: Runnable = object : Runnable {
        override fun run() {
            val b = invokeLaterScheduled.compareAndSet(true, false)
            assert(b)
            if (stopped || myShutUpCondition.value(null)) {
                stop()
                return
            }
            val start = System.currentTimeMillis()
            while (true) {
                if (!processNext()) {
                    break
                }
                val finish = System.currentTimeMillis()
                if (myMaxUnitOfWorkThresholdMs != -1 && finish - start > myMaxUnitOfWorkThresholdMs) {
                    break
                }
            }
            if (!isEmpty) {
                scheduleUpdate()
            }
        }
    }

    private val isEmpty: Boolean
        get() = !pinged

    private fun processNext(): Boolean {
        pinged = false
        pingAction.run()
        return pinged
    }

    /** returns true if invokeLater was called  */
    fun ping() {
        pinged = true
        scheduleUpdate()
    }

    /** returns true if invokeLater was called  */
    private fun scheduleUpdate() {
        if (!stopped && invokeLaterScheduled.compareAndSet(false, true)) {
            SwingUtilities.invokeLater(myUpdateRunnable)
        }
    }

    private fun stop() {
        stopped = true
    }
}
