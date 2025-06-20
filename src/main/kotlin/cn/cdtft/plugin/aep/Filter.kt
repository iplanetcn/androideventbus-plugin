package cn.cdtft.plugin.aep

import com.intellij.usages.Usage

interface Filter {
    fun shouldShow(usage: Usage): Boolean
}
