package cn.cdtft.plugin.aep

import com.intellij.usages.Usage
import com.intellij.usages.UsageInfo2UsageAdapter
import java.util.*

/**
 * ReceiverFilter
 *
 * @author john
 * @since 2019-04-13
 */
class ReceiverFilter internal constructor(tagPsiExpression: PsiExpression) : Filter {
    private val mTagPsiExpression: PsiExpression

    init {
        mTagPsiExpression = tagPsiExpression
    }

    override fun shouldShow(usage: Usage): Boolean {
        var element = (usage as UsageInfo2UsageAdapter).getElement()
        if (element is PsiJavaCodeReferenceElement) {
            if ((element.getParent().also { element = it }) is PsiTypeElement) {
                if ((element!!.getParent().also { element = it }) is PsiParameter) {
                    if ((element!!.getParent().also { element = it }) is PsiParameterList) {
                        if ((element!!.getParent().also { element = it }) is PsiMethod) {
                            val method: PsiMethod = element as PsiMethod
                            val modifierList: PsiModifierList = method.getModifierList()
                            for (psiAnnotation in modifierList.getAnnotations()) {
                                if (psiAnnotation.getQualifiedName() == "org.simple.eventbus.Subscriber") {
                                    val tag: PsiAnnotationMemberValue? = psiAnnotation.findAttributeValue("tag")
                                    return Objects.requireNonNull<Any?>(tag).getLastChild().getText()
                                        .equals(mTagPsiExpression.getLastChild().getText())
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
