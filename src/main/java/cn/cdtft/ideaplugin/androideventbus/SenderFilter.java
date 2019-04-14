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
    private final PsiClass eventClass;
    SenderFilter(PsiClass eventClass) {
        this.eventClass = eventClass;
    }

    @Override
    public boolean shouldShow(Usage usage) {
        PsiElement element = ((UsageInfo2UsageAdapter) usage).getElement();
        if (element instanceof PsiReferenceExpression) {
            if ((element = element.getParent()) instanceof PsiMethodCallExpression) {
                PsiMethodCallExpression callExpression = (PsiMethodCallExpression) element;
//                PsiType[] types = callExpression.getArgumentList().getExpressionTypes();
////                for (PsiType type : types) {
////                    if (PsiUtils.getClass(type).getName().equals(eventClass.getName())) {
////                        // pattern : EventBus.getDefault().post(new Event());
////                        return true;
////                    }
////                }

                final PsiExpression[] expressions = callExpression.getArgumentList().getExpressions();
                for (PsiExpression exp : expressions) {
                    if (exp.getText().equals(eventClass.getText())) {
                        return true;
                    }
                }



//                if ((element = element.getParent()) instanceof PsiExpressionStatement) {
//                    if ((element = element.getParent()) instanceof PsiCodeBlock) {
//                        PsiCodeBlock codeBlock = (PsiCodeBlock) element;
//                        PsiStatement[] statements = codeBlock.getStatements();
//                        for (PsiStatement statement : statements) {
//                            if (statement instanceof PsiDeclarationStatement) {
//                                PsiDeclarationStatement declarationStatement = (PsiDeclarationStatement) statement;
//                                PsiElement[] elements = declarationStatement.getDeclaredElements();
//                                for (PsiElement variable : elements) {
//                                    if (variable instanceof PsiLocalVariable) {
//                                        PsiLocalVariable localVariable = (PsiLocalVariable) variable;
//                                        PsiClass psiClass = PsiUtils.getClass(localVariable.getTypeElement().getType());
//                                        try {
//                                            if (psiClass.getName().equals(eventClass.getName())) {
//                                                // pattern :
//                                                //   Event event = new Event();
//                                                //   EventBus.getDefault().post(event);
//                                                return true;
//                                            }
//                                        }catch (NullPointerException e){
//                                            System.out.println(e.toString());
//                                        }
//
//                                    }
//                                }
//                            }
//                        }
//                    }
//                }
            }
        }

        return false;
    }
}
