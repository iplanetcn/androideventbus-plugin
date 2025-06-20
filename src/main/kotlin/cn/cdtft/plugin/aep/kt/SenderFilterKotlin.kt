package cn.cdtft.plugin.aep.kt

import cn.cdtft.plugin.aep.Filter
import cn.cdtft.plugin.aep.PsiUtils
import cn.cdtft.plugin.aep.utils.MLog
import com.intellij.psi.*
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.usages.Usage
import com.intellij.usages.UsageInfo2UsageAdapter

class SenderFilterKotlin internal constructor(private val eventClass: LeafPsiElement) : Filter {
    override fun shouldShow(usage: Usage): Boolean {
        var element = (usage as UsageInfo2UsageAdapter).element
        if (element is PsiReferenceExpression) {
            if ((element.parent.also { element = it }) is PsiMethodCallExpression) {
                val callExpression = element as PsiMethodCallExpression
                val types = callExpression.argumentList.expressionTypes
                for (type in types) {
                    MLog.debug("shouldShow: 01 : " + PsiUtils.getClass(type)?.name)
                    MLog.debug("shouldShow: 02 : " + eventClass.text)
                    if (PsiUtils.getClass(type)?.name == eventClass.text) {
                        // pattern : EventBus.getDefault().post(new Event());
                        return true
                    }
                }
                if ((element.parent.also { element = it }) is PsiExpressionStatement) {
                    if ((element!!.parent.also { element = it }) is PsiCodeBlock) {
                        val codeBlock = element as PsiCodeBlock
                        val statements = codeBlock.statements
                        for (statement in statements) {
                            if (statement is PsiDeclarationStatement) {
                                val declarationStatement = statement
                                val elements = declarationStatement.declaredElements
                                for (variable in elements) {
                                    if (variable is PsiLocalVariable) {
                                        val localVariable = variable
                                        val psiClass = PsiUtils.getClass(localVariable.typeElement.type)
                                        try {
                                            MLog.debug("shouldShow: 03 : " + psiClass?.name)
                                            MLog.debug("shouldShow: 04 : " + eventClass.text)
                                            if (psiClass?.name == eventClass.text) {
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
