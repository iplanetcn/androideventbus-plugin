package cn.cdtft.ideaplugin.androideventbus;

import com.intellij.psi.*;
import com.intellij.usages.Usage;
import com.intellij.usages.UsageInfo2UsageAdapter;

import java.util.Objects;

/**
 * ReceiverFilter
 *
 * @author john
 * @since 2019-04-13
 */
public class ReceiverFilter implements Filter {
    private final PsiExpression mTagPsiExpression;

    ReceiverFilter(PsiExpression tagPsiExpression) {
        mTagPsiExpression = tagPsiExpression;
    }

    @Override
    public boolean shouldShow(Usage usage) {
        PsiElement element = ((UsageInfo2UsageAdapter) usage).getElement();
        if (element instanceof PsiJavaCodeReferenceElement) {
            if ((element = element.getParent()) instanceof PsiTypeElement) {
                if ((element = element.getParent()) instanceof PsiParameter) {
                    if ((element = element.getParent()) instanceof PsiParameterList) {
                        if ((element = element.getParent()) instanceof PsiMethod) {
                            PsiMethod method = (PsiMethod) element;
                            PsiModifierList modifierList = method.getModifierList();
                            for (PsiAnnotation psiAnnotation : modifierList.getAnnotations()) {
                                if (Objects.equals(psiAnnotation.getQualifiedName(), "org.simple.eventbus.Subscriber")) {
                                    final PsiAnnotationMemberValue tag = psiAnnotation.findAttributeValue("tag");
                                    return Objects.requireNonNull(tag).getLastChild().getText().equals(mTagPsiExpression.getLastChild().getText());
                                }
                            }
                        }
                    }
                }
            }
        }
        return false;
    }
}
