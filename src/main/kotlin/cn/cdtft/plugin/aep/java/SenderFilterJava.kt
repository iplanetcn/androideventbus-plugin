package cn.cdtft.plugin.aep.java

import cn.cdtft.plugin.aep.Filter
import cn.cdtft.plugin.aep.PsiUtils
import com.intellij.psi.*
import com.intellij.usages.Usage
import com.intellij.usages.UsageInfo2UsageAdapter

/**
 * Created by kgmyshin on 2015/06/07.
 *
 * modify by likfe ( https://github.com/likfe/ ) in 2016/09/05
 *
 * add try-catch
 */
class SenderFilterJava(private val eventClass: PsiClass) : Filter {
    override fun shouldShow(usage: Usage): Boolean {
        var element = (usage as UsageInfo2UsageAdapter).getElement()
        if (element is PsiReferenceExpression) {
            if ((element.getParent().also { element = it }) is PsiMethodCallExpression) {
                val callExpression = element as PsiMethodCallExpression
                val types = callExpression.getArgumentList().getExpressionTypes()
                for (type in types) {
                    if (PsiUtils.getClass(type).getName() == eventClass.getName()) {
                        // pattern : EventBus.getDefault().post(new Event());
                        return true
                    }
                }
                if ((element.getParent().also { element = it }) is PsiExpressionStatement) {
                    if ((element!!.getParent().also { element = it }) is PsiCodeBlock) {
                        val codeBlock = element as PsiCodeBlock
                        val statements = codeBlock.getStatements()
                        for (statement in statements) {
                            if (statement is PsiDeclarationStatement) {
                                val declarationStatement = statement
                                val elements = declarationStatement.getDeclaredElements()
                                for (variable in elements) {
                                    if (variable is PsiLocalVariable) {
                                        val localVariable = variable
                                        val psiClass = PsiUtils.getClass(localVariable.getTypeElement().getType())
                                        try {
                                            if (psiClass.getName() == eventClass.getName()) {
                                                // pattern :
                                                //   Event event = new Event();
                                                //   EventBus.getDefault().post(event);
                                                return true
                                            }
                                        } catch (e: NullPointerException) {
                                            println(e.toString())
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        return false
    }
}
