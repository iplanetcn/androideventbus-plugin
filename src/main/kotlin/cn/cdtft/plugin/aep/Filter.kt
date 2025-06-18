package cn.cdtft.plugin.aep

import com.intellij.usages.Usage

/**
 * Filter
 *
 * @author john
 * @since 2019-04-13
 */
interface Filter {
    /**
     * should show according usage
     *
     * @param usage usage
     * @return if it should show return true, or false
     */
    fun shouldShow(usage: Usage): Boolean
}
