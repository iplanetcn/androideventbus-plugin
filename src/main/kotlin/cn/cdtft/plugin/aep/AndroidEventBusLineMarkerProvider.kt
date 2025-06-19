package cn.cdtft.plugin.aep

import com.intellij.codeInsight.daemon.GutterIconNavigationHandler
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.util.IconLoader.getIcon
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiAnnotationMemberValue
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiType
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiEditorUtil
import com.intellij.ui.awt.RelativePoint
import java.awt.event.MouseEvent
import javax.swing.Icon

/**
 * AndroidEventBusLineMarkerProvider
 *
 * @author john
 * @since 2019-04-13
 */
class AndroidEventBusLineMarkerProvider : LineMarkerProvider {
    
    
    override fun getLineMarkerInfo(psiElement: PsiElement): LineMarkerInfo<*>? {
        if (PsiUtils.isEventBusPost(psiElement)) {
            return LineMarkerInfo(
                psiElement, psiElement.textRange, ICON,
                null, SHOW_RECEIVERS,
                GutterIconRenderer.Alignment.LEFT
            ) {
                "post"
            }
        } else if (PsiUtils.isEventBusReceiver(psiElement)) {
            return LineMarkerInfo(
                psiElement, psiElement.textRange, ICON,
                null, SHOW_SENDERS,
                GutterIconRenderer.Alignment.LEFT
            ) {
                "org.simple.eventbus.Subscriber"
            }
        }
        return null
    }

    override fun collectSlowLineMarkers(elements: List<PsiElement?>, result: MutableCollection<in LineMarkerInfo<*>>) {
        // noop
    }
}

private const val MAX_USAGES = 100

private val ICON = getIcon("/icons/link.svg", Icon::class.java)

private val SHOW_SENDERS = GutterIconNavigationHandler { e: MouseEvent?, psiElement: PsiElement? ->
    if (psiElement is PsiMethod) {
        val project = psiElement.project
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
        val method: PsiMethod = psiElement

        val tagPsiElement: PsiAnnotationMemberValue? = method.getAnnotation("org.simple.eventbus.Subscriber")?.findAttributeValue("tag")

        ShowUsagesAction(SenderFilter(tagPsiElement!!)).startFindUsages(
            postMethod,
            RelativePoint(e!!),
            PsiEditorUtil.findEditor(psiElement),
            MAX_USAGES
        )
    }
}

// psiElement is postElement
private val SHOW_RECEIVERS = GutterIconNavigationHandler { e: MouseEvent?, psiElement: PsiElement? ->
    if (psiElement is PsiMethodCallExpression) {
        val expression: PsiMethodCallExpression = psiElement
        val expressionTypes: Array<PsiType?> = expression.argumentList.expressionTypes
        val expressions: Array<PsiExpression?> = expression.argumentList.expressions
        if (expressionTypes.isNotEmpty()) {
            val messageClass: PsiClass? = PsiUtils.getClass(expressionTypes[0])
            if (expressions.size == 2) {
                val tagExpression: PsiExpression? = expressions[1]
                if (messageClass != null && tagExpression != null) {
                    ShowUsagesAction(ReceiverFilter(tagExpression)).startFindUsages(
                        messageClass,
                        RelativePoint(e!!),
                        PsiEditorUtil.findEditor(psiElement),
                        MAX_USAGES
                    )
                }
            }
        }
    }
}