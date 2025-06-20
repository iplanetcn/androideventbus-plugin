package cn.cdtft.plugin.aep.java

import cn.cdtft.plugin.aep.Filter
import cn.cdtft.plugin.aep.PsiUtils
import com.intellij.psi.*
import com.intellij.usages.Usage
import com.intellij.usages.UsageInfo2UsageAdapter

class SenderFilterJava(private val mTagPsiElement: PsiElement) : Filter {
    override fun shouldShow(usage: Usage): Boolean {
        var element = (usage as UsageInfo2UsageAdapter).element
        if (element is PsiReferenceExpression) {
            if ((element.parent.also { element = it }) is PsiMethodCallExpression) {
                val callExpression = element as PsiMethodCallExpression
                val expressions = callExpression.argumentList.expressions
                for (expression in expressions) {
                    if (expression.lastChild.text.equals(mTagPsiElement.lastChild.text)) {
                        // pattern:
                        // EventBus.getDefault().post(event, "tag");
                        return true
                    }
                }
//                if ((element.parent.also { element = it }) is PsiExpressionStatement) {
//                    if ((element!!.parent.also { element = it }) is PsiCodeBlock) {
//                        val codeBlock = element as PsiCodeBlock
//                        val statements = codeBlock.statements
//                        for (statement in statements) {
//                            if (statement is PsiDeclarationStatement) {
//                                val declarationStatement = statement
//                                val elements = declarationStatement.declaredElements
//                                for (variable in elements) {
//                                    if (variable is PsiLocalVariable) {
//                                        val localVariable = variable
//                                        val psiClass = PsiUtils.getClass(localVariable.typeElement.type)
//                                        try {
//                                            if (psiClass?.name == eventClass.name) {
//                                                // pattern :
//                                                //   Event event = new Event();
//                                                //   EventBus.getDefault().post(event);
//                                                return true
//                                            }
//                                        } catch (e: NullPointerException) {
//                                            println(e.toString())
//                                        }
//                                    }
//                                }
//                            }
//                        }
//                    }
//                }
            }
        }

        return false
    }
}
