package cn.cdtft.plugin.aep.java

import cn.cdtft.plugin.aep.PsiUtils
import cn.cdtft.plugin.aep.ShowUsagesAction
import cn.cdtft.plugin.aep.utils.Constants
import com.intellij.codeInsight.daemon.GutterIconNavigationHandler
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiUtilBase
import com.intellij.ui.awt.RelativePoint
import java.awt.event.MouseEvent

/**
 * Created by kgmyshin on 15/06/08.
 *
 *
 * modify by likfe ( https://github.com/likfe/ ) in 2018/03/06
 *
 */
class LineMarkerProviderJava : LineMarkerProvider {
    override fun getLineMarkerInfo(psiElement: PsiElement): LineMarkerInfo<*>? {
        if (!PsiUtils.isJava(psiElement)) return null
        //if (!(psiElement instanceof PsiIdentifier && psiElement.getParent() instanceof PsiMethod)) return null;
        if (PsiUtils.isEventBusPost(psiElement)) {
            return LineMarkerInfo<PsiElement?>(
                psiElement,
                psiElement.getTextRange(),
                Constants.ICON,
                null,
                SHOW_RECEIVERS,
                GutterIconRenderer.Alignment.LEFT
            )
        } else if (PsiUtils.isEventBusReceiver(psiElement)) {
            return LineMarkerInfo<PsiElement?>(
                psiElement,
                psiElement.getTextRange(),
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
    ) {
    }

    companion object {
        /**
         * use Subscribe to find all matched post
         */
        private val SHOW_SENDERS: GutterIconNavigationHandler<PsiElement?> =
            object : GutterIconNavigationHandler<PsiElement?> {
                override fun navigate(e: MouseEvent, psiElement: PsiElement?) {
                    if (psiElement is PsiMethod) {
                        val project = psiElement.getProject()
                        val javaPsiFacade = JavaPsiFacade.getInstance(project)
                        val eventBusClass =
                            javaPsiFacade.findClass(Constants.FUN_EVENT_CLASS, GlobalSearchScope.allScope(project))
                        if (eventBusClass == null) return

                        val method = psiElement

                        //post
                        val postMethod = eventBusClass.findMethodsByName(Constants.FUN_NAME, false)[0]
                        if (null != postMethod) {
                            val eventClass = (method.getParameterList().getParameters()[0].getTypeElement()!!
                                .getType() as PsiClassType).resolve()

                            ShowUsagesAction(SenderFilterJava(eventClass)).startFindUsages(
                                postMethod,
                                RelativePoint(e),
                                PsiUtilBase.findEditor(psiElement),
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
            object : GutterIconNavigationHandler<PsiElement?> {
                override fun navigate(e: MouseEvent, psiElement: PsiElement?) {
                    if (psiElement is PsiMethodCallExpression) {
                        val expression = psiElement
                        try {
                            val expressionTypes = expression.getArgumentList().getExpressionTypes()
                            if (expressionTypes.size > 0) {
                                val eventClass = PsiUtils.getClass(expressionTypes[0])
                                if (eventClass != null) {
                                    ShowUsagesAction(ReceiverFilterJava()).startFindUsages(
                                        eventClass,
                                        RelativePoint(e),
                                        PsiUtilBase.findEditor(psiElement),
                                        Constants.MAX_USAGES
                                    )
                                }
                            }
                        } catch (ee: Exception) {
                            ee.fillInStackTrace()
                        }
                    }
                }
            }
    }
}
