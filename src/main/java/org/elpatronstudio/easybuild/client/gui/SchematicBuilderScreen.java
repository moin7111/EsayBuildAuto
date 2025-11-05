package org.elpatronstudio.easybuild.client.gui;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.elpatronstudio.easybuild.client.ClientChestRegistry;
import org.elpatronstudio.easybuild.client.model.SchematicFileEntry;
import org.elpatronstudio.easybuild.client.schematic.SchematicRepository;
import org.elpatronstudio.easybuild.client.state.EasyBuildClientState;
import org.elpatronstudio.easybuild.core.model.BuildMode;
import org.elpatronstudio.easybuild.core.model.JobPhase;

import java.nio.file.Path;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Primary GUI for selecting schematics and launching builds.
 */
public class SchematicBuilderScreen extends Screen {

    private static final Component TITLE = Component.translatable("easybuild.gui.title");
    private static final Component MODE_LABEL = Component.translatable("easybuild.gui.mode");
    private static final Component CHEST_BUTTON = Component.translatable("easybuild.gui.chests");
    private static final Component MATERIALS_BUTTON = Component.translatable("easybuild.gui.materials");
    private static final Component START_BUTTON = Component.translatable("easybuild.gui.start");
    private static final Component CLOSE_BUTTON = Component.translatable("gui.cancel");
    private static final Component RELOAD_BUTTON = Component.translatable("easybuild.gui.reload");
    private static final DateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT);
    private static final DecimalFormat SIZE_FORMAT = new DecimalFormat("#,##0.##");
    private static final int COLOR_TEXT_PRIMARY = 0xE0E0E0;
    private static final int COLOR_TEXT_SECONDARY = 0xA0A0A0;
    private static final int COLOR_STATUS_READY = 0x55FF55;
    private static final int COLOR_STATUS_RESERVED = 0xFFD37F;
    private static final int COLOR_STATUS_MISSING = 0xFF5555;
    private static final int COLOR_STATUS_UNKNOWN = 0xA0A0A0;

    private final EasyBuildClientState state = EasyBuildClientState.get();
    private SchematicSelectionList selectionList;
    private CycleButton<BuildMode> modeButton;
    private Button startButton;
    private Button chestButton;
    private Button materialsButton;
    private Button reloadButton;
    private int rightPaneLeft;
    private int buttonColumnWidth;

    public SchematicBuilderScreen() {
        super(TITLE);
    }

    @Override
    protected void init() {
        if (minecraft == null) {
            return;
        }

        loadPersistedChests();
        loadSchematics();

        int listWidth = Mth.clamp(this.width / 2 - 32, 160, 220);
        int listLeft = this.width / 2 - listWidth - 12;
        int listTop = 40;
        int listBottom = this.height - 40;

        this.selectionList = new SchematicSelectionList(minecraft, listWidth, listBottom - listTop, listTop, 24);
        this.selectionList.updateSizeAndPosition(listWidth, listBottom - listTop, listLeft, listTop);
        refreshSelectionList();
        this.addRenderableWidget(selectionList);

        this.rightPaneLeft = listLeft + listWidth + 24;
        this.buttonColumnWidth = 160;

        this.modeButton = CycleButton.builder(BuildMode::title)
                .withValues(BuildMode.values())
                .withInitialValue(state.buildMode())
                .displayOnlyValue()
                .create(rightPaneLeft, listTop, buttonColumnWidth, 20, MODE_LABEL, (button, value) -> {
                    state.setBuildMode(value);
                    updateButtonStates();
                });
        this.addRenderableWidget(modeButton);

        this.chestButton = Button.builder(CHEST_BUTTON, button -> onSelectChests())
                .bounds(rightPaneLeft, listTop + 28, buttonColumnWidth, 20)
                .build();
        this.addRenderableWidget(chestButton);

        this.materialsButton = Button.builder(MATERIALS_BUTTON, button -> onOpenMaterials())
                .bounds(rightPaneLeft, listTop + 56, buttonColumnWidth, 20)
                .build();
        this.addRenderableWidget(materialsButton);

        this.reloadButton = Button.builder(RELOAD_BUTTON, button -> reloadSchematics())
                .bounds(rightPaneLeft, listTop + 84, buttonColumnWidth, 20)
                .build();
        this.addRenderableWidget(reloadButton);

        this.startButton = Button.builder(START_BUTTON, button -> onStart())
                .bounds(rightPaneLeft, listBottom - 44, buttonColumnWidth, 20)
                .build();
        this.addRenderableWidget(startButton);

        this.addRenderableWidget(Button.builder(CLOSE_BUTTON, button -> onClose())
                .bounds(rightPaneLeft, listBottom - 20, buttonColumnWidth, 20)
                .build());

        updateButtonStates();
    }

    @Override
    public void tick() {
        super.tick();
        updateButtonStates();
    }

    private void updateButtonStates() {
        if (startButton == null || materialsButton == null || chestButton == null) {
            return;
        }

        boolean hasSelection = state.selectedSchematic().isPresent();
        Optional<EasyBuildClientState.MaterialStatus> statusOpt = currentMaterialStatus();
        boolean needsMaterials = hasSelection && state.buildMode() != BuildMode.SELF;
        boolean materialsReady = !needsMaterials || statusOpt.map(EasyBuildClientState.MaterialStatus::ready).orElse(true);

        this.startButton.active = hasSelection && materialsReady;
        if (needsMaterials && statusOpt.isPresent() && !statusOpt.get().ready()) {
            EasyBuildClientState.MaterialStatus status = statusOpt.get();
            int missingStacks = status.missing().size();
            int missingItems = totalMissingItems(status);
            this.startButton.setTooltip(Tooltip.create(Component.translatable("easybuild.gui.start.tooltip.missing", missingStacks, missingItems)));
        } else {
            this.startButton.setTooltip(null);
        }

        this.materialsButton.active = hasSelection;
        this.chestButton.active = hasSelection;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        Font font = this.font;
        int titleX = this.width / 2 - font.width(TITLE) / 2;
        guiGraphics.drawString(font, TITLE, titleX, 15, 0xFFFFFF, false);

        state.selectedSchematic().ifPresent(entry -> renderDetails(guiGraphics, entry));

        if (state.isChestSelectionActive()) {
            Component tip = Component.translatable("easybuild.gui.chest_selection_tip").withStyle(ChatFormatting.YELLOW);
            int width = font.width(tip);
            guiGraphics.drawString(font, tip, (this.width - width) / 2, this.height - 28, 0xFFFF55, false);
        }

        renderJobStatus(guiGraphics);
    }

    private void renderDetails(GuiGraphics guiGraphics, SchematicFileEntry entry) {
        int left = this.selectionList.getX() + this.selectionList.getWidth() + 28;
        int top = this.selectionList.getY() + 120;
        Font font = this.font;
        guiGraphics.drawString(font, Component.translatable("easybuild.gui.detail.name", entry.displayName()), left, top, COLOR_TEXT_PRIMARY, false);
        guiGraphics.drawString(font, Component.translatable("easybuild.gui.detail.path", entry.id()), left, top + 14, COLOR_TEXT_SECONDARY, false);
        guiGraphics.drawString(font, Component.translatable("easybuild.gui.detail.size", humanReadableSize(entry.fileSize())), left, top + 28, COLOR_TEXT_SECONDARY, false);
        if (entry.lastModified() > 0L) {
            guiGraphics.drawString(font, Component.translatable("easybuild.gui.detail.modified", DATE_FORMAT.format(new Date(entry.lastModified()))), left, top + 42, COLOR_TEXT_SECONDARY, false);
        }
        guiGraphics.drawString(font, Component.translatable("easybuild.gui.detail.mode", state.buildMode().title()), left, top + 58, COLOR_TEXT_PRIMARY, false);
        guiGraphics.drawString(font, Component.translatable("easybuild.gui.detail.chests", state.selectedChests().size()), left, top + 72, COLOR_TEXT_PRIMARY, false);
        renderMaterialStatus(guiGraphics, left, top + 86);
    }

    private String humanReadableSize(long bytes) {
        if (bytes <= 0L) {
            return "0";
        }
        double value = bytes;
        String[] units = {"B", "KiB", "MiB", "GiB"};
        int unit = 0;
        while (value >= 1024 && unit < units.length - 1) {
            value /= 1024;
            unit++;
        }
        return SIZE_FORMAT.format(value) + " " + units[unit];
    }

    private void renderMaterialStatus(GuiGraphics guiGraphics, int left, int top) {
        Font font = this.font;
        Optional<EasyBuildClientState.MaterialStatus> statusOpt = currentMaterialStatus();
        Component line;
        int color;
        if (statusOpt.isEmpty()) {
            line = Component.translatable("easybuild.gui.detail.material_status.unknown");
            color = COLOR_STATUS_UNKNOWN;
        } else {
            EasyBuildClientState.MaterialStatus status = statusOpt.get();
            if (status.ready()) {
                if (status.reserved()) {
                    long timeLeftMillis = status.reservationExpiresAt() - System.currentTimeMillis();
                    long seconds = Math.max(0L, timeLeftMillis / 1000L);
                    line = Component.translatable("easybuild.gui.detail.material_status.reserved", seconds);
                    color = COLOR_STATUS_RESERVED;
                } else {
                    line = Component.translatable("easybuild.gui.detail.material_status.ready");
                    color = COLOR_STATUS_READY;
                }
            } else {
                int missingStacks = status.missing().size();
                int missingItems = totalMissingItems(status);
                line = Component.translatable("easybuild.gui.detail.material_status.missing", missingStacks, missingItems);
                color = COLOR_STATUS_MISSING;
            }
        }
        guiGraphics.drawString(font, line, left, top, color, false);
    }

    private void renderJobStatus(GuiGraphics guiGraphics) {
        List<EasyBuildClientState.ClientBuildJob> jobs = state.recentJobs(3);
        if (jobs.isEmpty()) {
            return;
        }

        Font font = this.font;
        int headerY = (startButton != null ? startButton.getY() : this.height - 40) - (jobs.size() + 1) * 12 - 8;
        int minimumY = this.selectionList.getY() + 150;
        if (headerY < minimumY) {
            headerY = minimumY;
        }

        guiGraphics.drawString(font, Component.translatable("easybuild.gui.jobs.recent"), rightPaneLeft, headerY, 0xFFFFFF, false);

        long now = System.currentTimeMillis();
        int y = headerY + 12;
        for (EasyBuildClientState.ClientBuildJob job : jobs) {
            Component line = Component.literal(trimToColumn(buildJobStatusLine(job, now)));
            guiGraphics.drawString(font, line, rightPaneLeft, y, jobStatusColor(job), false);
            y += 12;
        }
    }

    private String buildJobStatusLine(EasyBuildClientState.ClientBuildJob job, long now) {
        StringBuilder builder = new StringBuilder();
        if (!job.completed() && job.phase() != JobPhase.CANCELLED) {
            builder.append("> ");
        }
        builder.append(job.displayName());
        builder.append(" – ").append(phaseLabel(job.phase()));

        String message = job.lastMessage();
        if (message.isBlank() && job.total() > 0) {
            message = job.placed() + " / " + job.total();
        }

        if (!message.isBlank()) {
            builder.append(": ").append(message);
        }

        if (job.total() > 0) {
            double percent = (double) job.placed() / Math.max(1, job.total()) * 100.0;
            builder.append(" (").append(Math.round(percent)).append("%)");
        }

        long ageMillis = now - job.lastUpdate();
        if (ageMillis >= 1000L) {
            long seconds = Math.max(1L, ageMillis / 1000L);
            builder.append(" • ").append(seconds).append("s ago");
        }

        return builder.toString();
    }

    private int jobStatusColor(EasyBuildClientState.ClientBuildJob job) {
        return switch (job.phase()) {
            case COMPLETED -> 0x55FF55;
            case CANCELLED -> 0xFF5555;
            case ROLLING_BACK -> 0xFFAA55;
            case PLACING -> 0x66CCFF;
            case RESERVING, QUEUED -> 0xFFFFAA;
            case PAUSED -> 0xFFD37F;
        };
    }

    private String phaseLabel(JobPhase phase) {
        return switch (phase) {
            case QUEUED -> "Queued";
            case RESERVING -> "Reserving";
            case PLACING -> "Placing";
            case PAUSED -> "Paused";
            case ROLLING_BACK -> "Rolling Back";
            case COMPLETED -> "Completed";
            case CANCELLED -> "Cancelled";
        };
    }

    private String trimToColumn(String text) {
        if (this.font.width(text) <= buttonColumnWidth) {
            return text;
        }
        return this.font.plainSubstrByWidth(text + "...", buttonColumnWidth);
    }

    private void loadSchematics() {
        if (minecraft == null) {
            return;
        }
        Path gameDir = minecraft.gameDirectory.toPath();
        List<SchematicFileEntry> entries = SchematicRepository.load(gameDir);
        state.setAvailableSchematics(entries);
        if (state.selectedSchematic().isEmpty() && !entries.isEmpty()) {
            state.selectSchematic(entries.get(0));
        }
    }

    private void loadPersistedChests() {
        if (minecraft == null) {
            return;
        }
        Path gameDir = minecraft.gameDirectory.toPath();
        state.setSelectedChests(ClientChestRegistry.getAll(gameDir));
    }

    private void refreshSelectionList() {
        this.selectionList.clearEntries();
        for (SchematicFileEntry entry : state.availableSchematics()) {
            this.selectionList.addItem(new SchematicEntry(entry));
        }
        state.selectedSchematic().ifPresent(selectionList::setSelectedEntryByValue);
    }

    private void reloadSchematics() {
        loadSchematics();
        refreshSelectionList();
    }

    private void onSelectChests() {
        Minecraft mc = this.minecraft;
        this.onClose();
        if (mc != null && mc.player != null) {
            mc.player.displayClientMessage(Component.translatable("easybuild.chest_selection.started"), true);
        }
        state.requestReopenGuiAfterSelection();
        state.setChestSelectionActive(true);
    }

    private void onOpenMaterials() {
        state.selectedSchematic().ifPresent(selected -> minecraft.setScreen(new MaterialListScreen(this, selected)));
    }

    private void onStart() {
        if (minecraft == null) {
            return;
        }
        state.selectedSchematic().ifPresent(schematic -> EasyBuildGuiActions.handleStart(minecraft, schematic, state.buildMode()));
    }

    private void setSelected(SchematicFileEntry entry) {
        state.selectSchematic(entry);
        updateButtonStates();
    }

    @Override
    public void onClose() {
        super.onClose();
        minecraft.setScreen(null);
    }

    private Optional<EasyBuildClientState.MaterialStatus> currentMaterialStatus() {
        return state.selectedSchematic().flatMap(entry -> state.materialStatus(entry.ref()));
    }

    private int totalMissingItems(EasyBuildClientState.MaterialStatus status) {
        return status.missing().stream().mapToInt(stack -> Math.max(0, stack.count())).sum();
    }

    private final class SchematicSelectionList extends ObjectSelectionList<SchematicEntry> {

        private SchematicSelectionList(Minecraft minecraft, int width, int height, int top, int itemHeight) {
            super(minecraft, width, height, top, itemHeight);
        }

        public void addItem(SchematicEntry entry) {
            super.addEntry(entry);
        }

        private void setSelectedEntryByValue(SchematicFileEntry entry) {
            for (SchematicEntry row : children()) {
                if (row.entry.equals(entry)) {
                    setSelected(row);
                    scrollToEntry(row);
                    return;
                }
            }
        }

        @Override
        public int getRowWidth() {
            return this.getWidth() - 8;
        }
    }

    private final class SchematicEntry extends ObjectSelectionList.Entry<SchematicEntry> {

        private final SchematicFileEntry entry;

        private SchematicEntry(SchematicFileEntry entry) {
            this.entry = entry;
        }

        @Override
        public Component getNarration() {
            return Component.literal(entry.displayName());
        }

        @Override
        public void renderContent(GuiGraphics guiGraphics, int mouseX, int mouseY, boolean hovered, float partialTick) {
            Font font = SchematicBuilderScreen.this.font;
            Component name = Component.literal(entry.displayName()).withStyle(ChatFormatting.WHITE);
            Component detail = Component.literal(entry.id()).withStyle(ChatFormatting.DARK_GRAY);
            int x = getContentX();
            int y = getContentY();
            guiGraphics.drawString(font, name, x, y, 0xFFFFFF, false);
            guiGraphics.drawString(font, detail, x, y + 12, 0xA0A0A0, false);
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean hovered) {
            if (event.button() == 0) {
                selectionList.setSelected(this);
                SchematicBuilderScreen.this.setSelected(entry);
                return true;
            }
            return false;
        }
    }
}
