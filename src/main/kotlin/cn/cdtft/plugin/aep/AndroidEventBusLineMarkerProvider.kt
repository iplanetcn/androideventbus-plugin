package cn.cdtft.plugin.aep

import com.intellij.codeHighlighting.Pass
import com.intellij.codeInsight.daemon.GutterIconNavigationHandler
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.util.IconLoader.getIcon
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiUtilBase
import com.intellij.ui.awt.RelativePoint
import java.awt.event.MouseEvent
import java.util.*
import kotlin.collections.get

/**
 * AndroidEventBusLineMarkerProvider
 *
 * @author john
 * @since 2019-04-13
 */
class AndroidEventBusLineMarkerProvider : LineMarkerProvider {
    override fun getLineMarkerInfo(psiElement: PsiElement): LineMarkerInfo<*>? {
        if (PsiUtils.isEventBusPost(psiElement)) {
            return LineMarkerInfo<PsiElement?>(
                psiElement, psiElement.textRange, ICON,
                Pass.UPDATE_ALL, null, SHOW_RECEIVERS,
                GutterIconRenderer.Alignment.LEFT
            )
        } else if (PsiUtils.isEventBusReceiver(psiElement)) {
            return LineMarkerInfo<PsiElement?>(
                psiElement, psiElement.textRange, ICON,
                Pass.UPDATE_ALL, null, SHOW_SENDERS,
                GutterIconRenderer.Alignment.LEFT
            )
        }
        return null
    }

    public override fun collectSlowLineMarkers(
        elements: MutableList<PsiElement?>,
        result: MutableCollection<LineMarkerInfo<*>?>
    ) {
    }

    companion object {
        private val ICON = getIcon("/icons/link.svg")
        private const val MAX_USAGES = 100

        private val SHOW_SENDERS = GutterIconNavigationHandler { e: MouseEvent?, psiElement: PsiElement? ->
            if (psiElement is PsiMethod) {
                val project = psiElement.getProject()
                val javaPsiFacade: JavaPsiFacade = JavaPsiFacade.getInstance(project)
                val androidEventBusClass: PsiClass = checkNotNull(
                    javaPsiFacade.findClass(
                        "org.simple.eventbus.EventBus",
                        GlobalSearchScope.allScope(project)
                    )
                )
                // 发送方法
                val postMethod: PsiMethod = androidEventBusClass.findMethodsByName("post", false)[1]
                // 本接收方法
                val method: PsiMethod = psiElement as PsiMethod

                val tagPsiElement: PsiAnnotationMemberValue? =
                    Objects.requireNonNull<T?>(method.getAnnotation("org.simple.eventbus.Subscriber"))
                        .findAttributeValue("tag")

                ShowUsagesAction(SenderFilter(tagPsiElement)).startFindUsages(
                    postMethod,
                    RelativePoint(e!!),
                    PsiUtilBase.findEditor(psiElement),
                    MAX_USAGES
                )
            }
        }

        // psiElement is postElement
        private val SHOW_RECEIVERS = GutterIconNavigationHandler { e: MouseEvent?, psiElement: PsiElement? ->
            if (psiElement is PsiMethodCallExpression) {
                val expression: PsiMethodCallExpression = psiElement as PsiMethodCallExpression
                val expressionTypes: Array<PsiType?> = expression.getArgumentList().getExpressionTypes()
                val expressions: Array<PsiExpression?> = expression.getArgumentList().getExpressions()
                if (expressionTypes.size > 0) {
                    val messageClass: PsiClass? = PsiUtils.getClass(expressionTypes[0])
                    if (expressions.size == 2) {
                        val tagExpression: PsiExpression? = expressions[1]
                        if (messageClass != null && tagExpression != null) {
                            ShowUsagesAction(ReceiverFilter(tagExpression)).startFindUsages(
                                messageClass,
                                RelativePoint(e!!),
                                PsiUtilBase.findEditor(psiElement),
                                MAX_USAGES
                            )
                        }
                    }
                }
            }
        }
    }
}
