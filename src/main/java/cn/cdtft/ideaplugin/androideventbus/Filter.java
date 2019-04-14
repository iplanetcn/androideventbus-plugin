package cn.cdtft.ideaplugin.androideventbus;

import com.intellij.usages.Usage;
/**
 * Filter
 *
 * @author john
 * @since 2019-04-13
 */
public interface Filter {
    /**
     *  should show according usage
     *
     * @param usage usage
     * @return if should show return true, or false
     */
    boolean shouldShow(Usage usage);
}
