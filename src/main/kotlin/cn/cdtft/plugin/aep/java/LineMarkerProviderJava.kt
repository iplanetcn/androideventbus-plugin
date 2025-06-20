package cn.cdtft.plugin.aep.java

import cn.cdtft.plugin.aep.PsiUtils
import cn.cdtft.plugin.aep.ShowUsagesAction
import cn.cdtft.plugin.aep.ext.isJava
import cn.cdtft.plugin.aep.utils.Constants
import com.intellij.codeInsight.daemon.GutterIconNavigationHandler
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiEditorUtil
import com.intellij.ui.awt.RelativePoint
import java.awt.event.MouseEvent


class LineMarkerProviderJava : LineMarkerProvider {
    override fun getLineMarkerInfo(psiElement: PsiElement): LineMarkerInfo<*>? {
        if (!psiElement.isJava()) return null
        if (PsiUtils.isEventBusPost(psiElement)) {
            return LineMarkerInfo<PsiElement?>(
                psiElement,
                psiElement.textRange,
                Constants.ICON,
                null,
                SHOW_RECEIVERS,
                GutterIconRenderer.Alignment.LEFT
            )
        } else if (PsiUtils.isEventBusReceiver(psiElement)) {
            return LineMarkerInfo<PsiElement?>(
                psiElement,
                psiElement.textRange,
                Constants.ICON,
                null,
                SHOW_SENDERS,
                GutterIconRenderer.Alignment.LEFT
            )
        }
        return null
    }

    override fun collectSlowLineMarkers(
        list: MutableList<out PsiElement?>,
        collection: MutableCollection<in LineMarkerInfo<*>?>
    ) {}

}

/**
 * use Subscribe to find all matched post
 */
private val SHOW_SENDERS: GutterIconNavigationHandler<PsiElement?> =
    object : GutterIconNavigationHandler<PsiElement?> {
        override fun navigate(e: MouseEvent, psiElement: PsiElement?) {
            if (psiElement is PsiMethod) {
                val project = psiElement.project
                val javaPsiFacade = JavaPsiFacade.getInstance(project)
                val eventBusClass = javaPsiFacade.findClass(Constants.FUN_EVENT_CLASS, GlobalSearchScope.allScope(project))
                if (eventBusClass == null) return
                // send methods
                val postMethod = eventBusClass.findMethodsByName(Constants.FUN_NAME, false).first()
                if (null != postMethod) {
                    val method = psiElement
                    val tagPsiElement: PsiAnnotationMemberValue? = method.getAnnotation(Constants.FUN_ANNOTATION)?.findAttributeValue(Constants.FUN_ANNOTATION_TAG)
//                    val eventClass = (method.parameterList.parameters[0].typeElement!!.type as PsiClassType).resolve()
                    ShowUsagesAction(SenderFilterJava(tagPsiElement!!)).startFindUsages(
                        postMethod,
                        RelativePoint(e),
                        PsiEditorUtil.findEditor(psiElement),
                        Constants.MAX_USAGES
                    )
                }

                //postSticky
//                        PsiMethod postMethod2 = eventBusClass.findMethodsByName(Constants.FUN_NAME2, false)[0];
//                        if (null != postMethod2) {
//                            PsiClass eventClass = ((PsiClassType) method.getParameterList().getParameters()[0].getTypeElement().getType()).resolve();
//
//                            new ShowUsagesAction(new SenderFilterJava(eventClass)).startFindUsages(postMethod2, new RelativePoint(e), PsiUtilBase.findEditor(psiElement), Constants.MAX_USAGES);
//                        }
            }
        }
    }

/**
 * use post to find all matched Subscribe
 */
private val SHOW_RECEIVERS: GutterIconNavigationHandler<PsiElement?> =
    GutterIconNavigationHandler<PsiElement?> { e, psiElement ->
        if (psiElement is PsiMethodCallExpression) {
            val expression = psiElement
            try {
                val expressionTypes = expression.argumentList.expressionTypes
                val expressions = expression.argumentList.expressions
                if (expressionTypes.size > 0) {
                    val eventClass = PsiUtils.getClass(expressionTypes[0])
                    if (expressions.size == 2) {
                        val tagExpression = expressions[1]
                        if (eventClass != null && tagExpression != null) {
                            ShowUsagesAction(ReceiverFilterJava(tagExpression)).startFindUsages(
                                eventClass,
                                RelativePoint(e),
                                PsiEditorUtil.findEditor(psiElement),
                                Constants.MAX_USAGES
                            )
                        }
                    }
                }
            } catch (ee: Exception) {
                ee.fillInStackTrace()
            }
        }
    }