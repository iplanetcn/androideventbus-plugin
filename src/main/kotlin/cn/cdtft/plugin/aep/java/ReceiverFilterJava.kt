package cn.cdtft.plugin.aep.java

import cn.cdtft.plugin.aep.Filter
import cn.cdtft.plugin.aep.PsiUtils
import com.intellij.psi.*
import com.intellij.usages.Usage
import com.intellij.usages.UsageInfo2UsageAdapter

class ReceiverFilterJava : Filter {
    override fun shouldShow(usage: Usage): Boolean {
        var element = (usage as UsageInfo2UsageAdapter).element
        if (element is PsiJavaCodeReferenceElement) {
            if ((element.parent.also { element = it }) is PsiTypeElement) {
                if ((element!!.parent.also { element = it }) is PsiParameter) {
                    if ((element!!.parent.also { element = it }) is PsiParameterList) {
                        if ((element!!.parent.also { element = it }) is PsiMethod) {
                            val method = element as PsiMethod
                            if (PsiUtils.isEventBusReceiver(method)) {
                                return true
                            }
                        }
                    }
                }
            }
        }
        return false
    }
}
