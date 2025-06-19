package cn.cdtft.plugin.aep.kt

import cn.cdtft.plugin.aep.Filter
import cn.cdtft.plugin.aep.PsiUtils
import cn.cdtft.plugin.aep.utils.MLog
import com.intellij.psi.*
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.usages.Usage
import com.intellij.usages.UsageInfo2UsageAdapter

/**
 * Created by likfe ( https://github.com/likfe/ ) in 2018/03/06
 *
 */
class SenderFilterKotlin internal constructor(private val eventClass: LeafPsiElement) : Filter {
    override fun shouldShow(usage: Usage): Boolean {
        var element = (usage as UsageInfo2UsageAdapter).getElement()
        if (element is PsiReferenceExpression) {
            if ((element.getParent().also { element = it }) is PsiMethodCallExpression) {
                val callExpression = element as PsiMethodCallExpression
                val types = callExpression.getArgumentList().getExpressionTypes()
                for (type in types) {
                    MLog.debug("shouldShow: 01 : " + PsiUtils.getClass(type)?.getName())
                    MLog.debug("shouldShow: 02 : " + eventClass.getText())
                    if (PsiUtils.getClass(type)?.getName() == eventClass.getText()) {
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
                                            MLog.debug("shouldShow: 03 : " + psiClass?.getName())
                                            MLog.debug("shouldShow: 04 : " + eventClass.getText())
                                            if (psiClass?.getName() == eventClass.getText()) {
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
