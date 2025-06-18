/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cn.cdtft.plugin.aep

import com.intellij.psi.PsiManager
import com.intellij.ui.FileColorManager
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import com.intellij.usages.Usage
import com.intellij.usages.impl.GroupNode
import com.intellij.usages.impl.UsageNode
import com.intellij.usages.impl.UsageViewImpl
import com.intellij.usages.rules.UsageInFile
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.FlowLayout
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.SwingConstants
import javax.swing.table.TableCellRenderer

internal class ShowUsagesTableCellRenderer(private val myUsageView: UsageViewImpl) : TableCellRenderer {
    override fun getTableCellRendererComponent(
        list: JTable,
        value: Any?,
        isSelected: Boolean,
        hasFocus: Boolean,
        row: Int,
        column: Int
    ): Component {
        val usageNode = if (value is UsageNode) value else null

        val usage = if (usageNode == null) null else usageNode.getUsage()

        val panel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0))
        val fileBgColor = getBackgroundColor(isSelected, usage)
        val bg = UIUtil.getListSelectionBackground()
        val fg = UIUtil.getListSelectionForeground()
        panel.setBackground(if (isSelected) bg else if (fileBgColor == null) list.getBackground() else fileBgColor)
        panel.setForeground(if (isSelected) fg else list.getForeground())

        if (usage == null || usageNode is ShowUsagesAction.StringNode) {
            panel.setLayout(BorderLayout())
            if (column == 0) {
                panel.add(JLabel("<html><body><b>" + value + "</b></body></html>", SwingConstants.CENTER))
            }
            return panel
        }


        val textChunks = SimpleColoredComponent()
        textChunks.setIpad(JBUI.emptyInsets())
        textChunks.setBorder(null)

        if (column == 0) {
            val parent = usageNode!!.getParent() as GroupNode?
            appendGroupText(parent, panel, fileBgColor)
            if (usage === ShowUsagesAction.Companion.MORE_USAGES_SEPARATOR) {
                textChunks.append("...<")
                textChunks.append("more usages", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
                textChunks.append(">...")
            }
        } else if (usage !== ShowUsagesAction.Companion.MORE_USAGES_SEPARATOR) {
            val presentation = usage.getPresentation()
            val text = presentation.getText()

            if (column == 1) {
                val icon = presentation.getIcon()
                textChunks.setIcon(if (icon == null) EmptyIcon.ICON_16 else icon)
                if (text.size != 0) {
                    val attributes = if (isSelected) SimpleTextAttributes(
                        bg,
                        fg,
                        fg,
                        SimpleTextAttributes.STYLE_ITALIC
                    ) else deriveAttributesWithColor(text[0].getSimpleAttributesIgnoreBackground(), fileBgColor)
                    textChunks.append(text[0].getText(), attributes)
                }
            } else if (column == 2) {
                for (i in 1..<text.size) {
                    val textChunk = text[i]
                    val attrs = textChunk.getSimpleAttributesIgnoreBackground()
                    val attributes = if (isSelected) SimpleTextAttributes(
                        bg,
                        fg,
                        fg,
                        attrs.getStyle()
                    ) else deriveAttributesWithColor(attrs, fileBgColor)
                    textChunks.append(textChunk.getText(), attributes)
                }
            } else {
                assert(false) { column }
            }
        }
        panel.add(textChunks)
        return panel
    }

    private fun getBackgroundColor(isSelected: Boolean, usage: Usage?): Color? {
        var fileBgColor: Color? = null
        if (isSelected) {
            fileBgColor = UIUtil.getListSelectionBackground()
        } else {
            val virtualFile = if (usage is UsageInFile) usage.getFile() else null
            if (virtualFile != null) {
                val project = myUsageView.getProject()
                val psiFile = PsiManager.getInstance(project).findFile(virtualFile)
                if (psiFile != null && psiFile.isValid()) {
                    val color = FileColorManager.getInstance(project).getRendererBackground(psiFile)
                    if (color != null) {
                        fileBgColor = color
                    }
                }
            }
        }
        return fileBgColor
    }

    private fun appendGroupText(node: GroupNode?, panel: JPanel, fileBgColor: Color?) {
        val group = if (node == null) null else node.getGroup()
        if (group == null) {
            return
        }
        val parentGroup = node!!.getParent() as GroupNode?
        appendGroupText(parentGroup, panel, fileBgColor)
        if (node.canNavigateToSource()) {
            val renderer = SimpleColoredComponent()

            renderer.setIcon(group.getIcon(false))
            val attributes: SimpleTextAttributes =
                deriveAttributesWithColor(SimpleTextAttributes.REGULAR_ATTRIBUTES, fileBgColor)
            renderer.append(group.getText(myUsageView), attributes)
            renderer.append(" ", attributes)
            renderer.setIpad(JBUI.emptyInsets())
            renderer.setBorder(null)
            panel.add(renderer)
        }
    }

    companion object {
        private fun deriveAttributesWithColor(
            attributes: SimpleTextAttributes,
            fileBgColor: Color?
        ): SimpleTextAttributes {
            var attributes = attributes
            if (fileBgColor != null) {
                attributes = attributes.derive(-1, null, fileBgColor, null)
            }
            return attributes
        }
    }
}