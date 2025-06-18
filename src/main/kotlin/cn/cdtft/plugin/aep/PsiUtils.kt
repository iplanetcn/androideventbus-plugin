package cn.cdtft.plugin.aep

import com.intellij.psi.PsiElement
import java.util.*

/**
 * PsiUtils
 *
 * @author john
 * @since 2019-04-13
 */
internal object PsiUtils {
    fun getClass(psiType: PsiType): PsiClass? {
        if (psiType is PsiClassType) {
            return (psiType as PsiClassType).resolve()
        }
        return null
    }

    /**
     * 判断是否为EventBus接收器
     */
    fun isEventBusReceiver(psiElement: PsiElement?): Boolean {
        if (psiElement is PsiMethod) {
            val method: PsiMethod = psiElement as PsiMethod
            val modifierList: PsiModifierList = method.getModifierList()
            for (psiAnnotation in modifierList.getAnnotations()) {
                if (psiAnnotation.getQualifiedName() == "org.simple.eventbus.Subscriber") {
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
            val callExpression: PsiCallExpression = psiElement as PsiCallExpression
            val method: PsiMethod? = callExpression.resolveMethod()
            if (method != null) {
                val name: String? = method.getName()
                val parent: PsiElement? = method.getParent()
                if ("post" == name && parent is PsiClass) {
                    val postParameters: Array<PsiParameter> = method.getParameterList().getParameters()
                    for (param in postParameters) {
                        if (param.getName() == "tag") {
                            val implClass: PsiClass = parent as PsiClass
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
        try {
            return "EventBus" == Objects.requireNonNull<T?>(psiClass.getName())
        } catch (e: Exception) {
            return false
        }
    }

    /**
     * 判断是否为EventBus的超类
     */
    private fun isSuperClassEventBus(psiClass: PsiClass): Boolean {
        val supers: Array<PsiClass> = psiClass.getSupers()
        if (supers.size == 0) {
            return false
        }
        for (superClass in supers) {
            try {
                if ("EventBus" == superClass.getName()) {
                    return true
                }
            } catch (e: Exception) {
                println(e.toString())
            }
        }
        return false
    }
}
