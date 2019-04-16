package cn.cdtft.ideaplugin.androideventbus;

import com.intellij.codeHighlighting.Pass;
import com.intellij.codeInsight.daemon.GutterIconNavigationHandler;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.ui.awt.RelativePoint;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * AndroidEventBusLineMarkerProvider
 *
 * @author john
 * @since 2019-04-13
 */
public class AndroidEventBusLineMarkerProvider implements LineMarkerProvider {
    private static final Icon ICON = IconLoader.getIcon("/icons/link.svg");
    private static final int MAX_USAGES = 100;

    private static GutterIconNavigationHandler<PsiElement> SHOW_SENDERS =
            (e, psiElement) -> {
                if (psiElement instanceof PsiMethod) {
                    Project project = psiElement.getProject();
                    JavaPsiFacade javaPsiFacade = JavaPsiFacade.getInstance(project);
                    PsiClass androidEventBusClass = javaPsiFacade.findClass("org.simple.eventbus.EventBus", GlobalSearchScope.allScope(project));
                    assert androidEventBusClass != null;
                    // 发送方法
                    PsiMethod postMethod = androidEventBusClass.findMethodsByName("post", false)[1];
                    // 本接收方法
                    PsiMethod method = (PsiMethod) psiElement;

                    final PsiAnnotationMemberValue tagPsiElement = Objects.requireNonNull(method.getAnnotation("org.simple.eventbus.Subscriber")).findAttributeValue("tag");

                    new ShowUsagesAction(new SenderFilter(tagPsiElement)).startFindUsages(postMethod, new RelativePoint(e), PsiUtilBase.findEditor(psiElement), MAX_USAGES);




                }
            };
    // psiElement is postElement
    private static GutterIconNavigationHandler<PsiElement> SHOW_RECEIVERS =
            (e, psiElement) -> {
                if (psiElement instanceof PsiMethodCallExpression) {
                    PsiMethodCallExpression expression = (PsiMethodCallExpression) psiElement;
                    PsiType[] expressionTypes = expression.getArgumentList().getExpressionTypes();
                    final PsiExpression[] expressions = expression.getArgumentList().getExpressions();
                    if (expressionTypes.length > 0) {
                        PsiClass messageClass = PsiUtils.getClass(expressionTypes[0]);
                        if (expressions.length == 2) {
                            PsiExpression tagExpression = expressions[1];
                            if (messageClass != null && tagExpression !=null ) {
                                new ShowUsagesAction(new ReceiverFilter(tagExpression)).startFindUsages(messageClass, new RelativePoint(e), PsiUtilBase.findEditor(psiElement), MAX_USAGES);
                            }
                        }

                    }
                }

            };


    @Nullable
    @Override
    public LineMarkerInfo getLineMarkerInfo(@NotNull PsiElement psiElement) {
        if (PsiUtils.isEventBusPost(psiElement)) {
            return new LineMarkerInfo<>(psiElement, psiElement.getTextRange(), ICON,
                    Pass.UPDATE_ALL, null, SHOW_RECEIVERS,
                    GutterIconRenderer.Alignment.LEFT);
        } else if (PsiUtils.isEventBusReceiver(psiElement)) {
            return new LineMarkerInfo<>(psiElement, psiElement.getTextRange(), ICON,
                    Pass.UPDATE_ALL, null, SHOW_SENDERS,
                    GutterIconRenderer.Alignment.LEFT);
        }
        return null;
    }

    @Override
    public void collectSlowLineMarkers(@NotNull List<PsiElement> elements, @NotNull Collection<LineMarkerInfo> result) {

    }
}
