package cn.cdtft.plugin.aep

import com.intellij.psi.PsiAnnotationMemberValue
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiJavaCodeReferenceElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifierList
import com.intellij.psi.PsiParameter
import com.intellij.psi.PsiParameterList
import com.intellij.psi.PsiTypeElement
import com.intellij.usages.Usage
import com.intellij.usages.UsageInfo2UsageAdapter
/**
 * ReceiverFilter
 *
 * @author john
 * @since 2019-04-13
 */
class ReceiverFilter internal constructor(tagPsiExpression: PsiExpression) : Filter {
    private val mTagPsiExpression: PsiExpression = tagPsiExpression

    override fun shouldShow(usage: Usage): Boolean {
        var element = (usage as UsageInfo2UsageAdapter).element
        if (element is PsiJavaCodeReferenceElement) {
            if ((element.parent.also { element = it }) is PsiTypeElement) {
                if ((element!!.parent.also { element = it }) is PsiParameter) {
                    if ((element!!.parent.also { element = it }) is PsiParameterList) {
                        if ((element!!.parent.also { element = it }) is PsiMethod) {
                            val method: PsiMethod = element as PsiMethod
                            val modifierList: PsiModifierList = method.modifierList
                            for (psiAnnotation in modifierList.annotations) {
                                if (psiAnnotation.qualifiedName == "org.simple.eventbus.Subscriber") {
                                    val tag: PsiAnnotationMemberValue? = psiAnnotation.findAttributeValue("tag")
                                    return tag != null && tag.lastChild.text.equals(mTagPsiExpression.lastChild.text)
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
