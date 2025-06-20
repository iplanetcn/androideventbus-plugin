package cn.cdtft.plugin.aep

import com.intellij.ui.ActiveComponent
import java.awt.FlowLayout
import javax.swing.JComponent
import javax.swing.JPanel

internal class CompositeActiveComponent(vararg components: ActiveComponent) : ActiveComponent {
    private val myComponents: Array<ActiveComponent> = components.asList().toTypedArray()
    private val myComponent: JPanel = JPanel(FlowLayout())

    init {
        myComponent.isOpaque = false
        for (component in components) {
            myComponent.add(component.component)
        }
    }

    override fun setActive(active: Boolean) {
        for (component in myComponents) {
            component.setActive(active)
        }
    }

    override fun getComponent(): JComponent {
        return myComponent
    }
}