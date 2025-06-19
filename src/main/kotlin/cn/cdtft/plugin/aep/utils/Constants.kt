package cn.cdtft.plugin.aep.utils

import cn.cdtft.plugin.aep.ShowUsagesAction
import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

/**
 * Created by likfe on 2018/3/6.
 */
object Constants {
    const val IS_DEBUG: Boolean = true

    const val ICON_PATH: String = "/icons/link.svg"
    val ICON: Icon = IconLoader.getIcon(ICON_PATH, ShowUsagesAction::class.java)
    const val MAX_USAGES: Int = 100
    const val FUN_START: String = "EventBus.getDefault()"
    const val FUN_NAME: String = "post"
    const val FUN_NAME2: String = "postSticky"
    const val FUN_ANNOTATION: String = "org.simple.eventbus.Subscribe"
    const val FUN_ANNOTATION_KT: String = "Subscribe"
    const val FUN_EVENT_CLASS: String = "org.simple.eventbus.EventBus"
    const val FUN_EVENT_CLASS_NAME: String = "EventBus"
}
