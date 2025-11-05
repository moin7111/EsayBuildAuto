package org.elpatronstudio.easybuild.client.gui;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.elpatronstudio.easybuild.client.model.SchematicFileEntry;
import org.elpatronstudio.easybuild.client.schematic.SchematicRepository;
import org.elpatronstudio.easybuild.client.state.EasyBuildClientState;
import org.elpatronstudio.easybuild.core.model.BuildMode;

import java.nio.file.Path;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

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

    private final EasyBuildClientState state = EasyBuildClientState.get();
    private SchematicSelectionList selectionList;
    private CycleButton<BuildMode> modeButton;
    private Button startButton;
    private Button chestButton;
    private Button materialsButton;
    private Button reloadButton;

    public SchematicBuilderScreen() {
        super(TITLE);
    }

    @Override
    protected void init() {
        if (minecraft == null) {
            return;
        }

        loadSchematics();

        int listWidth = Mth.clamp(this.width / 2 - 32, 160, 220);
        int listLeft = this.width / 2 - listWidth - 12;
        int listTop = 40;
        int listBottom = this.height - 40;

        this.selectionList = new SchematicSelectionList(minecraft, listWidth, listBottom - listTop, listTop, 24);
        this.selectionList.updateSizeAndPosition(listWidth, listBottom - listTop, listLeft, listTop);
        refreshSelectionList();
        this.addRenderableWidget(selectionList);

        int rightPaneLeft = listLeft + listWidth + 24;
        int buttonWidth = 160;

        this.modeButton = CycleButton.builder(BuildMode::title)
                .withValues(BuildMode.values())
                .withInitialValue(state.buildMode())
                .displayOnlyValue()
                .create(rightPaneLeft, listTop, buttonWidth, 20, MODE_LABEL, (button, value) -> state.setBuildMode(value));
        this.addRenderableWidget(modeButton);

        this.chestButton = Button.builder(CHEST_BUTTON, button -> onSelectChests())
                .bounds(rightPaneLeft, listTop + 28, buttonWidth, 20)
                .build();
        this.addRenderableWidget(chestButton);

        this.materialsButton = Button.builder(MATERIALS_BUTTON, button -> onOpenMaterials())
                .bounds(rightPaneLeft, listTop + 56, buttonWidth, 20)
                .build();
        this.addRenderableWidget(materialsButton);

        this.reloadButton = Button.builder(RELOAD_BUTTON, button -> reloadSchematics())
                .bounds(rightPaneLeft, listTop + 84, buttonWidth, 20)
                .build();
        this.addRenderableWidget(reloadButton);

        this.startButton = Button.builder(START_BUTTON, button -> onStart())
                .bounds(rightPaneLeft, listBottom - 44, buttonWidth, 20)
                .build();
        this.addRenderableWidget(startButton);

        this.addRenderableWidget(Button.builder(CLOSE_BUTTON, button -> onClose())
                .bounds(rightPaneLeft, listBottom - 20, buttonWidth, 20)
                .build());

        updateButtonStates();
    }

    @Override
    public void tick() {
        super.tick();
        updateButtonStates();
    }

    private void updateButtonStates() {
        boolean hasSelection = state.selectedSchematic().isPresent();
        this.startButton.active = hasSelection;
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
    }

    private void renderDetails(GuiGraphics guiGraphics, SchematicFileEntry entry) {
        int left = this.selectionList.getX() + this.selectionList.getWidth() + 28;
        int top = this.selectionList.getY() + 120;
        Font font = this.font;
        guiGraphics.drawString(font, Component.translatable("easybuild.gui.detail.name", entry.displayName()), left, top, 0xE0E0E0, false);
        guiGraphics.drawString(font, Component.translatable("easybuild.gui.detail.path", entry.id()), left, top + 14, 0xA0A0A0, false);
        guiGraphics.drawString(font, Component.translatable("easybuild.gui.detail.size", humanReadableSize(entry.fileSize())), left, top + 28, 0xA0A0A0, false);
        if (entry.lastModified() > 0L) {
            guiGraphics.drawString(font, Component.translatable("easybuild.gui.detail.modified", DATE_FORMAT.format(new Date(entry.lastModified()))), left, top + 42, 0xA0A0A0, false);
        }
        guiGraphics.drawString(font, Component.translatable("easybuild.gui.detail.mode", state.buildMode().title()), left, top + 58, 0xE0E0E0, false);
        guiGraphics.drawString(font, Component.translatable("easybuild.gui.detail.chests", state.selectedChests().size()), left, top + 72, 0xE0E0E0, false);
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
