package cn.cdtft.plugin.aep.java

import cn.cdtft.plugin.aep.Filter
import cn.cdtft.plugin.aep.utils.Constants
import com.intellij.psi.*
import com.intellij.usages.Usage
import com.intellij.usages.UsageInfo2UsageAdapter


class ReceiverFilterJava(private val mTagPsiExpression: PsiExpression) : Filter {
    override fun shouldShow(usage: Usage): Boolean {
        var element = (usage as UsageInfo2UsageAdapter).element
        if (element is PsiJavaCodeReferenceElement) {
            if ((element.parent.also { element = it }) is PsiTypeElement) {
                if ((element!!.parent.also { element = it }) is PsiParameter) {
                    if ((element!!.parent.also { element = it }) is PsiParameterList) {
                        if ((element!!.parent.also { element = it }) is PsiMethod) {
                            val method = element as PsiMethod
                            val modifierList = method.modifierList
                            for (psiAnnotation in modifierList.annotations) {
                                if (safeEquals(psiAnnotation.qualifiedName, Constants.FUN_ANNOTATION)) {
                                    val tag = psiAnnotation.findAttributeValue(Constants.FUN_ANNOTATION_TAG)
                                    return safeEquals(tag?.lastChild?.text, mTagPsiExpression.lastChild.text)
                                }
                            }
                        }
                    }
                }
            }
        }
        return false
    }
}


private fun safeEquals(obj: String?, value: String?): Boolean {
    return obj != null && obj == value
}