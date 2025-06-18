package cn.cdtft.plugin.aep

import com.intellij.ui.ActiveComponent
import java.awt.FlowLayout
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * CompositeActiveComponent
 *
 * @author john
 * @since 2019-04-13
 */
class CompositeActiveComponent internal constructor(vararg components: ActiveComponent) : ActiveComponent {
    private val mActiveComponents: Array<out ActiveComponent> = components
    private val mJPanel: JPanel = JPanel(FlowLayout())

    init {
        mJPanel.setOpaque(false)
        for (component in components) {
            mJPanel.add(component.component)
        }
    }

    override fun setActive(active: Boolean) {
        for (component in mActiveComponents) {
            mJPanel.add(component.component)
        }
    }

    override fun getComponent(): JComponent {
        return mJPanel
    }
}
