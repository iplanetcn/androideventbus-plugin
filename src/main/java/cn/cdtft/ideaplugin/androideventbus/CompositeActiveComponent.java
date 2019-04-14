package cn.cdtft.ideaplugin.androideventbus;

import com.intellij.ui.ActiveComponent;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * CompositeActiveComponent
 *
 * @author john
 * @since 2019-04-13
 */
public class CompositeActiveComponent implements ActiveComponent {
    private final ActiveComponent[] mActiveComponents;
    private final JPanel mJPanel;

    CompositeActiveComponent(@NotNull ActiveComponent... components) {
        mActiveComponents = components;

        mJPanel = new JPanel(new FlowLayout());
        mJPanel.setOpaque(false);
        for (ActiveComponent component : components) {
            mJPanel.add(component.getComponent());
        }
    }

    @Override
    public void setActive(boolean active) {
        for (ActiveComponent component : mActiveComponents) {
            mJPanel.add(component.getComponent());
        }
    }

    @Override
    public JComponent getComponent() {
        return mJPanel;
    }
}
