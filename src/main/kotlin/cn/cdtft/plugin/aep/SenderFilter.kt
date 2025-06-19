package cn.cdtft.plugin.aep

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiReferenceExpression
import com.intellij.usages.Usage
import com.intellij.usages.UsageInfo2UsageAdapter

/**
 * SenderFilter
 *
 * @author john
 * @since 2019-04-13
 */
class SenderFilter internal constructor(private val mTagPsiElement: PsiElement) : Filter {
    override fun shouldShow(usage: Usage): Boolean {
        var element = (usage as UsageInfo2UsageAdapter).element
        if (element is PsiReferenceExpression) {
            if ((element.parent.also { element = it }) is PsiMethodCallExpression) {
                val callExpression: PsiMethodCallExpression = element as PsiMethodCallExpression
                val expressions: Array<PsiExpression> = callExpression.argumentList.expressions
                for (exp in expressions) {
                    if (exp.lastChild.text.equals(mTagPsiElement.lastChild.text)) {
                        return true
                    }
                }
            }
        }

        return false
    }
}
