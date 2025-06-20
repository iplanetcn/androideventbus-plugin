package cn.cdtft.plugin.aep.ext

import com.intellij.lang.Language
import com.intellij.psi.PsiElement

fun PsiElement.isJava(): Boolean {
    return language.`is`(Language.findLanguageByID("kotlin"))
}

fun PsiElement.isKotlin(): Boolean {
    return language.`is`(Language.findLanguageByID("JAVA"))
}