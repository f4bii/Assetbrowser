package de.fabihbbbt.assetbrowser.client.gui

import de.fabihbbbt.assetbrowser.client.gui.AssetBrowserStyle.PREVIEW_PADDING
import de.fabihbbbt.assetbrowser.client.gui.export.AssetExportActions
import de.fabihbbbt.assetbrowser.client.gui.index.AssetIndex
import de.fabihbbbt.assetbrowser.client.gui.index.AssetIndex.AssetRecord
import de.fabihbbbt.assetbrowser.client.gui.index.AssetIndex.Node
import de.fabihbbbt.assetbrowser.client.gui.index.AssetIndexCache
import de.fabihbbbt.assetbrowser.client.gui.preview.AssetFilePreview
import de.fabihbbbt.assetbrowser.client.gui.preview.AssetPreviewPane
import de.fabihbbbt.assetbrowser.client.gui.widget.AssetFilterDropdown
import de.fabihbbbt.assetbrowser.client.gui.widget.AssetLinkPanel
import de.fabihbbbt.assetbrowser.client.gui.widget.AssetSidebarList
import de.fabihbbbt.assetbrowser.client.gui.widget.SplitterLayout
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.StringWidget
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout
import net.minecraft.client.gui.layouts.LinearLayout
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import java.util.ArrayDeque
import java.util.Deque
import java.util.Locale
import java.util.TreeSet
import kotlin.math.sign

class LoadedAssetsScreen(
    private val parent: Screen,
) : Screen(TITLE) {
    private val index: AssetIndex = AssetIndexCache.getOrBuild(minecraft)
    private val backStack: Deque<Identifier> = ArrayDeque()

    private var visibleAssets: List<AssetRecord> = index.all()
    var treeRoot: Node = AssetIndex.buildTree(visibleAssets)
        private set
    private var scanWasPending = true

    private lateinit var searchBox: EditBox
    private lateinit var packFilter: AssetFilterDropdown
    private lateinit var typeFilter: AssetFilterDropdown
    private lateinit var statusWidget: StringWidget
    private lateinit var sidebarList: AssetSidebarList
    private lateinit var copyButton: Button
    private lateinit var revealButton: Button
    private lateinit var backButton: Button
    private lateinit var playButton: Button
    private lateinit var copyAllButton: Button
    private lateinit var readAsTextButton: Button
    private var selectedGroup: Node? = null
    private var copyFeedbackTicks = 0
    private var lastShownCount = 0

    private val splitters = SplitterLayout(PANE_GAP)
    private val layout = HeaderAndFooterLayout(this, HEADER_HEIGHT, FOOTER_HEIGHT)

    private val previewPane = AssetPreviewPane(minecraft, this::updatePlayButtonLabel)
    val previewRecord: AssetRecord? get() = previewPane.record

    private val usedByPanel = AssetLinkPanel()
    private val referencesPanel = AssetLinkPanel()
    private val exportActions: AssetExportActions =
        AssetExportActions(
            minecraft,
            { msg -> statusWidget.setMessage(msg) },
            { copyFeedbackTicks = COPY_FEEDBACK_TICKS },
        )

    init {
        splitters.applySettings(LoadedAssetsScreenSettings.load())
    }

    override fun init() {
        val header = layout.addToHeader(LinearLayout.vertical().spacing(4))
        header.defaultCellSetting().alignHorizontallyCenter()
        header.addChild(StringWidget(getTitle(), font))
        statusWidget = header.addChild(StringWidget(Component.empty(), font))

        val filterRow = header.addChild(LinearLayout.horizontal().spacing(4))

        searchBox = filterRow.addChild(EditBox(font, 0, 0, 200, 20, Component.empty()))
        searchBox.setHint(SEARCH_HINT)
        searchBox.setResponder { value -> applyFilter(value) }

        packFilter =
            AssetFilterDropdown("Pack: ", PACK_BUTTON_WIDTH, listOf(ALL_PACKS) + index.packIds(), ALL_PACKS) { refreshFromFilters() }
        filterRow.addChild(packFilter.button)

        typeFilter = AssetFilterDropdown("Type: ", TYPE_BUTTON_WIDTH, computeExtensionOptions(), ALL_TYPES) { refreshFromFilters() }
        filterRow.addChild(typeFilter.button)

        sidebarList = layout.addToContents(AssetSidebarList(this, minecraft, width, layout.contentHeight, layout.headerHeight))

        copyAllButton = actionButton("Copy all", 90) { onCopyAll() }
        backButton = actionButton("< Back", 44) { goBack() }
        playButton = actionButton("Play", 50) { togglePlayback() }
        readAsTextButton = actionButton("As Text", 60) { onReadAsText() }
        revealButton = actionButton("Reveal", 60) { onReveal() }
        copyButton = actionButton("Copy", 50) { onCopy() }
        refreshActionButtons()

        layout.addToFooter(Button.builder(CommonComponents.GUI_BACK) { onClose() }.build())
        layout.visitWidgets(this::addRenderableWidget)
        repositionElements()

        refreshFromFilters()
    }

    override fun setInitialFocus() {
        setInitialFocus(searchBox)
    }

    private fun actionButton(
        label: String,
        width: Int,
        onPress: () -> Unit,
    ): Button = layout.addToContents(Button.builder(Component.literal(label)) { onPress() }.size(width, ACTION_BUTTON_HEIGHT).build())

    private fun actionButtonsRightToLeft(): List<Button> =
        listOf(copyButton, revealButton, readAsTextButton, playButton, backButton, copyAllButton)

    private fun refreshActionButtons() {
        val group = selectedGroup
        if (group != null) {
            copyAllButton.setMessage(Component.literal("Copy all (${group.groupMembers().size})"))
        }
        copyAllButton.active = group != null
        backButton.active = backStack.isNotEmpty()
        playButton.active = previewPane.sound.isActive()
        readAsTextButton.active = previewPane.file.canReadAsText()
        revealButton.active = previewRecord != null
        copyButton.active = previewRecord != null
    }

    override fun repositionElements() {
        layout.arrangeElements()
        val contentY = layout.headerHeight
        val contentHeight = layout.contentHeight

        splitters.layoutWidths(width)

        sidebarList.updateSizeAndPosition(splitters.sidebarWidth, contentHeight, 0, contentY)

        val previewX = splitters.sidebarWidth + PANE_GAP
        val previewWidth = (width - previewX - PANE_GAP - splitters.linksWidth).coerceAtLeast(0)
        previewPane.setBounds(previewX, contentY, previewWidth, contentHeight)

        val usedByX = previewX + previewWidth + PANE_GAP
        val linksInnerHeight = (contentHeight - PANE_GAP).coerceAtLeast(0)
        val usedByHeight = splitters.computeUsedByHeight(linksInnerHeight)
        usedByPanel.setBounds(usedByX, contentY, splitters.linksWidth, usedByHeight)

        val referencesY = contentY + usedByHeight + PANE_GAP
        val referencesHeight = contentHeight - usedByHeight - PANE_GAP
        referencesPanel.setBounds(usedByX, referencesY, splitters.linksWidth, referencesHeight)

        var buttonRight = previewX + previewWidth - PREVIEW_PADDING
        for (button in actionButtonsRightToLeft()) {
            buttonRight -= button.getWidth()
            button.setPosition(buttonRight, contentY + PREVIEW_PADDING)
            buttonRight -= PANE_GAP
        }
    }

    private fun currentGeometry(): SplitterLayout.Geometry =
        SplitterLayout.Geometry(
            previewPane.x,
            previewPane.y,
            previewPane.width,
            previewPane.height,
            usedByPanel.x,
            usedByPanel.y,
            usedByPanel.width,
            usedByPanel.height,
        )

    override fun onClose() {
        minecraft.gui.setScreen(parent)
    }

    override fun removed() {
        previewPane.release()
        splitters.toSettings().save()
    }

    override fun tick() {
        if (copyFeedbackTicks > 0 && --copyFeedbackTicks == 0) {
            updateStatus(lastShownCount)
        }
        previewPane.tick()
        if (scanWasPending && !index.isScanning()) {
            scanWasPending = false
            refreshLinks()
        }
    }

    override fun mouseClicked(
        event: MouseButtonEvent,
        doubleClick: Boolean,
    ): Boolean {
        if (packFilter.mouseClicked(event.x(), event.y())) {
            return true
        }
        if (typeFilter.mouseClicked(event.x(), event.y())) {
            return true
        }
        if (splitters.mouseClicked(event.x(), event.y(), currentGeometry())) {
            return true
        }
        for (panel in listOf(usedByPanel, referencesPanel)) {
            val target = panel.itemAt(font, event.x(), event.y()) ?: continue
            navigateTo(target)
            return true
        }
        return previewPane.sound.isActive() && previewPane.sound.seekSlider.mouseClicked(event, doubleClick) || super.mouseClicked(event, doubleClick)
    }

    override fun mouseDragged(
        event: MouseButtonEvent,
        dragX: Double,
        dragY: Double,
    ): Boolean {
        if (splitters.mouseDragged(event.x(), event.y(), width, currentGeometry())) {
            repositionElements()
            return true
        }
        if (previewPane.sound.isActive() && previewPane.sound.seekSlider.isDragging()) {
            return previewPane.sound.seekSlider.mouseDragged(event, dragX, dragY)
        }
        return super.mouseDragged(event, dragX, dragY)
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        if (splitters.mouseReleased()) {
            splitters.toSettings().save()
            return true
        }
        if (previewPane.sound.isActive() && previewPane.sound.seekSlider.isDragging()) {
            return previewPane.sound.seekSlider.mouseReleased(event)
        }
        return super.mouseReleased(event)
    }

    override fun mouseScrolled(
        mouseX: Double,
        mouseY: Double,
        scrollX: Double,
        scrollY: Double,
    ): Boolean {
        val delta = sign(scrollY).toInt() * 3
        if (packFilter.mouseScrolled(mouseX, mouseY, delta)) {
            return true
        }
        if (typeFilter.mouseScrolled(mouseX, mouseY, delta)) {
            return true
        }
        if (previewPane.contains(mouseX, mouseY) && previewPane.file.scroll(delta)) {
            return true
        }
        if (usedByPanel.contains(mouseX, mouseY)) {
            return usedByPanel.scrollBy(delta)
        }
        if (referencesPanel.contains(mouseX, mouseY)) {
            return referencesPanel.scrollBy(delta)
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
    }

    override fun extractRenderState(
        graphics: GuiGraphicsExtractor,
        mouseX: Int,
        mouseY: Int,
        a: Float,
    ) {
        super.extractRenderState(graphics, mouseX, mouseY, a)
        previewPane.render(graphics, font, copyAllButton.x, mouseX, mouseY, a)
        usedByPanel.render(graphics, font, "Used by", index.isScanning(), mouseX, mouseY)
        referencesPanel.render(graphics, font, "References", false, mouseX, mouseY)
        splitters.render(graphics, mouseX, mouseY, currentGeometry())
        packFilter.render(graphics, font, mouseX, mouseY)
        typeFilter.render(graphics, font, mouseX, mouseY)
    }

    private fun refreshFromFilters() {
        val pack = packFilter.selected
        val type = typeFilter.selected
        visibleAssets =
            index
                .all()
                .filter { record -> ALL_PACKS == pack || record.packId == pack }
                .filter { record -> ALL_TYPES == type || hasExtension(record.id.path, type) }
        treeRoot = AssetIndex.buildTree(visibleAssets)
        applyFilter(searchBox.value)
    }

    private fun computeExtensionOptions(): List<String> {
        val extensions = TreeSet<String>()
        for ((id) in index.all()) {
            extensions.add(AssetIndex.extensionOf(id.path))
        }
        val options = ArrayList<String>()
        options.add(ALL_TYPES)
        options.addAll(extensions)
        return options
    }

    private fun applyFilter(rawFilter: String) {
        val filter = rawFilter.lowercase(Locale.ROOT).trim()
        if (filter.isEmpty()) {
            sidebarList.showTree(treeRoot)
            updateStatus(visibleAssets.size)
        } else {
            val filtered = visibleAssets.filter { record -> record.searchKey.contains(filter) }
            sidebarList.showFlat(filtered)
            updateStatus(filtered.size)
        }
    }

    private fun updateStatus(shown: Int) {
        lastShownCount = shown
        copyFeedbackTicks = 0
        val text =
            if (shown == index.all().size) {
                Component.literal("$shown assets")
            } else {
                Component.literal("$shown / ${index.all().size} assets")
            }
        statusWidget.setMessage(text)
    }

    private fun onCopy() {
        val record = previewRecord ?: return
        exportActions.copy(record.id)
    }

    private fun onReveal() {
        val record = previewRecord ?: return
        exportActions.reveal(record.id)
    }

    fun onGroupSelected(group: Node) {
        selectedGroup = group
        refreshActionButtons()
    }

    private fun onCopyAll() {
        val group = selectedGroup ?: return
        exportActions.copyAll(group.groupMembers())
    }

    fun navigateTo(id: Identifier) {
        val current = previewRecord
        if (current != null && current.id == id) {
            return
        }
        val record = index.recordOf(id) ?: return
        if (current != null) {
            backStack.addLast(current.id)
        }
        openRecord(record)
    }

    private fun goBack() {
        while (!backStack.isEmpty()) {
            val id = backStack.removeLast()
            val record = index.recordOf(id)
            if (record != null) {
                openRecord(record)
                return
            }
        }
    }

    private fun openRecord(record: AssetRecord) {
        selectedGroup = null
        referencesPanel.setItems(previewPane.open(record, index))
        refreshActionButtons()
        updatePlayButtonLabel()
        refreshLinks()
        ensureVisible(record.id)
    }

    private fun onReadAsText() {
        referencesPanel.setItems(previewPane.readAsText())
        refreshActionButtons()
    }

    private fun togglePlayback() {
        previewPane.sound.togglePlayback()
    }

    private fun updatePlayButtonLabel() {
        playButton.setMessage(Component.literal(if (previewPane.sound.isPlaying()) "Stop" else "Play"))
    }

    private fun ensureVisible(id: Identifier) {
        val record = index.recordOf(id) ?: return
        if (ALL_PACKS != packFilter.selected && packFilter.selected != record.packId) {
            packFilter.select(ALL_PACKS)
        }
        if (ALL_TYPES != typeFilter.selected && !hasExtension(record.id.path, typeFilter.selected)) {
            typeFilter.select(ALL_TYPES)
        }
        AssetIndex.expandTo(treeRoot, id)
        searchBox.value = ""
        sidebarList.scrollToRecord(id)
    }

    private fun refreshLinks() {
        val record = previewRecord
        usedByPanel.setItems(if (record == null) emptyList() else index.usagesOf(record.id))
        usedByPanel.resetScroll()
        referencesPanel.resetScroll()
    }

    companion object {
        private val TITLE: Component = Component.literal("Loaded Assets")
        private val SEARCH_HINT: Component = Component.literal("Search by namespace or path...")
        private const val ALL_PACKS = "All packs"
        private const val ALL_TYPES = "All types"

        private const val HEADER_HEIGHT = 4 + 9 + 4 + 9 + 4 + 20 + 4
        private const val FOOTER_HEIGHT = 33
        private const val PANE_GAP = 8
        private const val COPY_FEEDBACK_TICKS = 40
        private const val ACTION_BUTTON_HEIGHT = 20
        private const val PACK_BUTTON_WIDTH = 150
        private const val TYPE_BUTTON_WIDTH = 110

        private fun hasExtension(
            path: String,
            extension: String,
        ): Boolean = AssetIndex.extensionOf(path) == extension

        @JvmStatic
        fun openButton(parent: Screen): Button {
            val button =
                Button
                    .builder(Component.literal("Loaded Assets")) {
                        Minecraft.getInstance().gui.setScreen(LoadedAssetsScreen(parent))
                    }.size(100, 20)
                    .build()
            button.x = parent.width - button.getWidth() - 5
            button.y = 5
            return button
        }
    }
}
