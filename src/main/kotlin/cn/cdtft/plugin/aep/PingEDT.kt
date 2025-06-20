package cn.cdtft.plugin.aep

import com.intellij.openapi.util.Condition
import org.jetbrains.annotations.NonNls
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.SwingUtilities
import kotlin.concurrent.Volatile

class PingEDT(
    private val myName: @NonNls String, private val myShutUpCondition: Condition<*>,
    private val myMaxUnitOfWorkThresholdMs: Int, private val pingAction: Runnable
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
            var processed = 0
            while (true) {
                if (processNext()) {
                    processed++
                } else {
                    break
                }
                val finish = System.currentTimeMillis()
                if (myMaxUnitOfWorkThresholdMs != -1 && finish - start > myMaxUnitOfWorkThresholdMs) break
            }
            if (!this@PingEDT.isEmpty) {
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

    // returns true if invokeLater was called
    fun ping(): Boolean {
        pinged = true
        return scheduleUpdate()
    }

    // returns true if invokeLater was called
    private fun scheduleUpdate(): Boolean {
        if (!stopped && invokeLaterScheduled.compareAndSet(false, true)) {
            SwingUtilities.invokeLater(myUpdateRunnable)
            return true
        }
        return false
    }

    fun stop() {
        stopped = true
    }
}
