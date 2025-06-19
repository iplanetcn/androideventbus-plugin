package cn.cdtft.plugin.aep.utils

import com.intellij.ide.plugins.PluginManagerCore.logger
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.diagnostic.Logger

/**
 * Created by likfe on 2018/3/1.
 */
object MLog {
    private const val TAG = "eb3"
    const val DEBUG: Boolean = true
    private val LOG = Logger.getInstance(MLog::class.java)


    fun debug(s: String) {
        LOG.info(s)
        logger.debug(s)
        log(s)
    }

    fun debug(s: Throwable) {
        LOG.info(s)
        logger.debug(s)
        log(s.toString())
    }


    fun debug(s: String, vararg more: Any?) {
        logger.debug(s, *more)
        val sb = StringBuilder(s)
        for (obj in more) {
            sb.append(obj)
        }

        log(sb.toString())
        LOG.info(sb.toString())
    }

    private fun log(s: String) {
        val notification = Notification(TAG, "debug", s, NotificationType.INFORMATION)

        //Notifications.Bus.notify(notification);
        Logger.getInstance("#ddd").debug(s)
    }
}
