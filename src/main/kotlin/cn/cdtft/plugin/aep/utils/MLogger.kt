package cn.cdtft.plugin.aep.utils

import com.intellij.openapi.diagnostic.Logger
import org.apache.log4j.Level
import org.jetbrains.annotations.NonNls

/**
 * Created by likfe on 2018/3/2.
 */
class MLogger : Logger() {
    override fun isDebugEnabled(): Boolean {
        return MLog.DEBUG
    }

    override fun debug(s: @NonNls String?) {
        System.err.println("ERROR: " + s)
    }

    override fun debug(throwable: Throwable?) {
    }

    override fun debug(s: @NonNls String?, throwable: Throwable?) {
    }

    override fun info(s: @NonNls String?) {
    }

    override fun info(s: @NonNls String?, throwable: Throwable?) {
    }

    override fun warn(s: @NonNls String?, throwable: Throwable?) {
    }

    override fun error(s: @NonNls String?, throwable: Throwable?, vararg strings: String) {
    }

    override fun setLevel(level: Level) {
    }

    override fun isTraceEnabled(): Boolean {
        return MLog.DEBUG
    }

    companion object {
        fun getInstance(s: String): MLogger {
            val logger = MLogger()
            logger.setLevel(Level.ALL)
            return logger
        }
    }
}
