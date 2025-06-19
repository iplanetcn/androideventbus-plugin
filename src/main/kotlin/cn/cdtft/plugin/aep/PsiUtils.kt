package cn.cdtft.plugin.aep

import com.intellij.psi.PsiAnnotationMemberValue
import com.intellij.psi.PsiCallExpression
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifierList
import com.intellij.psi.PsiParameter
import com.intellij.psi.PsiType

/**
 * PsiUtils
 *
 * @author john
 * @since 2019-04-13
 */
internal object PsiUtils {
    fun getClass(psiType: PsiType?): PsiClass? {
        if (psiType is PsiClassType) {
            return psiType.resolve()
        }
        return null
    }

    /**
     * 判断是否为EventBus接收器
     */
    fun isEventBusReceiver(psiElement: PsiElement?): Boolean {
        if (psiElement is PsiMethod) {
            val method: PsiMethod = psiElement
            val modifierList: PsiModifierList = method.modifierList
            for (psiAnnotation in modifierList.annotations) {
                if (psiAnnotation.qualifiedName == "org.simple.eventbus.Subscriber") {
                    val tag: PsiAnnotationMemberValue? = psiAnnotation.findAttributeValue("tag")
                    if (tag != null) {
                        return true
                    }
                }
            }
        }
        return false
    }

    /**
     * 判断是否为EventBus发送器
     */
    fun isEventBusPost(psiElement: PsiElement?): Boolean {
        if (psiElement is PsiCallExpression) {
            val callExpression: PsiCallExpression = psiElement
            val method: PsiMethod? = callExpression.resolveMethod()
            if (method != null) {
                val name: String? = method.name
                val parent: PsiElement? = method.parent
                if ("post" == name && parent is PsiClass) {
                    val postParameters: Array<PsiParameter> = method.parameterList.parameters
                    for (param in postParameters) {
                        if (param.name == "tag") {
                            val implClass: PsiClass = parent
                            return isEventBusClass(implClass) || isSuperClassEventBus(implClass)
                        }
                    }
                }
            }
        }
        return false
    }

    /**
     * 判断是否为EventBus类
     */
    private fun isEventBusClass(psiClass: PsiClass): Boolean {
        return "EventBus" == psiClass.name
    }

    /**
     * 判断是否为EventBus的超类
     */
    private fun isSuperClassEventBus(psiClass: PsiClass): Boolean {
        val supers: Array<PsiClass> = psiClass.supers
        if (supers.isEmpty()) {
            return false
        }
        for (superClass in supers) {
            try {
                if ("EventBus" == superClass.name) {
                    return true
                }
            } catch (e: Exception) {
                println(e.toString())
            }
        }
        return false
    }
}
