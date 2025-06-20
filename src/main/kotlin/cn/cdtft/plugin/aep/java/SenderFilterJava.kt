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
                        return true
                    }
                }
            }
        }

        return false
    }
}
