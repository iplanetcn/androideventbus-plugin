/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.CommonBundle
import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.hint.HintUtil
import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction
import com.intellij.featureStatistics.FeatureUsageTracker
import com.intellij.find.FindBundle
import com.intellij.find.FindManager
import com.intellij.find.FindSettings
import com.intellij.find.actions.FindUsagesInFileAction
import com.intellij.find.actions.UsageListCellRenderer
import com.intellij.find.findUsages.FindUsagesHandler
import com.intellij.find.findUsages.FindUsagesManager
import com.intellij.find.findUsages.FindUsagesOptions
import com.intellij.find.findUsages.PsiElement2UsageTargetAdapter
import com.intellij.find.impl.FindManagerImpl
import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.ide.util.gotoByName.ModelDiff
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorLocation
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.PopupChooserBuilder
import com.intellij.openapi.util.Comparing
import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.search.ProjectScope
import com.intellij.psi.search.PsiElementProcessor
import com.intellij.psi.search.SearchScope
import com.intellij.ui.*
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.popup.AbstractPopup
import com.intellij.usageView.UsageViewBundle
import com.intellij.usages.*
import com.intellij.usages.impl.*
import com.intellij.usages.rules.UsageFilteringRuleProvider
import com.intellij.util.Alarm
import com.intellij.util.PlatformIcons
import com.intellij.util.Processor
import com.intellij.util.ui.AsyncProcessIcon
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.ListTableModel
import org.jetbrains.annotations.NonNls
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Rectangle
import java.awt.event.ActionEvent
import java.util.*
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import javax.swing.*
import kotlin.math.max
import kotlin.math.min

/**
 * ShowUsagesAction
 *
 * @author john
 * @since 2019-04-13
 */
class ShowUsagesAction internal constructor(private val filter: Filter) : AnAction(), PopupAction {
    private val myUsageViewSettings: UsageViewSettings
    private var mySearchEverywhereRunnable: Runnable? = null

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    } 
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.getData(PlatformDataKeys.PROJECT)
        if (project == null) {
            return
        }

        val searchEverywhere = mySearchEverywhereRunnable
        mySearchEverywhereRunnable = null
        hideHints()

        if (searchEverywhere != null) {
            searchEverywhere.run()
            return
        }

        val popupPosition = JBPopupFactory.getInstance().guessBestPopupLocation(e.dataContext)
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        FeatureUsageTracker.getInstance().triggerFeatureUsed("navigation.goto.usages")

        val usageTargets = e.getData(UsageView.USAGE_TARGETS_KEY)
        val editor = e.getData(PlatformDataKeys.EDITOR)
        if (usageTargets == null) {
            chooseAmbiguousTargetAndPerform(
                project, editor
            ) { element: PsiElement? ->
                startFindUsages(element!!, popupPosition, editor, USAGES_PAGE_SIZE)
                false
            }
        } else {
            val element = (usageTargets[0] as PsiElementUsageTarget).element
            if (element != null) {
                startFindUsages(element, popupPosition, editor, USAGES_PAGE_SIZE)
            }
        }
    }

    // element is postMethod
    fun startFindUsages(element: PsiElement, popupPosition: RelativePoint, editor: Editor?, maxUsages: Int) {
        val project = element.project
        val findUsagesManager = (FindManager.getInstance(project) as FindManagerImpl).findUsagesManager
        val handler = findUsagesManager.getNewFindUsagesHandler(element, false)
        if (handler == null) {
            return
        }
        showElementUsages(handler, editor, popupPosition, maxUsages, getDefaultOptions(handler))
    }

    /**
     * 显示使用的地方
     */
    private fun showElementUsages(
        handler: FindUsagesHandler, editor: Editor?,
        popupPosition: RelativePoint, maxUsages: Int,
        options: FindUsagesOptions
    ) {
        ApplicationManager.getApplication().assertIsDispatchThread()
        val usageViewSettings: UsageViewSettings = UsageViewSettings.instance
        val savedGlobalSettings = UsageViewSettings()

        savedGlobalSettings.loadState(usageViewSettings)
        usageViewSettings.loadState(myUsageViewSettings)

        val project = handler.project
        val manager = UsageViewManager.getInstance(project)
        val findUsagesManager = (FindManager.getInstance(project) as FindManagerImpl).findUsagesManager
        val presentation = findUsagesManager.createPresentation(handler, options)
        presentation.isDetachedMode = true
        val usageView =
            manager.createUsageView(UsageTarget.EMPTY_ARRAY, Usage.EMPTY_ARRAY, presentation, null) as UsageViewImpl

        Disposer.register(usageView) {
            myUsageViewSettings.loadState(usageViewSettings)
            usageViewSettings.loadState(savedGlobalSettings)
        }

        val usages: MutableList<Usage> = ArrayList<Usage>()
        val visibleNodes: MutableSet<UsageNode?> = LinkedHashSet<UsageNode?>()

        val table = MyTable()
        val processIcon = AsyncProcessIcon("xxx")
        val hadMoreSeparator = visibleNodes.remove(MORE_USAGES_SEPARATOR_NODE)
        if (hadMoreSeparator) {
            usages.add(MORE_USAGES_SEPARATOR)
            visibleNodes.add(MORE_USAGES_SEPARATOR_NODE)
        }

        addUsageNodes(usageView.root, usageView, ArrayList<UsageNode?>())

        ScrollingUtil.installActions(table)

        val data: MutableList<UsageNode?> = collectData(usages, visibleNodes, usageView, presentation)
        setTableModel(table, usageView, data)

        val speedSearch: SpeedSearchBase<JTable?> = MySpeedSearch(table)
        speedSearch.comparator = SpeedSearchComparator(false)

        val popup = createUsagePopup(
            usages, visibleNodes, handler, editor, popupPosition,
            maxUsages, usageView, options, table, presentation, processIcon, hadMoreSeparator
        )

        Disposer.register(popup, usageView)

        // show popup only if you find usages takes more than 300ms, otherwise it would flicker needlessly
        val alarm = Alarm(usageView)
        alarm.addRequest({ showPopupIfNeedTo(popup, popupPosition) }, 300)

        val pingEDT = PingEDT(Condition { o: Any? -> popup.isDisposed }, 100, Runnable {
            if (popup.isDisposed) {
                return@Runnable
            }
            val nodes: MutableList<UsageNode?> = ArrayList<UsageNode?>()
            val copy: MutableList<Usage>?
            synchronized(usages) {
                // open up popup as soon as several usages 've been found
                if (usages.size <= 1 || !showPopupIfNeedTo(popup, popupPosition)) {
                    if (!popup.isVisible) {
                        return@Runnable
                    }
                }

                addUsageNodes(usageView.root, usageView, nodes)
                copy = ArrayList<Usage>(usages)
            }
            rebuildPopup(usageView, copy!!, nodes, table, popup, presentation, popupPosition, !processIcon.isDisposed)
        })

        val messageBusConnection = project.messageBus.connect(usageView)
        @Suppress("UnstableApiUsage")
        messageBusConnection.subscribe(UsageFilteringRuleProvider.RULES_CHANGED, Runnable { pingEDT.ping() })
        
        val collect: Processor<Usage?> = object : Processor<Usage?> {
            private val myUsageTarget = arrayOf<UsageTarget?>(PsiElement2UsageTargetAdapter(handler.psiElement, true))

            override fun process(usage: Usage?): Boolean {
                synchronized(usages) {
                    if (!filter.shouldShow(usage!!)) {
                        return true
                    }
                    if (visibleNodes.size >= maxUsages) {
                        return false
                    }
                    if (UsageViewManager.isSelfUsage(usage, myUsageTarget)) {
                        return true
                    }

                    val usageToAdd = transform(usage)

                    val node = usageView.doAppendUsage(usageToAdd)
                    usages.add(usageToAdd)
                    if (node != null) {
                        visibleNodes.add(node)
                        var continueSearch = true
                        if (visibleNodes.size == maxUsages) {
                            visibleNodes.add(MORE_USAGES_SEPARATOR_NODE)
                            usages.add(MORE_USAGES_SEPARATOR)
                            continueSearch = false
                        }
                        pingEDT.ping()

                        return continueSearch
                    }
                    return true
                }
            }
        }

        val indicator = FindUsagesManager.startProcessUsages(
            handler, handler.primaryElements,
            handler.secondaryElements, collect, options
        ) {
            ApplicationManager.getApplication().invokeLater(
                {
                    Disposer.dispose(processIcon)
                    val parent = processIcon.getParent()
                    parent.remove(processIcon)
                    parent.repaint()
                    pingEDT.ping() // repaint title
                    synchronized(usages) {
                        if (visibleNodes.isEmpty()) {
                            if (usages.isEmpty()) {
                                val text = UsageViewBundle.message(
                                    "no.usages.found.in",
                                    searchScopePresentableName(options, project)
                                )
                                showHint(text, editor, popupPosition, handler, maxUsages, options)
                                popup.cancel()
                            }
                        } else if (visibleNodes.size == 1) {
                            if (usages.size == 1) {
                                //the only usage
                                val usage = visibleNodes.iterator().next()!!.usage
                                usage.navigate(true)
                                popup.cancel()
                            } else {
                                assert(usages.size > 1) { usages }
                                // usage view can filter usages down to one
                                val visibleUsage = visibleNodes.iterator().next()!!.usage
                                if (areAllUsagesInOneLine(visibleUsage, usages)) {
                                    val hint = UsageViewBundle.message(
                                        "all.usages.are.in.this.line",
                                        usages.size,
                                        searchScopePresentableName(options, project)
                                    )
                                    navigateAndHint(visibleUsage, hint, handler, popupPosition, maxUsages, options)
                                    popup.cancel()
                                }
                            }
                        } else {
                            val title = presentation.tabText
                            val shouldShowMoreSeparator = visibleNodes.contains(MORE_USAGES_SEPARATOR_NODE)
                            val fullTitle: String = getFullTitle(
                                usages,
                                title,
                                shouldShowMoreSeparator,
                                visibleNodes.size - (if (shouldShowMoreSeparator) 1 else 0),
                                false
                            )
                            popup.setCaption(fullTitle)
                        }
                    }
                }, project.disposed
            )
        }
        Disposer.register(popup) { indicator.cancel() }
    }

    private fun transform(usage: Usage): Usage {
        return usage
    }

    private class MyModel(data: MutableList<UsageNode?>, cols: Int) : ListTableModel<UsageNode?>(cols(cols), data, 0), ModelDiff.Model<Any> {
        override fun addToModel(idx: Int, element: Any) {
            val node = element as? UsageNode ?: createStringNode(element)

            if (idx < rowCount) {
                insertRow(idx, node)
            } else {
                addRow(node)
            }
        }

        override fun removeRangeFromModel(start: Int, end: Int) {
            for (i in end downTo start) {
                removeRow(i)
            }
        }

        companion object {
            private fun cols(cols: Int): Array<ColumnInfo<*, *>> {
                val o: ColumnInfo<UsageNode?, UsageNode?> = object : ColumnInfo<UsageNode?, UsageNode?>("") {
                    override fun valueOf(node: UsageNode?): UsageNode? {
                        return node
                    }
                }
                val list = Collections.nCopies(cols, o)
                return list.toTypedArray<ColumnInfo<*, *>>()
            }
        }
    }

    private fun showHint(
        text: String,
        editor: Editor?,
        popupPosition: RelativePoint,
        handler: FindUsagesHandler,
        maxUsages: Int,
        options: FindUsagesOptions
    ) {
        val label = createHintComponent(text, handler, popupPosition, editor, maxUsages, options)
        if (editor == null || editor.isDisposed) {
            HintManager.getInstance().showHint(
                label, popupPosition, HintManager.HIDE_BY_ANY_KEY or
                        HintManager.HIDE_BY_TEXT_CHANGE or HintManager.HIDE_BY_SCROLLING, 0
            )
        } else {
            HintManager.getInstance().showInformationHint(editor, label)
        }
    }

    private fun createHintComponent(
        text: String,
        handler: FindUsagesHandler,
        popupPosition: RelativePoint,
        editor: Editor?,
        maxUsages: Int,
        options: FindUsagesOptions
    ): JComponent {
        val label: JComponent = HintUtil.createInformationLabel(suggestSecondInvocation(options, handler, "$text&nbsp;"))
        val button = createSettingsButton(handler, popupPosition, editor, maxUsages, HIDE_HINTS_ACTION)

        val panel: JPanel = object : JPanel(BorderLayout()) {
            override fun addNotify() {
                mySearchEverywhereRunnable = Runnable {
                    searchEverywhere(
                        options,
                        handler,
                        editor,
                        popupPosition,
                        maxUsages
                    )
                }
                super.addNotify()
            }

            override fun removeNotify() {
                mySearchEverywhereRunnable = null
                super.removeNotify()
            }
        }
        button.setBackground(label.getBackground())
        panel.setBackground(label.getBackground())
        label.setOpaque(false)
        label.setBorder(null)
        panel.setBorder(HintUtil.createHintBorder())
        panel.add(label, BorderLayout.CENTER)
        panel.add(button, BorderLayout.EAST)
        return panel
    }

    private fun createSettingsButton(
        handler: FindUsagesHandler,
        popupPosition: RelativePoint,
        editor: Editor?,
        maxUsages: Int,
        cancelAction: Runnable
    ): InplaceButton {
        var shortcutText = ""
        val shortcut = UsageViewImpl.getShowUsagesWithSettingsShortcut()
        if (shortcut != null) {
            shortcutText = "(" + KeymapUtil.getShortcutText(shortcut) + ")"
        }
        return InplaceButton(
            "Settings...$shortcutText",
            AllIcons.General.Settings
        ) { e: ActionEvent? ->
            SwingUtilities.invokeLater {
                showDialogAndFindUsages(
                    handler,
                    popupPosition,
                    editor,
                    maxUsages
                )
            }
            cancelAction.run()
        }
    }

    private fun showDialogAndFindUsages(
        handler: FindUsagesHandler,
        popupPosition: RelativePoint,
        editor: Editor?,
        maxUsages: Int
    ) {
        val dialog = handler.getFindUsagesDialog(false, false, false)
        dialog.show()
        if (dialog.isOK) {
            dialog.calcFindUsagesOptions()
            showElementUsages(handler, editor, popupPosition, maxUsages, getDefaultOptions(handler))
        }
    }

    private fun createUsagePopup(
        usages: MutableList<Usage>,
        visibleNodes: MutableSet<UsageNode?>,
        handler: FindUsagesHandler,
        editor: Editor?,
        popupPosition: RelativePoint,
        maxUsages: Int,
        usageView: UsageViewImpl,
        options: FindUsagesOptions,
        table: JTable,
        presentation: UsageViewPresentation,
        processIcon: AsyncProcessIcon,
        hadMoreSeparator: Boolean
    ): JBPopup {
        table.setRowHeight(PlatformIcons.CLASS_ICON.iconHeight + 2)
        table.setShowGrid(false)
        table.setShowVerticalLines(false)
        table.setShowHorizontalLines(false)
        table.setTableHeader(null)
        table.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN)
        table.setIntercellSpacing(Dimension(0, 0))

        val builder: PopupChooserBuilder<*> = PopupChooserBuilder<Any?>(table)
        val title = presentation.tabText
        if (title != null) {
            val result: String = getFullTitle(usages, title, hadMoreSeparator, visibleNodes.size - 1, true)
            builder.setTitle(result)
            builder.setAdText(getSecondInvocationTitle(options, handler))
        }

        builder.setMovable(true).setResizable(true)
        builder.setItemChosenCallback(Runnable {
            val selected = table.selectedRows
            for (i in selected) {
                val value = table.getValueAt(i, 0)
                if (value is UsageNode) {
                    val usage = value.usage
                    if (usage === MORE_USAGES_SEPARATOR) {
                        appendMoreUsages(editor, popupPosition, handler, maxUsages)
                        return@Runnable
                    }
                    navigateAndHint(usage, null, handler, popupPosition, maxUsages, options)
                }
            }
        })
        val popup: Array<JBPopup?> = arrayOfNulls(1)
        var shortcut = UsageViewImpl.getShowUsagesWithSettingsShortcut()
        if (shortcut != null) {
            object : DumbAwareAction() {
                override fun actionPerformed(e: AnActionEvent) {
                    popup[0]?.cancel()
                    showDialogAndFindUsages(handler, popupPosition, editor, maxUsages)
                }
            }.registerCustomShortcutSet(CustomShortcutSet(shortcut.firstKeyStroke), table)
        }
        shortcut = showUsagesShortcut
        if (shortcut != null) {
            object : DumbAwareAction() {
                override fun actionPerformed(e: AnActionEvent) {
                    popup[0]?.cancel()
                    searchEverywhere(options, handler, editor, popupPosition, maxUsages)
                }
            }.registerCustomShortcutSet(CustomShortcutSet(shortcut.firstKeyStroke), table)
        }

        val settingsButton =
            createSettingsButton(handler, popupPosition, editor, maxUsages) { popup[0]?.cancel() }

        val spinningProgress: ActiveComponent = object : ActiveComponent {
            override fun setActive(active: Boolean) {
            }

            override fun getComponent(): JComponent {
                return processIcon
            }
        }
        builder.setCommandButton(CompositeActiveComponent(spinningProgress, settingsButton))

        val toolbar = DefaultActionGroup()
        usageView.addFilteringActions(toolbar)

        toolbar.add(UsageGroupingRuleProviderImpl().createGroupingActions(usageView).first())
        toolbar.add(object : AnAction(
            "Open Find Usages Tool Window",
            "Show all usages in a separate tool window",
            AllIcons.Toolwindows.ToolWindowFind
        ) {
            init {
                val action = ActionManager.getInstance().getAction(IdeActions.ACTION_FIND_USAGES)
                @Suppress("UnstableApiUsage")
                shortcutSet = action.shortcutSet
            }

            override fun actionPerformed(e: AnActionEvent) {
                hideHints()
                popup[0]?.cancel()
                val findUsagesManager =
                    (FindManager.getInstance(usageView.project) as FindManagerImpl).findUsagesManager

                findUsagesManager.findUsages(
                    handler.primaryElements, handler.secondaryElements, handler, options,
                    FindSettings.getInstance().isSkipResultsWithOneUsage
                )
            }
        })

        val actionToolbar =
            ActionManager.getInstance().createActionToolbar(ActionPlaces.USAGE_VIEW_TOOLBAR, toolbar, true)
        actionToolbar.isReservePlaceAutoPopupIcon = false
        val toolBar = actionToolbar.component
        toolBar.setOpaque(false)
        builder.setSettingButton(toolBar)

        popup[0] = builder.createPopup()
        val content = popup[0]?.content

        if (title != null) {
            myWidth = ((toolBar.getPreferredSize().getWidth()
                    + JLabel(
                getFullTitle(
                    usages,
                    title,
                    hadMoreSeparator,
                    visibleNodes.size - 1,
                    true
                )
            ).getPreferredSize().getWidth()
                    + settingsButton.preferredSize.getWidth())).toInt()
        }
        myWidth = -1
        for (action in toolbar.getChildren(null)) {
            action.unregisterCustomShortcutSet(usageView.component)
            action.registerCustomShortcutSet(action.shortcutSet, content)
        }

        return popup[0]!!
    }

    private fun searchEverywhere(
        options: FindUsagesOptions,
        handler: FindUsagesHandler,
        editor: Editor?,
        popupPosition: RelativePoint,
        maxUsages: Int
    ) {
        val cloned = options.clone()
        cloned.searchScope = FindUsagesManager.getMaximalScope(handler)
        showElementUsages(handler, editor, popupPosition, maxUsages, cloned)
    }

    private var myWidth = 0

    init {
        setInjectedContext(true)
        val usageViewSettings: UsageViewSettings = UsageViewSettings.instance
        myUsageViewSettings = UsageViewSettings()
        myUsageViewSettings.loadState(usageViewSettings)
        myUsageViewSettings.isGroupByFileStructure = false
        myUsageViewSettings.isGroupByModule = false
        myUsageViewSettings.isGroupByPackage = false
        myUsageViewSettings.isGroupByUsageType = false
        myUsageViewSettings.isGroupByScope = false
    }


    private fun rebuildPopup(
        usageView: UsageViewImpl,
        usages: MutableList<Usage>,
        nodes: MutableList<UsageNode?>,
        table: JTable,
        popup: JBPopup,
        presentation: UsageViewPresentation,
        popupPosition: RelativePoint,
        findUsagesInProgress: Boolean
    ) {
        ApplicationManager.getApplication().assertIsDispatchThread()

        val shouldShowMoreSeparator = usages.contains(MORE_USAGES_SEPARATOR)
        if (shouldShowMoreSeparator) {
            nodes.add(MORE_USAGES_SEPARATOR_NODE)
        }

        val title = presentation.tabText
        val fullTitle: String = getFullTitle(
            usages,
            title,
            shouldShowMoreSeparator,
            nodes.size - (if (shouldShowMoreSeparator) 1 else 0),
            findUsagesInProgress
        )

        popup.setCaption(fullTitle)

        val data: MutableList<UsageNode?> = collectData(usages, nodes, usageView, presentation)
        val tableModel: MyModel = setTableModel(table, usageView, data)
        val existingData = tableModel.items

        val row = table.selectedRow

        var newSelection: Int = updateModel(tableModel, existingData, data, if (row == -1) 0 else row)
        if (newSelection < 0 || newSelection >= tableModel.rowCount) {
            TableUtil.ensureSelectionExists(table)
            newSelection = table.selectedRow
        } else {
            table.getSelectionModel().setSelectionInterval(newSelection, newSelection)
        }
        ScrollingUtil.ensureIndexIsVisible(table, newSelection, 0)

        setSizeAndDimensions(table, popup, popupPosition, data)
    }

    private fun setSizeAndDimensions(
        table: JTable,
        popup: JBPopup,
        popupPosition: RelativePoint,
        data: MutableList<UsageNode?>
    ) {
        val content = popup.content
        val window = SwingUtilities.windowForComponent(content)
        val d = window.size

        var width: Int = calcMaxWidth(table)
        width = max(d.getWidth(), width.toDouble()).toInt()
        val headerSize = (popup as AbstractPopup).headerPreferredSize
        width = max(headerSize.getWidth().toInt(), width)
        width = max(myWidth, width)

        if (myWidth == -1) {
            myWidth = width
        }
        val newWidth = max(width, d.width + width - myWidth)

        myWidth = newWidth

        val rowsToShow = min(30, data.size)
        var dimension = Dimension(newWidth, table.getRowHeight() * rowsToShow)
        val rectangle: Rectangle = fitToScreen(dimension, popupPosition, table)
        dimension = rectangle.size
        val location = window.location
        if (location != rectangle.location) {
            window.location = rectangle.location
        }

        if (!data.isEmpty()) {
            TableUtil.ensureSelectionExists(table)
        }
        table.size = dimension

        val footerSize = popup.footerPreferredSize

        /* invisible borders, margins etc*/
        val newHeight = (dimension.height + headerSize.getHeight() + footerSize.getHeight()).toInt() + 4
        val newDim = Dimension(dimension.width, newHeight)
        window.size = newDim
        window.minimumSize = newDim
        window.setMaximumSize(newDim)

        window.validate()
        window.repaint()
        table.revalidate()
        table.repaint()
    }

    private fun appendMoreUsages(
        editor: Editor?,
        popupPosition: RelativePoint,
        handler: FindUsagesHandler,
        maxUsages: Int
    ) {
        showElementUsages(handler, editor, popupPosition, maxUsages + USAGES_PAGE_SIZE, getDefaultOptions(handler))
    }

    private fun addUsageNodes(root: GroupNode, usageView: UsageViewImpl, outNodes: MutableList<UsageNode?>) {
        for (node in root.usageNodes) {
            val usage = node.usage
            if (usageView.isVisible(usage)) {
                node.setParent(root)
                outNodes.add(node)
            }
        }
        for (groupNode in root.subGroups) {
            groupNode.setParent(root)
            addUsageNodes(groupNode, usageView, outNodes)
        }
    }

    override fun update(e: AnActionEvent) {
        FindUsagesInFileAction.updateFindUsagesAction(e)
    }

    private fun navigateAndHint(
        usage: Usage,
        hint: String?,
        handler: FindUsagesHandler,
        popupPosition: RelativePoint,
        maxUsages: Int,
        options: FindUsagesOptions
    ) {
        usage.navigate(true)
        if (hint == null) {
            return
        }
        val newEditor: Editor? = getEditorFor(usage)
        if (newEditor == null) {
            return
        }
        // val project = handler.project
        // opening editor is performing in invokeLater
        ApplicationManager.getApplication().invokeLater {
            newEditor.scrollingModel.runActionOnScrollingFinished {
                // after new editor created, some editor resizing events are still bubbling. To prevent hiding hint, invokeLater this
                ApplicationManager.getApplication().invokeLater {
                    if (newEditor.component.isShowing()) {
                        showHint(hint, newEditor, popupPosition, handler, maxUsages, options)
                    }
                }
            }
        }
    }

    @Suppress("JComponentDataProvider")
    private class MyTable : JTable(), DataProvider {
        override fun getScrollableTracksViewportWidth(): Boolean {
            return true
        }

        override fun getData(dataId: @NonNls String): Any? {
            if (LangDataKeys.PSI_ELEMENT.`is`(dataId)) {
                val selected = selectedRows
                if (selected.size == 1) {
                    return getPsiElementForHint(getValueAt(selected[0], 0))
                }
            }
            return null
        }

        fun getPsiElementForHint(selectedValue: Any?): PsiElement? {
            if (selectedValue is UsageNode) {
                val usage = selectedValue.usage
                if (usage is UsageInfo2UsageAdapter) {
                    val element = usage.element
                    if (element != null) {
                        val view = UsageToPsiElementProvider.findAppropriateParentFrom(element)
                        return view ?: element
                    }
                }
            }
            return null
        }
    }

    @Suppress("UnstableApiUsage")
    internal class StringNode(private val myString: Any) : UsageNode(null, NullUsage.INSTANCE) {
        override fun toString(): String {
            return myString.toString()
        }
    }

    private class MySpeedSearch(table: MyTable) : SpeedSearchBase<JTable?>(table) {
        override fun getSelectedIndex(): Int {
            return this.table!!.selectedRow
        }

        @Suppress("removal")
        override fun convertIndexToModel(viewIndex: Int): Int {
            return this.table!!.convertRowIndexToModel(viewIndex)
        }


        @Suppress("removal")
        override fun getAllElements(): Array<UsageNode?> {
            return (this.table!!.model as MyModel).items.toTypedArray()
        }

        override fun getElementText(element: Any): String? {
            if (element !is UsageNode) {
                return element.toString()
            }
            val node = element
            if (node is StringNode) {
                return ""
            }
            val usage = node.usage
            if (usage === MORE_USAGES_SEPARATOR) {
                return ""
            }
            val group = node.getParent() as GroupNode?
            return usage.presentation.plainText + group
        }

        override fun selectElement(element: Any?, selectedText: String?) {
            val data = (this.table!!.model as MyModel).items
            if (element is UsageNode) {
                val i = data.indexOf(element)
                if (i == -1) {
                    return
                }

                val viewRow = this.table!!.convertRowIndexToView(i)
                this.table!!.getSelectionModel().setSelectionInterval(viewRow, viewRow)
                TableUtil.scrollSelectionToVisible(this.table!!)
            }
        }

        val table: MyTable?
            get() = myComponent as MyTable?
    }

    companion object {
        private const val USAGES_PAGE_SIZE = 100

        val MORE_USAGES_SEPARATOR: NullUsage = NullUsage.INSTANCE
        private val MORE_USAGES_SEPARATOR_NODE: UsageNode = UsageViewImpl.NULL_NODE

        private val USAGE_NODE_COMPARATOR = Comparator { c1: UsageNode?, c2: UsageNode? ->
            if (c1 is StringNode) {
                return@Comparator 1
            }
            if (c2 is StringNode) {
                return@Comparator -1
            }
            val o1 = c1!!.usage
            val o2 = c2!!.usage
            if (o1 === MORE_USAGES_SEPARATOR) {
                return@Comparator 1
            }
            if (o2 === MORE_USAGES_SEPARATOR) {
                return@Comparator -1
            }

            val v1 = UsageListCellRenderer.getVirtualFile(o1)
            val v2 = UsageListCellRenderer.getVirtualFile(o2)
            val name1 = v1?.name
            val name2 = v2?.name
            val i = Comparing.compare<String>(name1, name2)
            if (i != 0) {
                return@Comparator i
            }

            if (o1 is Comparable<*> && o2 is Comparable<*>) {
                @Suppress("UNCHECKED_CAST")
                return@Comparator (o1 as Comparable<Any>).compareTo(o2)
            }

            val loc1 = o1.location
            val loc2 = o2.location
            Comparing.compare<FileEditorLocation?>(loc1, loc2)
        }
        private val HIDE_HINTS_ACTION = Runnable { hideHints() }
        private fun chooseAmbiguousTargetAndPerform(
            project: Project,
            editor: Editor?,
            processor: PsiElementProcessor<PsiElement?>
        ) {
            if (editor == null) {
                Messages.showMessageDialog(
                    project, FindBundle.message("find.no.usages.at.cursor.error"),
                    CommonBundle.getErrorTitle(), Messages.getErrorIcon()
                )
            } else {
                val offset = editor.caretModel.offset
                val chosen = GotoDeclarationAction.chooseAmbiguousTarget(
                    editor, offset, processor,
                    FindBundle.message("find.usages.ambiguous.title", "crap"), null
                )
                if (!chosen) {
                    ApplicationManager.getApplication().invokeLater(Runnable {
                        if (editor.isDisposed || !editor.component.isShowing()) {
                            return@Runnable
                        }
                        HintManager.getInstance()
                            .showErrorHint(editor, FindBundle.message("find.no.usages.at.cursor.error"))
                    }, project.disposed)
                }
            }
        }


        private fun hideHints() {
            HintManager.getInstance().hideHints(HintManager.HIDE_BY_ANY_KEY, false, false)
        }

        private fun getDefaultOptions(handler: FindUsagesHandler): FindUsagesOptions {
            var options: FindUsagesOptions? = null
            try {
                options = handler.getFindUsagesOptions(
                    DataManager.getInstance().dataContextFromFocusAsync.blockingGet(5, TimeUnit.SECONDS)
                )
            } catch (e: TimeoutException) {
                e.printStackTrace()
            } catch (e: ExecutionException) {
                e.printStackTrace()
            }
            // by default, scope in FindUsagesOptions is copied from the FindSettings, but we need a default one
            checkNotNull(options)
            options.searchScope = FindUsagesManager.getMaximalScope(handler)
            return options
        }

        private fun createStringNode(string: Any): UsageNode {
            return StringNode(string)
        }

        private fun showPopupIfNeedTo(popup: JBPopup, popupPosition: RelativePoint): Boolean {
            if (!popup.isDisposed && !popup.isVisible) {
                popup.show(popupPosition)
                return true
            } else {
                return false
            }
        }

        private fun searchScopePresentableName(options: FindUsagesOptions, project: Project): String {
            return notNullizeScope(options, project).displayName
        }

        private fun notNullizeScope(options: FindUsagesOptions, project: Project): SearchScope {
            val scope = options.searchScope
            if (scope == null) {
                return ProjectScope.getAllScope(project)
            }
            return scope
        }

        private fun getFullTitle(
            usages: MutableList<Usage>,
            title: String,
            hadMoreSeparator: Boolean,
            visibleNodesCount: Int,
            findUsagesInProgress: Boolean
        ): String {
            val s: String = if (hadMoreSeparator) {
                "<b>Some</b> " + title + " " + "<b>(Only " + visibleNodesCount + " usages shown" + (if (findUsagesInProgress) " so far" else "") + ")</b>"
            } else {
                "$title (" + UsageViewBundle.message(
                    "usages.n",
                    usages.size
                ) + (if (findUsagesInProgress) " so far" else "") + ")"
            }
            return "<html><nobr>$s</nobr></html>"
        }

        private fun suggestSecondInvocation(
            options: FindUsagesOptions,
            handler: FindUsagesHandler,
            text: String
        ): String {
            var text = text
            val title: String? = getSecondInvocationTitle(options, handler)

            if (title != null) {
                text += "<br><small>Press $title</small>"
            }
            return "<html><body>$text</body></html>"
        }

        private fun getSecondInvocationTitle(options: FindUsagesOptions, handler: FindUsagesHandler): String? {
            if (showUsagesShortcut != null) {
                val maximalScope = FindUsagesManager.getMaximalScope(handler)
                if (notNullizeScope(options, handler.project) != maximalScope) {
                    return "Press " + KeymapUtil.getShortcutText(showUsagesShortcut!!) + " again to search in " + maximalScope.displayName
                }
            }
            return null
        }

        private val showUsagesShortcut: KeyboardShortcut?
            get() = ActionManager.getInstance().getKeyboardShortcut("ShowUsages")

        private fun filtered(usages: MutableList<Usage>, usageView: UsageViewImpl): Int {
            var count = 0
            for (usage in usages) {
                if (!usageView.isVisible(usage)) {
                    count++
                }
            }
            return count
        }

        private fun getUsageOffset(usage: Usage): Int {
            if (usage !is UsageInfo2UsageAdapter) {
                return -1
            }
            val element = usage.element
            if (element == null) {
                return -1
            }
            return element.textRange.startOffset
        }

        private fun areAllUsagesInOneLine(visibleUsage: Usage, usages: MutableList<Usage>): Boolean {
            val editor: Editor? = getEditorFor(visibleUsage)
            if (editor == null) {
                return false
            }
            val offset: Int = getUsageOffset(visibleUsage)
            if (offset == -1) {
                return false
            }
            val lineNumber = editor.document.getLineNumber(offset)
            for (other in usages) {
                val otherEditor: Editor? = getEditorFor(other)
                if (otherEditor !== editor) {
                    return false
                }
                val otherOffset: Int = getUsageOffset(other)
                if (otherOffset == -1) {
                    return false
                }
                val otherLine = otherEditor.document.getLineNumber(otherOffset)
                if (otherLine != lineNumber) {
                    return false
                }
            }
            return true
        }

        private fun setTableModel(
            table: JTable,
            usageView: UsageViewImpl,
            data: MutableList<UsageNode?>
        ): MyModel {
            ApplicationManager.getApplication().assertIsDispatchThread()
            val columnCount: Int = calcColumnCount(data)
            var model = if (table.model is MyModel) table.model as MyModel? else null
            if (model == null || model.columnCount != columnCount) {
                model = MyModel(data, columnCount)
                table.setModel(model)

                val renderer = ShowUsagesTableCellRenderer(usageView)
                for (i in 0..<table.getColumnModel().columnCount) {
                    val column = table.getColumnModel().getColumn(i)
                    column.setCellRenderer(renderer)
                }
            }
            return model
        }

        private fun calcColumnCount(data: MutableList<UsageNode?>): Int {
            return if (data.isEmpty() || data[0] is StringNode) 1 else 3
        }

        private fun collectData(
            usages: MutableList<Usage>,
            visibleNodes: MutableCollection<UsageNode?>,
            usageView: UsageViewImpl,
            presentation: UsageViewPresentation
        ): MutableList<UsageNode?> {
            val data: MutableList<UsageNode?> = ArrayList<UsageNode?>()
            val filtered: Int = filtered(usages, usageView)
            if (filtered != 0) {
                data.add(createStringNode(UsageViewBundle.message("usages.were.filtered.out", filtered)))
            }
            data.addAll(visibleNodes)
            if (data.isEmpty()) {
                val progressText = UsageViewManagerImpl.getProgressTitle(presentation)
                data.add(createStringNode(progressText))
            }
            data.sortWith(USAGE_NODE_COMPARATOR)
            return data
        }

        private fun calcMaxWidth(table: JTable): Int {
            val colsNum = table.getColumnModel().columnCount

            var totalWidth = 0
            for (col in 0..<colsNum - 1) {
                val column = table.getColumnModel().getColumn(col)
                val preferred = column.preferredWidth
                val width = max(preferred, columnMaxWidth(table, col))
                totalWidth += width
                column.setMinWidth(width)
                column.setMaxWidth(width)
                column.setWidth(width)
                column.setPreferredWidth(width)
            }

            totalWidth += columnMaxWidth(table, colsNum - 1)

            return totalWidth
        }

        private fun columnMaxWidth(table: JTable, col: Int): Int {
            val column = table.getColumnModel().getColumn(col)
            var width = 0
            for (row in 0..<table.getRowCount()) {
                val component = table.prepareRenderer(column.getCellRenderer(), row, col)

                val rendererWidth = component.preferredSize.width
                width = max(width, rendererWidth + table.intercellSpacing.width)
            }
            return width
        }

        private fun updateModel(
            tableModel: MyModel,
            listOld: MutableList<UsageNode?>,
            listNew: MutableList<UsageNode?>,
            oldSelection: Int
        ): Int {
            val oa = listOld.toTypedArray<UsageNode?>()
            val na = listNew.toTypedArray<UsageNode?>()
            val cmds = ModelDiff.createDiffCmds(tableModel, oa, na)
            var selection = oldSelection
            if (cmds != null) {
                for (cmd in cmds) {
                    selection = cmd.translateSelection(selection)
                    cmd.apply()
                }
            }
            return selection
        }

        private fun fitToScreen(newDim: Dimension, popupPosition: RelativePoint, table: JTable): Rectangle {
            val rectangle = Rectangle(popupPosition.screenPoint, newDim)
            ScreenUtil.fitToScreen(rectangle)
            if (rectangle.getHeight() != newDim.getHeight()) {
                val newHeight = rectangle.getHeight().toInt()
                val roundedHeight = newHeight - newHeight % table.getRowHeight()
                rectangle.setSize(rectangle.getWidth().toInt(), max(roundedHeight, table.getRowHeight()))
            }
            return rectangle
        }

        private fun getEditorFor(usage: Usage): Editor? {
            val location = usage.location
            val newFileEditor = location?.editor
            return if (newFileEditor is TextEditor) newFileEditor.editor else null
        }
    }
}
