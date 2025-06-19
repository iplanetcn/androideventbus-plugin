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
        if (psiElement.getLanguage().`is`(Language.findLanguageByID("JAVA"))) {
            if (psiElement is PsiMethod) {
                val method = psiElement
                val modifierList = method.getModifierList()
                for (psiAnnotation in modifierList.getAnnotations()) {
                    if (safeEquals(psiAnnotation.getQualifiedName(), Constants.FUN_ANNOTATION)) {
                        return true
                    }
                }
            }
        } else if (psiElement.getLanguage().`is`(Language.findLanguageByID("kotlin"))) {
            if (psiElement is KtNamedFunction) {
                val function = psiElement
                val modifierList = function.getModifierList()
                if (modifierList != null) {
                    for (annotationEntry in modifierList.getAnnotationEntries()) {
                        val calleeExpression = annotationEntry.getCalleeExpression()
                        if (calleeExpression != null && safeEquals(
                                calleeExpression.getText(),
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
        if (psiElement.getLanguage().`is`(Language.findLanguageByID("JAVA"))) {
            if (psiElement is PsiMethodCallExpressionImpl && psiElement.getFirstChild() != null && psiElement.getFirstChild() is PsiReferenceExpressionImpl) {
                val all = psiElement.getFirstChild() as PsiReferenceExpressionImpl
                if (all.getFirstChild() is PsiMethodCallExpressionImpl && all.getLastChild() is PsiIdentifierImpl) {
                    val start = all.getFirstChild() as PsiMethodCallExpressionImpl
                    val post = all.getLastChild() as PsiIdentifierImpl
                    if ((safeEquals(post.getText(), Constants.FUN_NAME) || safeEquals(
                            post.getText(),
                            Constants.FUN_NAME2
                        )) && safeEquals(start.getText(), Constants.FUN_START)
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
                    val name = method.getName()
                    val parent = method.getParent()
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
        } else if (psiElement.getLanguage().`is`(Language.findLanguageByID("kotlin"))) {
            if (psiElement is KtDotQualifiedExpression) {
                val all = psiElement
                if (all.getFirstChild() is KtDotQualifiedExpression && all.getLastChild() is KtCallExpression) {
                    val start = all.getFirstChild().getText()
                    if (start != null && start == Constants.FUN_START) {
                        val postRoot = all.getLastChild() as KtCallExpression
                        if (postRoot.getFirstChild() is KtNameReferenceExpression) {
                            val referenceExpression = postRoot.getFirstChild() as KtNameReferenceExpression
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
        return safeEquals(psiClass.getName(), Constants.FUN_EVENT_CLASS_NAME)
    }

    private fun isSuperClassEventBus(psiClass: PsiClass): Boolean {
        val supers = psiClass.getSupers()
        if (supers.size == 0) {
            return false
        }
        for (superClass in supers) {
            if (safeEquals(superClass.getName(), Constants.FUN_EVENT_CLASS_NAME)) {
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
            return pluginDescriptor != null && pluginDescriptor.isEnabled()
        }
        return false
    }

    private fun logPluginList() {
        val pluginDescriptors = PluginManager.getPlugins()
        MLog.debug("== list plug ==")
        for (item in pluginDescriptors) {
            MLog.debug(
                "id: " + item.getPluginId()
                    .getIdString() + " name: " + item.getName() + " isEnable: " + item.isEnabled()
            )
        }
        MLog.debug("== list plug end ==")
    }
}
