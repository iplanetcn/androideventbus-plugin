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
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Rectangle
import java.util.*
import javax.swing.*
import kotlin.math.max
import kotlin.math.min

class ShowUsagesAction : AnAction, PopupAction {
    private var filter: Filter? = null

    private val myUsageViewSettings: UsageViewSettings
    private var mySearchEverywhereRunnable: Runnable? = null

    constructor(filter: Filter) {
        this.filter = filter
    }
    
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

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }


    override fun actionPerformed(e: AnActionEvent) {
        val project = e.getData(PlatformDataKeys.PROJECT)
        if (project == null) return

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
            chooseAmbiguousTargetAndPerform(project, editor) { element ->
                startFindUsages(element, popupPosition, editor, USAGES_PAGE_SIZE)
                false
            }
        } else {
            val element = (usageTargets[0] as PsiElementUsageTarget).element
            if (element != null) {
                startFindUsages(element, popupPosition, editor, USAGES_PAGE_SIZE)
            }
        }
    }

    fun startFindUsages(element: PsiElement, popupPosition: RelativePoint, editor: Editor?, maxUsages: Int) {
        val project = element.project
        val findUsagesManager = (FindManager.getInstance(project) as FindManagerImpl).findUsagesManager
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

        val project = handler.project
        val manager = UsageViewManager.getInstance(project)
        val findUsagesManager = (FindManager.getInstance(project) as FindManagerImpl).findUsagesManager
        val presentation = findUsagesManager.createPresentation(handler, options)
        presentation.isDetachedMode = true
        val usageView = manager.createUsageView(UsageTarget.EMPTY_ARRAY, Usage.EMPTY_ARRAY, presentation, null) as UsageViewImpl

        Disposer.register(usageView) {
            myUsageViewSettings.loadState(usageViewSettings)
            usageViewSettings.loadState(savedGlobalSettings)
        }

        val usages: MutableList<Usage> = ArrayList<Usage>()
        val visibleNodes: MutableSet<UsageNode?> = LinkedHashSet<UsageNode?>()
        val descriptor = TargetElementsDescriptor(handler.primaryElements, handler.secondaryElements)

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
            usages, descriptor, visibleNodes, handler, editor, popupPosition,
            maxUsages, usageView, options, table, presentation, processIcon, hadMoreSeparator
        )

        Disposer.register(popup, usageView)

        // show popup only if you find usages takes more than 300ms, otherwise it would flicker needlessly
        val alarm = Alarm(usageView)
        alarm.addRequest({ showPopupIfNeedTo(popup, popupPosition) }, 300)

        val pingEDT = PingEDT("Rebuild popup in EDT", Condition<Any?> { popup.isDisposed }, 100, object : Runnable {
            override fun run() {
                if (popup.isDisposed) return

                val nodes: MutableList<UsageNode?> = ArrayList<UsageNode?>()
                val copy: MutableList<Usage>?
                synchronized(usages) {
                    // open up popup as soon as several usages 've been found
                    if (!popup.isVisible && (usages.size <= 1 || !showPopupIfNeedTo(popup, popupPosition))) {
                        return
                    }
                    addUsageNodes(usageView.root, usageView, nodes)
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
                    !processIcon.isDisposed
                )
            }
        })

        val messageBusConnection = project.messageBus.connect(usageView)
        messageBusConnection.subscribe<Runnable>(UsageFilteringRuleProvider.RULES_CHANGED!!, Runnable { pingEDT.ping() })


        val collect: Processor<Usage> = object : Processor<Usage> {
            private val myUsageTarget = arrayOf<UsageTarget?>(PsiElement2UsageTargetAdapter(handler.psiElement, true))

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
            handler.primaryElements,
            handler.secondaryElements,
            collect,
            options
        ) {
            ApplicationManager.getApplication().invokeLater({
                Disposer.dispose(processIcon)
                val parent = processIcon.parent
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
                            val usage = visibleNodes.iterator().next()!!.usage
                            usage.navigate(true)
                            //String message = UsageViewBundle.message("show.usages.only.usage", searchScopePresentableName(options, project));
                            //navigateAndHint(usage, message, handler, popupPosition, maxUsages, options);
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
                        val title = presentation.tabText
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
            }, project.disposed)
        }
        Disposer.register(popup) { indicator.cancel() }
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
        if (editor == null || editor.isDisposed) {
            HintManager.getInstance().showHint(
                label, popupPosition, HintManager.HIDE_BY_ANY_KEY or HintManager.HIDE_BY_TEXT_CHANGE or HintManager.HIDE_BY_SCROLLING, 0
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
                mySearchEverywhereRunnable = Runnable { searchEverywhere(options, handler, editor, popupPosition, maxUsages) }
                super.addNotify()
            }

            override fun removeNotify() {
                mySearchEverywhereRunnable = null
                super.removeNotify()
            }
        }
        button.background = label.background
        panel.background = label.background
        label.isOpaque = false
        label.border = null
        panel.border = HintUtil.createHintBorder()
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
        return InplaceButton("Settings..." + shortcutText, AllIcons.General.Settings) {
            SwingUtilities.invokeLater { showDialogAndFindUsages(handler, popupPosition, editor, maxUsages) }
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
        table.rowHeight = PlatformIcons.CLASS_ICON.iconHeight + 2
        table.setShowGrid(false)
        table.setShowVerticalLines(false)
        table.setShowHorizontalLines(false)
        table.tableHeader = null
        table.autoResizeMode = JTable.AUTO_RESIZE_LAST_COLUMN
        table.intercellSpacing = Dimension(0, 0)

        val builder: PopupChooserBuilder<*> = PopupChooserBuilder<Any?>(table)
        val title = presentation.tabText
        if (title != null) {
            val result: String = getFullTitle(usages, title, hadMoreSeparator, visibleNodes.size - 1, true)
            builder.setTitle(result)
            builder.setAdText(getSecondInvocationTitle(options, handler))
        }

        builder.setMovable(true).setResizable(true)
        builder.setItemChosenCallback(object : Runnable {
            override fun run() {
                val selected = table.selectedRows
                for (i in selected) {
                    val value = table.getValueAt(i, 0)
                    if (value is UsageNode) {
                        val usage = value.usage
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
            }.registerCustomShortcutSet(CustomShortcutSet(shortcut.firstKeyStroke), table)
        }
        shortcut = showUsagesShortcut
        if (shortcut != null) {
            object : DumbAwareAction() {
                override fun actionPerformed(e: AnActionEvent) {
                    popup.first()?.cancel()
                    searchEverywhere(options, handler, editor, popupPosition, maxUsages)
                }
            }.registerCustomShortcutSet(CustomShortcutSet(shortcut.firstKeyStroke), table)
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
                shortcutSet = action.shortcutSet
            }

            override fun actionPerformed(e: AnActionEvent) {
                hideHints()
                popup.first()?.cancel()
                val findUsagesManager = (FindManager.getInstance(usageView.project) as FindManagerImpl).findUsagesManager

                findUsagesManager.findUsages(
                    handler.primaryElements, handler.secondaryElements, handler, options,
                    FindSettings.getInstance().isSkipResultsWithOneUsage
                )
            }
        })

        val actionToolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.USAGE_VIEW_TOOLBAR, toolbar, true)
        actionToolbar.isReservePlaceAutoPopupIcon = false
        val toolBar = actionToolbar.component
        toolBar.isOpaque = false
        builder.setSettingButton(toolBar)

        popup[0] = builder.createPopup()
        val content = popup.first()?.content

        myWidth = ((toolBar.preferredSize.width
                + JLabel(
            getFullTitle(
                usages,
                title!!,
                hadMoreSeparator,
                visibleNodes.size - 1,
                true
            )
        ).preferredSize.width + settingsButton.preferredSize.width))
        myWidth = -1
        for (action in toolbar.getChildren(null as AnActionEvent?)) {
            action.unregisterCustomShortcutSet(usageView.component)
            action.registerCustomShortcutSet(action.shortcutSet, content)
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
            ScrollingUtil.ensureSelectionExists(table)
            newSelection = table.selectedRow
        } else {
            table.selectionModel.setSelectionInterval(newSelection, newSelection)
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
        width = max(d.width, width)
        val headerSize = (popup as AbstractPopup).headerPreferredSize
        width = max(headerSize.width, width)
        width = max(myWidth, width)

        if (myWidth == -1) myWidth = width
        val newWidth = max(width, d.width + width - myWidth)

        myWidth = newWidth

        val rowsToShow = min(30, data.size)
        var dimension = Dimension(newWidth, table.rowHeight * rowsToShow)
        val rectangle: Rectangle = fitToScreen(dimension, popupPosition, table)
        dimension = rectangle.size
        val location = window.location
        if (location != rectangle.location) {
            window.location = rectangle.location
        }

        if (data.isNotEmpty()) {
            ScrollingUtil.ensureSelectionExists(table)
        }
        table.size = dimension


        //table.setPreferredSize(dimension);
        //table.setMaximumSize(dimension);
        //table.setPreferredScrollableViewportSize(dimension);
        val footerSize = popup.footerPreferredSize

        val newHeight = (dimension.height + headerSize.height + footerSize.height) + 4 /* invisible borders, margins etc*/
        val newDim = Dimension(dimension.width, newHeight)
        window.size = newDim
        window.minimumSize = newDim
        window.maximumSize = newDim

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
        if (hint == null) return
        val newEditor: Editor? = getEditorFor(usage)
        if (newEditor == null) return
        val project = handler.project
        //opening editor is performing in invokeLater
        IdeFocusManager.getInstance(project).doWhenFocusSettlesDown {
            newEditor.scrollingModel.runActionOnScrollingFinished { // after new editor created, some editor resizing events are still bubbling. To prevent hiding hint, invokeLater this
                IdeFocusManager.getInstance(project).doWhenFocusSettlesDown {
                    if (newEditor.component.isShowing) {
                        showHint(hint, newEditor, popupPosition, handler, maxUsages, options)
                    }
                }
            }
        }
    }

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

    internal class StringNode(private val myString: Any) : UsageNode(null, NullUsage.INSTANCE) {
        override fun toString(): String {
            return myString.toString()
        }
    }

    @Suppress("removal", "OVERRIDE_DEPRECATION")
    private class MySpeedSearch(table: MyTable) : SpeedSearchBase<JTable?>(table) {
        override fun getSelectedIndex(): Int {
            return this.table!!.selectedRow
        }

        override fun convertIndexToModel(viewIndex: Int): Int {
            return this.table!!.convertRowIndexToModel(viewIndex)
        }

        override fun getAllElements(): Array<Any?> {
            return (this.table!!.model as MyModel).items.toTypedArray()
        }

        override fun getElementText(element: Any): String? {
            if (element !is UsageNode) return element.toString()
            val node = element
            if (node is StringNode) return ""
            val usage = node.usage
            if (usage === MORE_USAGES_SEPARATOR) return ""
            val group = node.parent as GroupNode?
            return usage.presentation.plainText + group
        }

        override fun selectElement(element: Any?, selectedText: String?) {
            val data = (this.table!!.model as MyModel).items
            val i = data.indexOf(element as UsageNode?)
            if (i == -1) return
            val viewRow = this.table!!.convertRowIndexToView(i)
            this.table!!.selectionModel.setSelectionInterval(viewRow, viewRow)
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
                val o1 = c1.usage
                val o2 = c2.usage
                if (o1 === MORE_USAGES_SEPARATOR) return 1
                if (o2 === MORE_USAGES_SEPARATOR) return -1

                val v1 = UsageListCellRenderer.getVirtualFile(o1)
                val v2 = UsageListCellRenderer.getVirtualFile(o2)
                val name1 = v1?.name
                val name2 = v2?.name
                val i = Comparing.compare<String>(name1, name2)
                if (i != 0) return i

                if (o1 is Comparable<*> && o2 is Comparable<*>) {
                    @Suppress("UNCHECKED_CAST")
                    return (o1 as Comparable<Any>).compareTo(o2 as Any)
                }

                val loc1 = o1.location
                val loc2 = o2.location
                return Comparing.compare<FileEditorLocation?>(loc1, loc2)
            }
        }
        private val HIDE_HINTS_ACTION: Runnable = Runnable { hideHints() }

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
                val offset = editor.caretModel.offset
                val chosen = GotoDeclarationAction.chooseAmbiguousTarget(
                    editor, offset, processor,
                    FindBundle.message("find.usages.ambiguous.title", "crap"), null
                )
                if (!chosen) {
                    ApplicationManager.getApplication().invokeLater(object : Runnable {
                        override fun run() {
                            if (editor.isDisposed || !editor.component.isShowing) return
                            HintManager.getInstance()
                                .showErrorHint(editor, FindBundle.message("find.no.usages.at.cursor.error"))
                        }
                    }, project.disposed)
                }
            }
        }


        private fun hideHints() {
            HintManager.getInstance().hideHints(HintManager.HIDE_BY_ANY_KEY, false, false)
        }

        private fun getDefaultOptions(handler: FindUsagesHandler): FindUsagesOptions {
            val options = handler.findUsagesOptions
            // by default, scope in FindUsagesOptions is copied from the FindSettings, but we need a default one
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
            @Suppress("SENSELESS_COMPARISON")
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
                if (!usageView.isVisible(usage)) count++
            }
            return count
        }

        private fun getUsageOffset(usage: Usage): Int {
            if (usage !is UsageInfo2UsageAdapter) return -1
            val element = usage.element
            if (element == null) return -1
            return element.textRange.startOffset
        }

        private fun areAllUsagesInOneLine(visibleUsage: Usage, usages: MutableList<Usage>): Boolean {
            val editor: Editor? = getEditorFor(visibleUsage)
            if (editor == null) return false
            val offset: Int = getUsageOffset(visibleUsage)
            if (offset == -1) return false
            val lineNumber = editor.document.getLineNumber(offset)
            for (other in usages) {
                val otherEditor: Editor? = getEditorFor(other)
                if (otherEditor !== editor) return false
                val otherOffset: Int = getUsageOffset(other)
                if (otherOffset == -1) return false
                val otherLine = otherEditor.document.getLineNumber(otherOffset)
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
            var model = if (table.model is MyModel) table.model as MyModel? else null
            if (model == null || model.columnCount != columnCount) {
                model = MyModel(data, columnCount)
                table.model = model

                val renderer = ShowUsagesTableCellRenderer(usageView)
                for (i in 0..<table.columnModel.columnCount) {
                    val column = table.columnModel.getColumn(i)
                    column.cellRenderer = renderer
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
            Collections.sort<UsageNode?>(data, USAGE_NODE_COMPARATOR)
            return data
        }

        private fun calcMaxWidth(table: JTable): Int {
            val colsNum = table.columnModel.columnCount

            var totalWidth = 0
            for (col in 0..<colsNum - 1) {
                val column = table.columnModel.getColumn(col)
                val preferred = column.preferredWidth
                val width = max(preferred, columnMaxWidth(table, col))
                totalWidth += width
                column.minWidth = width
                column.maxWidth = width
                column.width = width
                column.preferredWidth = width
            }

            totalWidth += columnMaxWidth(table, colsNum - 1)

            return totalWidth
        }

        private fun columnMaxWidth(table: JTable, col: Int): Int {
            val column = table.columnModel.getColumn(col)
            var width = 0
            for (row in 0..<table.rowCount) {
                val component = table.prepareRenderer(column.cellRenderer, row, col)

                val rendererWidth = component.preferredSize.width
                width = max(width, rendererWidth + table.intercellSpacing.width)
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
            val rectangle = Rectangle(popupPosition.screenPoint, newDim)
            ScreenUtil.fitToScreen(rectangle)
            if (rectangle.height != newDim.height) {
                val newHeight = rectangle.height
                val roundedHeight = newHeight - newHeight % table.rowHeight
                rectangle.setSize(rectangle.width, max(roundedHeight, table.rowHeight))
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
