package cn.cdtft.plugin.aep

import cn.cdtft.plugin.aep.utils.Constants
import cn.cdtft.plugin.aep.utils.MLog
import com.intellij.ide.plugins.PluginManager
import com.intellij.lang.Language
import com.intellij.openapi.extensions.PluginId
import com.intellij.psi.*
import com.intellij.psi.impl.source.tree.java.PsiIdentifierImpl
import com.intellij.psi.impl.source.tree.java.PsiMethodCallExpressionImpl
import com.intellij.psi.impl.source.tree.java.PsiReferenceExpressionImpl
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction

/**
 * modify by likfe ( https://github.com/likfe/ ) on 2018/03/05.
 */
object PsiUtils {
    fun getClass(psiType: PsiType?): PsiClass? {
        if (psiType is PsiClassType) {
            return psiType.resolve()
        }
        return null
    }

    fun isEventBusReceiver(psiElement: PsiElement): Boolean {
        if (psiElement.language.`is`(Language.findLanguageByID("JAVA"))) {
            if (psiElement is PsiMethod) {
                val method = psiElement
                val modifierList = method.modifierList
                for (psiAnnotation in modifierList.annotations) {
                    if (safeEquals(psiAnnotation.qualifiedName, Constants.FUN_ANNOTATION)) {
                        return true
                    }
                }
            }
        } else if (psiElement.language.`is`(Language.findLanguageByID("kotlin"))) {
            if (psiElement is KtNamedFunction) {
                val function = psiElement
                val modifierList = function.modifierList
                if (modifierList != null) {
                    for (annotationEntry in modifierList.annotationEntries) {
                        val calleeExpression = annotationEntry.calleeExpression
                        if (calleeExpression != null && safeEquals(
                                calleeExpression.text,
                                Constants.FUN_ANNOTATION_KT
                            )
                        ) {
                            return true
                        }
                    }
                }
            }
        }
        return false
    }

    fun isEventBusPost(psiElement: PsiElement): Boolean {
        if (psiElement.language.`is`(Language.findLanguageByID("JAVA"))) {
            if (psiElement is PsiMethodCallExpressionImpl && psiElement.firstChild != null && psiElement.firstChild is PsiReferenceExpressionImpl) {
                val all = psiElement.firstChild as PsiReferenceExpressionImpl
                if (all.firstChild is PsiMethodCallExpressionImpl && all.lastChild is PsiIdentifierImpl) {
                    val start = all.firstChild as PsiMethodCallExpressionImpl
                    val post = all.lastChild as PsiIdentifierImpl
                    if ((safeEquals(post.text, Constants.FUN_NAME) || safeEquals(
                            post.text,
                            Constants.FUN_NAME2
                        )) && safeEquals(start.text, Constants.FUN_START)
                    ) {
                        return true
                    }
                }
            }

            //xx.post()/xx.postSticky()，存在误判的可能
            if (psiElement is PsiCallExpression) {
                val callExpression = psiElement
                val method = callExpression.resolveMethod()
                if (method != null) {
                    val name = method.name
                    val parent = method.parent
                    if ((safeEquals(Constants.FUN_NAME, name) || safeEquals(
                            Constants.FUN_NAME2,
                            name
                        )) && parent is PsiClass
                    ) {
                        val implClass = parent
                        return isEventBusClass(implClass) || isSuperClassEventBus(implClass)
                    }
                }
            }
        } else if (psiElement.language.`is`(Language.findLanguageByID("kotlin"))) {
            if (psiElement is KtDotQualifiedExpression) {
                val all = psiElement
                if (all.firstChild is KtDotQualifiedExpression && all.lastChild is KtCallExpression) {
                    val start = all.firstChild.text
                    if (start != null && start == Constants.FUN_START) {
                        val postRoot = all.lastChild as KtCallExpression
                        if (postRoot.firstChild is KtNameReferenceExpression) {
                            val referenceExpression = postRoot.firstChild as KtNameReferenceExpression
                            if (referenceExpression.getReferencedName() == Constants.FUN_NAME) {
                                return true
                            }
                        }
                    }
                }
            }
        }
        return false
    }

    private fun isEventBusClass(psiClass: PsiClass): Boolean {
        return safeEquals(psiClass.name, Constants.FUN_EVENT_CLASS_NAME)
    }

    private fun isSuperClassEventBus(psiClass: PsiClass): Boolean {
        val supers = psiClass.supers
        if (supers.size == 0) {
            return false
        }
        for (superClass in supers) {
            if (safeEquals(superClass.name, Constants.FUN_EVENT_CLASS_NAME)) {
                return true
            }
        }
        return false
    }

    private fun safeEquals(obj: String?, value: String?): Boolean {
        return obj != null && obj == value
    }

    fun isKotlin(psiElement: PsiElement): Boolean {
        return psiElement.language.`is`(Language.findLanguageByID("kotlin"))
    }

    fun isJava(psiElement: PsiElement): Boolean {
        return psiElement.language.`is`(Language.findLanguageByID("JAVA"))
    }

    /**
     * is kotlin plug installed and enable
     *
     * @return boolean
     */
    fun checkIsKotlinInstalled(): Boolean {
        val pluginId = PluginId.findId("org.jetbrains.kotlin")
        if (pluginId != null) {
            val pluginDescriptor = PluginManager.getPlugin(pluginId)
            return pluginDescriptor != null && pluginDescriptor.isEnabled
        }
        return false
    }

    private fun logPluginList() {
        val pluginDescriptors = PluginManager.getPlugins()
        MLog.debug("== list plug ==")
        for (item in pluginDescriptors) {
            MLog.debug(
                "id: " + item.pluginId
                    .idString + " name: " + item.name + " isEnable: " + item.isEnabled
            )
        }
        MLog.debug("== list plug end ==")
    }
}
