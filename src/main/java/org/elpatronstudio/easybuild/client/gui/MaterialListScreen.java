package org.elpatronstudio.easybuild.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.Font;
import org.elpatronstudio.easybuild.client.model.SchematicFileEntry;
import org.elpatronstudio.easybuild.client.state.EasyBuildClientState;
import org.elpatronstudio.easybuild.core.model.MaterialStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Displays the server-evaluated material requirements for the selected schematic.
 */
public class MaterialListScreen extends Screen {

    private static final Component TITLE = Component.translatable("easybuild.gui.materials.title");
    private final Screen parent;
    private final SchematicFileEntry schematic;
    private MaterialListWidget listWidget;
    private EditBox searchBox;
    private long lastStatusTimestamp = -1L;
    private EasyBuildClientState.MaterialStatus currentStatus;

    public MaterialListScreen(Screen parent, SchematicFileEntry schematic) {
        super(TITLE);
        this.parent = parent;
        this.schematic = schematic;
    }

    @Override
    protected void init() {
        int listWidth = Math.min(this.width - 40, 340);
        int left = (this.width - listWidth) / 2;

        this.searchBox = new EditBox(this.font, left, 68, listWidth, 18, Component.translatable("easybuild.gui.materials.search"));
        this.searchBox.setHint(Component.translatable("easybuild.gui.materials.search"));
        this.searchBox.setResponder(value -> {
            if (this.listWidget != null) {
                this.listWidget.setFilter(value);
            }
        });
        this.addRenderableWidget(this.searchBox);

        int listTop = this.searchBox.getY() + this.searchBox.getHeight() + 6;
        int listHeight = this.height - listTop - 90;
        this.listWidget = new MaterialListWidget(this.minecraft, listWidth, listHeight, listTop, 18);
        this.listWidget.updateSizeAndPosition(listWidth, listHeight, left, listTop);
        this.addRenderableWidget(listWidget);

        int buttonWidth = 100;
        this.addRenderableWidget(Button.builder(Component.translatable("gui.back"), button -> onClose())
                .bounds(left, this.height - 40, buttonWidth, 20)
                .build());
        this.addRenderableWidget(Button.builder(Component.translatable("easybuild.gui.materials.refresh"), button -> EasyBuildGuiActions.requestMaterialCheck(minecraft, schematic))
                .bounds(left + listWidth - buttonWidth, this.height - 40, buttonWidth, 20)
                .build());

        EasyBuildGuiActions.requestMaterialCheck(minecraft, schematic);
        refreshStatus();
    }

    @Override
    public void tick() {
        super.tick();
        if (searchBox != null) {
            searchBox.tick();
        }
        refreshStatus();
    }

    private void refreshStatus() {
        Optional<EasyBuildClientState.MaterialStatus> statusOpt = EasyBuildClientState.get().materialStatus(schematic.ref());
        EasyBuildClientState.MaterialStatus status = statusOpt.orElse(null);
        long timestamp = status != null ? status.updatedAtMillis() : -1L;
        if (timestamp != lastStatusTimestamp) {
            lastStatusTimestamp = timestamp;
            currentStatus = status;
            listWidget.setStatus(status);
            if (searchBox != null) {
                listWidget.setFilter(searchBox.getValue());
            }
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        Component header = Component.translatable("easybuild.gui.materials.header", schematic.displayName());
        guiGraphics.drawCenteredString(this.font, header, this.width / 2, 20, 0xFFFFFF);

        Component statusLine = buildStatusLine();
        guiGraphics.drawCenteredString(this.font, statusLine, this.width / 2, 40, resolveStatusColor());

        if (currentStatus != null && currentStatus.suggestedSources().size() > 0) {
            guiGraphics.drawCenteredString(this.font,
                    Component.translatable("easybuild.gui.materials.suggested", currentStatus.suggestedSources().size()),
                    this.width / 2, 54, 0xA0A0A0);
        } else if (currentStatus == null) {
            guiGraphics.drawCenteredString(this.font,
                    Component.translatable("easybuild.gui.materials.pending"),
                    this.width / 2, 54, 0xA0A0A0);
        }
    }

    private Component buildStatusLine() {
        if (currentStatus == null) {
            return Component.translatable("easybuild.gui.materials.status.wait");
        }
        if (currentStatus.ready()) {
            if (currentStatus.reserved()) {
                long timeLeftMillis = currentStatus.reservationExpiresAt() - System.currentTimeMillis();
                long seconds = Math.max(0L, timeLeftMillis / 1000L);
                return Component.translatable("easybuild.gui.materials.status.reserved", seconds);
            }
            return Component.translatable("easybuild.gui.materials.status.ready");
        }
        int types = currentStatus.missing().size();
        int total = totalMissingItems(currentStatus);
        return Component.translatable("easybuild.gui.materials.status.missing", types, total);
    }

    private int resolveStatusColor() {
        if (currentStatus == null) {
            return 0xA0A0A0;
        }
        if (currentStatus.ready()) {
            return currentStatus.reserved() ? 0xFFD37F : 0x55FF55;
        }
        return 0xFF5555;
    }

    private int totalMissingItems(EasyBuildClientState.MaterialStatus status) {
        return status.missing().stream().mapToInt(stack -> Math.max(0, stack.count())).sum();
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
    }

    private static final class MaterialListWidget extends ObjectSelectionList<MaterialEntry> {

        private final List<MaterialEntry> allEntries = new ArrayList<>();
        private String filter = "";

        private MaterialListWidget(Minecraft minecraft, int width, int height, int top, int itemHeight) {
            super(minecraft, width, height, top, itemHeight);
        }

        public void setStatus(EasyBuildClientState.MaterialStatus status) {
            allEntries.clear();
            clearEntries();
            if (status != null) {
                List<MaterialStack> stacks = new ArrayList<>(status.missing());
                stacks.sort(Comparator.comparingInt(MaterialStack::count).reversed());
                Font font = Minecraft.getInstance().font;
                for (MaterialStack stack : stacks) {
                    allEntries.add(new MaterialEntry(stack, font));
                }
            }
            applyFilter();
        }

        public void setFilter(String filter) {
            String normalized = filter == null ? "" : filter.trim().toLowerCase(Locale.ROOT);
            if (!Objects.equals(this.filter, normalized)) {
                this.filter = normalized;
                applyFilter();
            }
        }

        private void applyFilter() {
            clearEntries();
            if (allEntries.isEmpty()) {
                return;
            }
            if (filter.isEmpty()) {
                allEntries.forEach(this::addEntry);
            } else {
                for (MaterialEntry entry : allEntries) {
                    if (entry.matches(filter)) {
                        addEntry(entry);
                    }
                }
            }
            setScrollAmount(0.0D);
        }

        @Override
        public int getRowWidth() {
            return this.getWidth() - 8;
        }
    }

    private static final class MaterialEntry extends ObjectSelectionList.Entry<MaterialEntry> {

        private final MaterialStack stack;
        private final ItemStack displayStack;
        private final Component displayName;
        private final String searchKey;
        private final Font font;

        private MaterialEntry(MaterialStack stack, Font font) {
            this.stack = stack;
            this.font = font;
            Item item = BuiltInRegistries.ITEM.getOptional(stack.itemId()).orElse(null);
            if (item == null) {
                this.displayStack = ItemStack.EMPTY;
                this.displayName = Component.literal(stack.itemId().toString());
            } else {
                this.displayStack = new ItemStack(item);
                this.displayName = this.displayStack.getHoverName();
            }
            this.searchKey = this.displayName.getString().toLowerCase(Locale.ROOT);
        }

        private boolean matches(String filter) {
            return searchKey.contains(filter);
        }

        @Override
        public Component getNarration() {
            return displayName;
        }

        @Override
        public void renderContent(GuiGraphics guiGraphics, int mouseX, int mouseY, boolean hovered, float partialTick) {
            int x = getContentX();
            int y = getContentY();
            if (!displayStack.isEmpty()) {
                guiGraphics.renderItem(displayStack, x, y - 1);
                guiGraphics.renderItemDecorations(font, displayStack, x, y - 1);
            }
            int textX = x + 20;
            Component count = Component.translatable("easybuild.gui.materials.count", stack.count());
            guiGraphics.drawString(font, displayName, textX, y, 0xFF5555, false);
            guiGraphics.drawString(font, count, getContentRight() - font.width(count), y, 0xFFAA55, false);
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean hovered) {
            return false;
        }
    }
}
