package cn.cdtft.ideaplugin.androideventbus;

import com.intellij.psi.*;
import com.intellij.usages.Usage;
import com.intellij.usages.UsageInfo2UsageAdapter;

/**
 * SenderFilter
 *
 * @author john
 * @since 2019-04-13
 */
public class SenderFilter implements Filter {
    private final PsiElement mTagPsiElement;

    public SenderFilter(PsiElement tagPsiElement) {
        mTagPsiElement = tagPsiElement;
    }

    @Override
    public boolean shouldShow(Usage usage) {
        PsiElement element = ((UsageInfo2UsageAdapter) usage).getElement();
        if (element instanceof PsiReferenceExpression) {
            if ((element = element.getParent()) instanceof PsiMethodCallExpression) {
                PsiMethodCallExpression callExpression = (PsiMethodCallExpression) element;
                final PsiExpression[] expressions = callExpression.getArgumentList().getExpressions();
                for (PsiExpression exp : expressions) {
                    if (exp.getLastChild().getText().equals(mTagPsiElement.getLastChild().getText())) {
                        return true;
                    }
                }
            }
        }

        return false;
    }
}
