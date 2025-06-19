package cn.cdtft.plugin.aep.java

import cn.cdtft.plugin.aep.Filter
import cn.cdtft.plugin.aep.PsiUtils
import com.intellij.psi.*
import com.intellij.usages.Usage
import com.intellij.usages.UsageInfo2UsageAdapter

/**
 * Created by kgmyshin on 2015/06/07.
 */
class ReceiverFilterJava : Filter {
    override fun shouldShow(usage: Usage): Boolean {
        var element = (usage as UsageInfo2UsageAdapter).getElement()
        if (element is PsiJavaCodeReferenceElement) {
            if ((element.getParent().also { element = it }) is PsiTypeElement) {
                if ((element!!.getParent().also { element = it }) is PsiParameter) {
                    if ((element!!.getParent().also { element = it }) is PsiParameterList) {
                        if ((element!!.getParent().also { element = it }) is PsiMethod) {
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
