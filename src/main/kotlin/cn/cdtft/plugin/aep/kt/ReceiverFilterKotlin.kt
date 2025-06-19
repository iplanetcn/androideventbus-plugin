package cn.cdtft.plugin.aep.kt

import cn.cdtft.plugin.aep.Filter
import cn.cdtft.plugin.aep.PsiUtils
import cn.cdtft.plugin.aep.utils.MLog
import com.intellij.usages.Usage
import com.intellij.usages.UsageInfo2UsageAdapter

/**
 * Created by kgmyshin on 2015/06/07.
 */
class ReceiverFilterKotlin : Filter {
    override fun shouldShow(usage: Usage): Boolean {
        val element = (usage as UsageInfo2UsageAdapter).element!!
        MLog.debug("ReceiverFilterKotlin 0 " + PsiUtils.isKotlin(element))
        MLog.debug("ReceiverFilterKotlin 0 $element")
        if (PsiUtils.isEventBusReceiver(element)) {
            MLog.debug("ReceiverFilterKotlin 1 isEventBusReceiver")
            return true
        }
        return false
    }
}
