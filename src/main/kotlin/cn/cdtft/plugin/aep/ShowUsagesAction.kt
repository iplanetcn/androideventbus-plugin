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
import com.intellij.ide.util.gotoByName.ModelDiff
import com.intellij.openapi.Disposable
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
import com.intellij.usages.UsageInfoToUsageConverter.TargetElementsDescriptor
import com.intellij.usages.impl.*
import com.intellij.usages.rules.UsageFilteringRuleProvider
import com.intellij.util.Alarm
import com.intellij.util.PlatformIcons
import com.intellij.util.Processor
import com.intellij.util.ui.AsyncProcessIcon
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.ListTableModel
import org.jetbrains.annotations.NonNls
import org.jetbrains.kotlin.idea.facet.getInstance
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Rectangle
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.util.*
import javax.swing.*
import kotlin.math.max
import kotlin.math.min

/**
 * modify by likfe ( https://github.com/likfe/ ) in 2016/09/05
 *
 *
 * add ShowUsagesAction(), if Registering actions in the plugin.xml file,ShowUsagesAction must have ShowUsagesAction()
 */
class ShowUsagesAction : AnAction, PopupAction {
    private var filter: Filter? = null

    private val myUsageViewSettings: UsageViewSettings
    private var mySearchEverywhereRunnable: Runnable? = null

    constructor() {
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

    constructor(filter: Filter) {
        this.filter = filter
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


    override fun actionPerformed(e: AnActionEvent) {
        val project = e.getData<Project?>(PlatformDataKeys.PROJECT)
        if (project == null) return

        val searchEverywhere = mySearchEverywhereRunnable
        mySearchEverywhereRunnable = null
        hideHints()

        if (searchEverywhere != null) {
            searchEverywhere.run()
            return
        }

        val popupPosition = JBPopupFactory.getInstance().guessBestPopupLocation(e.getDataContext())
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        FeatureUsageTracker.getInstance().triggerFeatureUsed("navigation.goto.usages")

        val usageTargets = e.getData<Array<UsageTarget?>?>(UsageView.USAGE_TARGETS_KEY)
        val editor = e.getData<Editor?>(PlatformDataKeys.EDITOR)
        if (usageTargets == null) {
            chooseAmbiguousTargetAndPerform(
                project, editor,
                object : PsiElementProcessor<PsiElement?> {
                    override fun execute(element: PsiElement): Boolean {
                        startFindUsages(element, popupPosition, editor, USAGES_PAGE_SIZE)
                        return false
                    }
                })
        } else {
            val element = (usageTargets[0] as PsiElementUsageTarget).getElement()
            if (element != null) {
                startFindUsages(element, popupPosition, editor, USAGES_PAGE_SIZE)
            }
        }
    }

    fun startFindUsages(element: PsiElement, popupPosition: RelativePoint, editor: Editor?, maxUsages: Int) {
        val project = element.getProject()
        val findUsagesManager = (FindManager.getInstance(project) as FindManagerImpl).getFindUsagesManager()
        val handler = findUsagesManager.getNewFindUsagesHandler(element, false)
        if (handler == null) return
        showElementUsages(handler, editor, popupPosition, maxUsages, getDefaultOptions(handler))
    }

    private fun showElementUsages(
        handler: FindUsagesHandler,
        editor: Editor?,
        popupPosition: RelativePoint,
        maxUsages: Int,
        options: FindUsagesOptions
    ) {
        ApplicationManager.getApplication().assertIsDispatchThread()
        val usageViewSettings: UsageViewSettings = UsageViewSettings.instance
        val savedGlobalSettings = UsageViewSettings()

        savedGlobalSettings.loadState(usageViewSettings)
        usageViewSettings.loadState(myUsageViewSettings)

        val project = handler.getProject()
        val manager = UsageViewManager.getInstance(project)
        val findUsagesManager = (FindManager.getInstance(project) as FindManagerImpl).getFindUsagesManager()
        val presentation = findUsagesManager.createPresentation(handler, options)
        presentation.setDetachedMode(true)
        val usageView =
            manager.createUsageView(UsageTarget.EMPTY_ARRAY, Usage.EMPTY_ARRAY, presentation, null) as UsageViewImpl

        Disposer.register(usageView, object : Disposable {
            override fun dispose() {
                myUsageViewSettings.loadState(usageViewSettings)
                usageViewSettings.loadState(savedGlobalSettings)
            }
        })

        val usages: MutableList<Usage> = ArrayList<Usage>()
        val visibleNodes: MutableSet<UsageNode?> = LinkedHashSet<UsageNode?>()
        val descriptor =
            TargetElementsDescriptor(handler.getPrimaryElements(), handler.getSecondaryElements())

        val table = MyTable()
        val processIcon = AsyncProcessIcon("xxx")
        val hadMoreSeparator = visibleNodes.remove(MORE_USAGES_SEPARATOR_NODE)
        if (hadMoreSeparator) {
            usages.add(MORE_USAGES_SEPARATOR)
            visibleNodes.add(MORE_USAGES_SEPARATOR_NODE)
        }

        addUsageNodes(usageView.getRoot(), usageView, ArrayList<UsageNode?>())

        ScrollingUtil.installActions(table)

        val data: MutableList<UsageNode?> = collectData(usages, visibleNodes, usageView, presentation)
        setTableModel(table, usageView, data)

        val speedSearch: SpeedSearchBase<JTable?> = MySpeedSearch(table)
        speedSearch.setComparator(SpeedSearchComparator(false))

        val popup = createUsagePopup(
            usages, descriptor, visibleNodes, handler, editor, popupPosition,
            maxUsages, usageView, options, table, presentation, processIcon, hadMoreSeparator
        )

        Disposer.register(popup, usageView)

        // show popup only if find usages takes more than 300ms, otherwise it would flicker needlessly
        val alarm = Alarm(usageView)
        alarm.addRequest(object : Runnable {
            override fun run() {
                showPopupIfNeedTo(popup, popupPosition)
            }
        }, 300)

        val pingEDT = PingEDT("Rebuild popup in EDT", object : Condition<Any?> {
            override fun value(o: Any?): Boolean {
                return popup.isDisposed()
            }
        }, 100, object : Runnable {
            override fun run() {
                if (popup.isDisposed()) return

                val nodes: MutableList<UsageNode?> = ArrayList<UsageNode?>()
                val copy: MutableList<Usage>?
                synchronized(usages) {
                    // open up popup as soon as several usages 've been found
                    if (!popup.isVisible() && (usages.size <= 1 || !showPopupIfNeedTo(popup, popupPosition))) {
                        return
                    }
                    addUsageNodes(usageView.getRoot(), usageView, nodes)
                    copy = ArrayList<Usage>(usages)
                }

                rebuildPopup(
                    usageView,
                    copy!!,
                    nodes,
                    table,
                    popup,
                    presentation,
                    popupPosition,
                    !processIcon.isDisposed()
                )
            }
        })

        val messageBusConnection = project.getMessageBus().connect(usageView)
        messageBusConnection.subscribe<Runnable>(UsageFilteringRuleProvider.RULES_CHANGED!!, Runnable { pingEDT.ping() })


        val collect: Processor<Usage> = object : Processor<Usage> {
            private val myUsageTarget =
                arrayOf<UsageTarget?>(PsiElement2UsageTargetAdapter(handler.getPsiElement(), true))

            override fun process(usage: Usage): Boolean {
                synchronized(usages) {
                    if (!filter!!.shouldShow(usage)) return true
                    if (visibleNodes.size >= maxUsages) return false
                    if (UsageViewManager.isSelfUsage(usage, myUsageTarget)) {
                        return true
                    }

                    val usageToAdd = transform(usage)
                    if (usageToAdd == null) return true

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
            handler,
            handler.getPrimaryElements(),
            handler.getSecondaryElements(),
            collect,
            options,
            object : Runnable {
                override fun run() {
                    ApplicationManager.getApplication().invokeLater(object : Runnable {
                        override fun run() {
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
                                    } else {
                                        // all usages filtered out
                                    }
                                } else if (visibleNodes.size == 1) {
                                    if (usages.size == 1) {
                                        //the only usage
                                        val usage = visibleNodes.iterator().next()!!.getUsage()
                                        usage.navigate(true)
                                        //String message = UsageViewBundle.message("show.usages.only.usage", searchScopePresentableName(options, project));
                                        //navigateAndHint(usage, message, handler, popupPosition, maxUsages, options);
                                        popup.cancel()
                                    } else {
                                        assert(usages.size > 1) { usages }
                                        // usage view can filter usages down to one
                                        val visibleUsage = visibleNodes.iterator().next()!!.getUsage()
                                        if (areAllUsagesInOneLine(visibleUsage, usages)) {
                                            val hint = UsageViewBundle.message(
                                                "all.usages.are.in.this.line",
                                                usages.size,
                                                searchScopePresentableName(options, project)
                                            )
                                            navigateAndHint(
                                                visibleUsage,
                                                hint,
                                                handler,
                                                popupPosition,
                                                maxUsages,
                                                options
                                            )
                                            popup.cancel()
                                        }
                                    }
                                } else {
                                    val title = presentation.getTabText()
                                    val shouldShowMoreSeparator = visibleNodes.contains(MORE_USAGES_SEPARATOR_NODE)
                                    val fullTitle: String = getFullTitle(
                                        usages,
                                        title,
                                        shouldShowMoreSeparator,
                                        visibleNodes.size - (if (shouldShowMoreSeparator) 1 else 0),
                                        false
                                    )
                                    (popup as AbstractPopup).setCaption(fullTitle)
                                }
                            }
                        }
                    }, project.getDisposed())
                }
            })
        Disposer.register(popup, object : Disposable {
            override fun dispose() {
                indicator.cancel()
            }
        })
    }

    protected fun transform(usage: Usage): Usage? {
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
        val label = createHintComponent(text, handler, popupPosition, editor, HIDE_HINTS_ACTION, maxUsages, options)
        if (editor == null || editor.isDisposed()) {
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
        cancelAction: Runnable,
        maxUsages: Int,
        options: FindUsagesOptions
    ): JComponent {
        val label: JComponent =
            HintUtil.createInformationLabel(suggestSecondInvocation(options, handler, text + "&nbsp;"))
        val button = createSettingsButton(handler, popupPosition, editor, maxUsages, cancelAction)

        val panel: JPanel = object : JPanel(BorderLayout()) {
            override fun addNotify() {
                mySearchEverywhereRunnable = object : Runnable {
                    override fun run() {
                        searchEverywhere(options, handler, editor, popupPosition, maxUsages)
                    }
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
        return InplaceButton("Settings..." + shortcutText, AllIcons.General.Settings, object : ActionListener {
            override fun actionPerformed(e: ActionEvent?) {
                SwingUtilities.invokeLater(object : Runnable {
                    override fun run() {
                        showDialogAndFindUsages(handler, popupPosition, editor, maxUsages)
                    }
                })
                cancelAction.run()
            }
        })
    }

    private fun showDialogAndFindUsages(
        handler: FindUsagesHandler,
        popupPosition: RelativePoint,
        editor: Editor?,
        maxUsages: Int
    ) {
        val dialog = handler.getFindUsagesDialog(false, false, false)
        dialog.show()
        if (dialog.isOK()) {
            dialog.calcFindUsagesOptions()
            showElementUsages(handler, editor, popupPosition, maxUsages, getDefaultOptions(handler))
        }
    }

    private fun createUsagePopup(
        usages: MutableList<Usage>,
        descriptor: TargetElementsDescriptor,
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
        table.setRowHeight(PlatformIcons.CLASS_ICON.getIconHeight() + 2)
        table.setShowGrid(false)
        table.setShowVerticalLines(false)
        table.setShowHorizontalLines(false)
        table.setTableHeader(null)
        table.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN)
        table.setIntercellSpacing(Dimension(0, 0))

        val builder: PopupChooserBuilder<*> = PopupChooserBuilder<Any?>(table)
        val title = presentation.getTabText()
        if (title != null) {
            val result: String = getFullTitle(usages, title, hadMoreSeparator, visibleNodes.size - 1, true)
            builder.setTitle(result)
            builder.setAdText(getSecondInvocationTitle(options, handler))
        }

        builder.setMovable(true).setResizable(true)
        builder.setItemChoosenCallback(object : Runnable {
            override fun run() {
                val selected = table.getSelectedRows()
                for (i in selected) {
                    val value = table.getValueAt(i, 0)
                    if (value is UsageNode) {
                        val usage = value.getUsage()
                        if (usage === MORE_USAGES_SEPARATOR) {
                            appendMoreUsages(editor, popupPosition, handler, maxUsages)
                            return
                        }
                        navigateAndHint(usage, null, handler, popupPosition, maxUsages, options)
                    }
                }
            }
        })
        val popup: Array<JBPopup?> = arrayOfNulls(1)

        var shortcut = UsageViewImpl.getShowUsagesWithSettingsShortcut()
        if (shortcut != null) {
            object : DumbAwareAction() {
                override fun actionPerformed(e: AnActionEvent) {
                    popup.first()?.cancel()
                    showDialogAndFindUsages(handler, popupPosition, editor, maxUsages)
                }
            }.registerCustomShortcutSet(CustomShortcutSet(shortcut.getFirstKeyStroke()), table)
        }
        shortcut = showUsagesShortcut
        if (shortcut != null) {
            object : DumbAwareAction() {
                override fun actionPerformed(e: AnActionEvent) {
                    popup.first()?.cancel()
                    searchEverywhere(options, handler, editor, popupPosition, maxUsages)
                }
            }.registerCustomShortcutSet(CustomShortcutSet(shortcut.getFirstKeyStroke()), table)
        }

        val settingsButton = createSettingsButton(handler, popupPosition, editor, maxUsages, object : Runnable {
            override fun run() {
                popup.first()?.cancel()
            }
        })

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

        toolbar.add(UsageGroupingRuleProviderImpl.createGroupByFileStructureAction(usageView))
        toolbar.add(object : AnAction(
            "Open Find Usages Toolwindow",
            "Show all usages in a separate toolwindow",
            AllIcons.Toolwindows.ToolWindowFind
        ) {
            init {
                val action = ActionManager.getInstance().getAction(IdeActions.ACTION_FIND_USAGES)
                setShortcutSet(action.getShortcutSet())
            }

            override fun actionPerformed(e: AnActionEvent) {
                hideHints()
                popup.first()?.cancel()
                val findUsagesManager =
                    (FindManager.getInstance(usageView.getProject()) as FindManagerImpl).getFindUsagesManager()

                findUsagesManager.findUsages(
                    handler.getPrimaryElements(), handler.getSecondaryElements(), handler, options,
                    FindSettings.getInstance().isSkipResultsWithOneUsage()
                )
            }
        })

        val actionToolbar =
            ActionManager.getInstance().createActionToolbar(ActionPlaces.USAGE_VIEW_TOOLBAR, toolbar, true)
        actionToolbar.setReservePlaceAutoPopupIcon(false)
        val toolBar = actionToolbar.getComponent()
        toolBar.setOpaque(false)
        builder.setSettingButton(toolBar)

        popup[0] = builder.createPopup()
        val content = popup.first()?.getContent()

        myWidth = ((toolBar.getPreferredSize().getWidth()
                + JLabel(
            getFullTitle(
                usages,
                title!!,
                hadMoreSeparator,
                visibleNodes.size - 1,
                true
            )
        ).getPreferredSize().getWidth()
                + settingsButton.getPreferredSize().getWidth())).toInt()
        myWidth = -1
        for (action in toolbar.getChildren(null as AnActionEvent?)) {
            action.unregisterCustomShortcutSet(usageView.getComponent())
            action.registerCustomShortcutSet(action.getShortcutSet(), content)
        }

        return popup.first()!!
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

        val title = presentation.getTabText()
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
        val existingData = tableModel.getItems()

        val row = table.getSelectedRow()

        var newSelection: Int = updateModel(tableModel, existingData, data, if (row == -1) 0 else row)
        if (newSelection < 0 || newSelection >= tableModel.getRowCount()) {
            ScrollingUtil.ensureSelectionExists(table)
            newSelection = table.getSelectedRow()
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
        val content = popup.getContent()
        val window = SwingUtilities.windowForComponent(content)
        val d = window.getSize()

        var width: Int = calcMaxWidth(table)
        width = max(d.getWidth(), width.toDouble()).toInt()
        val headerSize = (popup as AbstractPopup).getHeaderPreferredSize()
        width = max(headerSize.getWidth().toInt(), width)
        width = max(myWidth, width)

        if (myWidth == -1) myWidth = width
        val newWidth = max(width, d.width + width - myWidth)

        myWidth = newWidth

        val rowsToShow = min(30, data.size)
        var dimension = Dimension(newWidth, table.getRowHeight() * rowsToShow)
        val rectangle: Rectangle = fitToScreen(dimension, popupPosition, table)
        dimension = rectangle.getSize()
        val location = window.getLocation()
        if (location != rectangle.getLocation()) {
            window.setLocation(rectangle.getLocation())
        }

        if (!data.isEmpty()) {
            ScrollingUtil.ensureSelectionExists(table)
        }
        table.setSize(dimension)


        //table.setPreferredSize(dimension);
        //table.setMaximumSize(dimension);
        //table.setPreferredScrollableViewportSize(dimension);
        val footerSize = popup.getFooterPreferredSize()

        val newHeight =
            (dimension.height + headerSize.getHeight() + footerSize.getHeight()).toInt() + 4 /* invisible borders, margins etc*/
        val newDim = Dimension(dimension.width, newHeight)
        window.setSize(newDim)
        window.setMinimumSize(newDim)
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
        for (node in root.getUsageNodes()) {
            val usage = node.getUsage()
            if (usageView.isVisible(usage)) {
                node.setParent(root)
                outNodes.add(node)
            }
        }
        for (groupNode in root.getSubGroups()) {
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
        if (hint == null) return
        val newEditor: Editor? = getEditorFor(usage)
        if (newEditor == null) return
        val project = handler.getProject()
        //opening editor is performing in invokeLater
        IdeFocusManager.getInstance(project).doWhenFocusSettlesDown(object : Runnable {
            override fun run() {
                newEditor.getScrollingModel().runActionOnScrollingFinished(object : Runnable {
                    override fun run() {
                        // after new editor created, some editor resizing events are still bubbling. To prevent hiding hint, invokeLater this
                        IdeFocusManager.getInstance(project).doWhenFocusSettlesDown(object : Runnable {
                            override fun run() {
                                if (newEditor.getComponent().isShowing()) {
                                    showHint(hint, newEditor, popupPosition, handler, maxUsages, options)
                                }
                            }
                        })
                    }
                })
            }
        })
    }

    private class MyTable : JTable(), DataProvider {
        override fun getScrollableTracksViewportWidth(): Boolean {
            return true
        }

        override fun getData(dataId: @NonNls String): Any? {
            if (LangDataKeys.PSI_ELEMENT.`is`(dataId)) {
                val selected = getSelectedRows()
                if (selected.size == 1) {
                    return getPsiElementForHint(getValueAt(selected[0], 0))
                }
            }
            return null
        }

        fun getPsiElementForHint(selectedValue: Any?): PsiElement? {
            if (selectedValue is UsageNode) {
                val usage = selectedValue.getUsage()
                if (usage is UsageInfo2UsageAdapter) {
                    val element = usage.getElement()
                    if (element != null) {
                        val view = UsageToPsiElementProvider.findAppropriateParentFrom(element)
                        return if (view == null) element else view
                    }
                }
            }
            return null
        }
    }

    internal class StringNode(private val myString: Any) : UsageNode(null, NullUsage.INSTANCE) {
        override fun toString(): String {
            return myString.toString()
        }
    }

    @Suppress("removal", "OVERRIDE_DEPRECATION")
    private class MySpeedSearch(table: MyTable) : SpeedSearchBase<JTable?>(table) {
        override fun getSelectedIndex(): Int {
            return this.table!!.getSelectedRow()
        }

        override fun convertIndexToModel(viewIndex: Int): Int {
            return this.table!!.convertRowIndexToModel(viewIndex)
        }

        override fun getAllElements(): Array<Any?> {
            return (this.table!!.getModel() as MyModel).getItems().toTypedArray()
        }

        override fun getElementText(element: Any): String? {
            if (element !is UsageNode) return element.toString()
            val node = element
            if (node is StringNode) return ""
            val usage = node.getUsage()
            if (usage === MORE_USAGES_SEPARATOR) return ""
            val group = node.getParent() as GroupNode?
            return usage.getPresentation().getPlainText() + group
        }

        override fun selectElement(element: Any?, selectedText: String?) {
            val data = (this.table!!.getModel() as MyModel).getItems()
            val i = data.indexOf(element as UsageNode?)
            if (i == -1) return
            val viewRow = this.table!!.convertRowIndexToView(i)
            this.table!!.getSelectionModel().setSelectionInterval(viewRow, viewRow)
            TableUtil.scrollSelectionToVisible(this.table!!)
        }

        val table: MyTable?
            get() = myComponent as MyTable?
    }

    companion object {
        private const val USAGES_PAGE_SIZE = 100

        val MORE_USAGES_SEPARATOR: NullUsage = NullUsage.INSTANCE
        private val MORE_USAGES_SEPARATOR_NODE: UsageNode = UsageViewImpl.NULL_NODE

        private val USAGE_NODE_COMPARATOR: Comparator<UsageNode> = object : Comparator<UsageNode> {
            override fun compare(c1: UsageNode, c2: UsageNode): Int {
                if (c1 is StringNode) return 1
                if (c2 is StringNode) return -1
                val o1 = c1.getUsage()
                val o2 = c2.getUsage()
                if (o1 === MORE_USAGES_SEPARATOR) return 1
                if (o2 === MORE_USAGES_SEPARATOR) return -1

                val v1 = UsageListCellRenderer.getVirtualFile(o1)
                val v2 = UsageListCellRenderer.getVirtualFile(o2)
                val name1 = if (v1 == null) null else v1.getName()
                val name2 = if (v2 == null) null else v2.getName()
                val i = Comparing.compare<String?>(name1, name2)
                if (i != 0) return i

                if (o1 is Comparable<*> && o2 is Comparable<*>) {
                    return (o1 as Comparable<Any>).compareTo(o2)
                }

                val loc1 = o1.getLocation()
                val loc2 = o2.getLocation()
                return Comparing.compare<FileEditorLocation?>(loc1, loc2)
            }
        }
        private val HIDE_HINTS_ACTION: Runnable = object : Runnable {
            override fun run() {
                hideHints()
            }
        }

        fun chooseAmbiguousTargetAndPerform(
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
                val offset = editor.getCaretModel().getOffset()
                val chosen = GotoDeclarationAction.chooseAmbiguousTarget(
                    editor, offset, processor,
                    FindBundle.message("find.usages.ambiguous.title", "crap"), null
                )
                if (!chosen) {
                    ApplicationManager.getApplication().invokeLater(object : Runnable {
                        override fun run() {
                            if (editor.isDisposed() || !editor.getComponent().isShowing()) return
                            HintManager.getInstance()
                                .showErrorHint(editor, FindBundle.message("find.no.usages.at.cursor.error"))
                        }
                    }, project.getDisposed())
                }
            }
        }


        private fun hideHints() {
            HintManager.getInstance().hideHints(HintManager.HIDE_BY_ANY_KEY, false, false)
        }

        private fun getDefaultOptions(handler: FindUsagesHandler): FindUsagesOptions {
            val options = handler.getFindUsagesOptions()
            // by default, scope in FindUsagesOptions is copied from the FindSettings, but we need a default one
            options.searchScope = FindUsagesManager.getMaximalScope(handler)
            return options
        }

        private fun createStringNode(string: Any): UsageNode {
            return StringNode(string)
        }

        private fun showPopupIfNeedTo(popup: JBPopup, popupPosition: RelativePoint): Boolean {
            if (!popup.isDisposed() && !popup.isVisible()) {
                popup.show(popupPosition)
                return true
            } else {
                return false
            }
        }

        private fun searchScopePresentableName(options: FindUsagesOptions, project: Project): String {
            return notNullizeScope(options, project).getDisplayName()
        }

        private fun notNullizeScope(options: FindUsagesOptions, project: Project): SearchScope {
            val scope = options.searchScope
            if (scope == null) return ProjectScope.getAllScope(project)
            return scope
        }

        private fun getFullTitle(
            usages: MutableList<Usage>,
            title: String,
            hadMoreSeparator: Boolean,
            visibleNodesCount: Int,
            findUsagesInProgress: Boolean
        ): String {
            val s: String
            if (hadMoreSeparator) {
                s =
                    "<b>Some</b> " + title + " " + "<b>(Only " + visibleNodesCount + " usages shown" + (if (findUsagesInProgress) " so far" else "") + ")</b>"
            } else {
                s = title + " (" + UsageViewBundle.message(
                    "usages.n",
                    usages.size
                ) + (if (findUsagesInProgress) " so far" else "") + ")"
            }
            return "<html><nobr>" + s + "</nobr></html>"
        }

        private fun suggestSecondInvocation(
            options: FindUsagesOptions,
            handler: FindUsagesHandler,
            text: String
        ): String {
            var text = text
            val title: String? = getSecondInvocationTitle(options, handler)

            if (title != null) {
                text += "<br><small>Press " + title + "</small>"
            }
            return "<html><body>" + text + "</body></html>"
        }

        private fun getSecondInvocationTitle(options: FindUsagesOptions, handler: FindUsagesHandler): String? {
            if (showUsagesShortcut != null) {
                val maximalScope = FindUsagesManager.getMaximalScope(handler)
                if (notNullizeScope(options, handler.getProject()) != maximalScope) {
                    return "Press " + KeymapUtil.getShortcutText(showUsagesShortcut!!) + " again to search in " + maximalScope.getDisplayName()
                }
            }
            return null
        }

        private val showUsagesShortcut: KeyboardShortcut?
            get() = ActionManager.getInstance().getKeyboardShortcut("ShowUsages")

        private fun filtered(usages: MutableList<Usage>, usageView: UsageViewImpl): Int {
            var count = 0
            for (usage in usages) {
                if (!usageView.isVisible(usage)) count++
            }
            return count
        }

        private fun getUsageOffset(usage: Usage): Int {
            if (usage !is UsageInfo2UsageAdapter) return -1
            val element = usage.getElement()
            if (element == null) return -1
            return element.getTextRange().getStartOffset()
        }

        private fun areAllUsagesInOneLine(visibleUsage: Usage, usages: MutableList<Usage>): Boolean {
            val editor: Editor? = getEditorFor(visibleUsage)
            if (editor == null) return false
            val offset: Int = getUsageOffset(visibleUsage)
            if (offset == -1) return false
            val lineNumber = editor.getDocument().getLineNumber(offset)
            for (other in usages) {
                val otherEditor: Editor? = getEditorFor(other)
                if (otherEditor !== editor) return false
                val otherOffset: Int = getUsageOffset(other)
                if (otherOffset == -1) return false
                val otherLine = otherEditor.getDocument().getLineNumber(otherOffset)
                if (otherLine != lineNumber) return false
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
            var model = if (table.getModel() is MyModel) table.getModel() as MyModel? else null
            if (model == null || model.getColumnCount() != columnCount) {
                model = MyModel(data, columnCount)
                table.setModel(model)

                val renderer = ShowUsagesTableCellRenderer(usageView)
                for (i in 0..<table.getColumnModel().getColumnCount()) {
                    val column = table.getColumnModel().getColumn(i)
                    column.setCellRenderer(renderer)
                }
            }
            return model
        }

        private fun calcColumnCount(data: MutableList<UsageNode?>): Int {
            return if (data.isEmpty() || data.get(0) is StringNode) 1 else 3
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
            Collections.sort<UsageNode?>(data, USAGE_NODE_COMPARATOR)
            return data
        }

        private fun calcMaxWidth(table: JTable): Int {
            val colsNum = table.getColumnModel().getColumnCount()

            var totalWidth = 0
            for (col in 0..<colsNum - 1) {
                val column = table.getColumnModel().getColumn(col)
                val preferred = column.getPreferredWidth()
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

                val rendererWidth = component.getPreferredSize().width
                width = max(width, rendererWidth + table.getIntercellSpacing().width)
            }
            return width
        }

        // returns new selection
        private fun updateModel(
            tableModel: MyModel,
            listOld: MutableList<UsageNode?>,
            listNew: MutableList<UsageNode?>,
            oldSelection: Int
        ): Int {
            val oa = listOld.toTypedArray<UsageNode?>()
            val na = listNew.toTypedArray<UsageNode?>()
            val cmds = ModelDiff.createDiffCmds<Any?>(tableModel, oa, na)
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
            val rectangle = Rectangle(popupPosition.getScreenPoint(), newDim)
            ScreenUtil.fitToScreen(rectangle)
            if (rectangle.getHeight() != newDim.getHeight()) {
                val newHeight = rectangle.getHeight().toInt()
                val roundedHeight = newHeight - newHeight % table.getRowHeight()
                rectangle.setSize(rectangle.getWidth().toInt(), max(roundedHeight, table.getRowHeight()))
            }
            return rectangle
        }

        private fun getEditorFor(usage: Usage): Editor? {
            val location = usage.getLocation()
            val newFileEditor = if (location == null) null else location.getEditor()
            return if (newFileEditor is TextEditor) newFileEditor.getEditor() else null
        }
    }
}
