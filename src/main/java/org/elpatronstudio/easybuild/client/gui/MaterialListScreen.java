package org.elpatronstudio.easybuild.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.input.MouseButtonEvent;
import org.elpatronstudio.easybuild.client.model.SchematicFileEntry;
import org.elpatronstudio.easybuild.client.state.EasyBuildClientState;
import org.elpatronstudio.easybuild.core.model.MaterialStack;

import java.util.List;
import java.util.Optional;

/**
 * Displays the server-evaluated material requirements for the selected schematic.
 */
public class MaterialListScreen extends Screen {

    private static final Component TITLE = Component.translatable("easybuild.gui.materials.title");
    private final Screen parent;
    private final SchematicFileEntry schematic;
    private MaterialListWidget listWidget;
    private long lastStatusTimestamp = -1L;
    private EasyBuildClientState.MaterialStatus currentStatus;

    public MaterialListScreen(Screen parent, SchematicFileEntry schematic) {
        super(TITLE);
        this.parent = parent;
        this.schematic = schematic;
    }

    @Override
    protected void init() {
        int listWidth = Math.min(this.width - 40, 320);
        int left = (this.width - listWidth) / 2;
        int listTop = 70;
        int listHeight = this.height - listTop - 80;
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

        private MaterialListWidget(Minecraft minecraft, int width, int height, int top, int itemHeight) {
            super(minecraft, width, height, top, itemHeight);
        }

        public void setStatus(EasyBuildClientState.MaterialStatus status) {
            clearEntries();
            if (status != null) {
                for (MaterialStack stack : status.missing()) {
                    super.addEntry(new MaterialEntry(stack));
                }
            }
        }

        @Override
        public int getRowWidth() {
            return this.getWidth() - 8;
        }
    }

    private static final class MaterialEntry extends ObjectSelectionList.Entry<MaterialEntry> {

        private final MaterialStack stack;

        private MaterialEntry(MaterialStack stack) {
            this.stack = stack;
        }

        @Override
        public Component getNarration() {
            return Component.literal(resolveItemName(stack));
        }

        @Override
        public void renderContent(GuiGraphics guiGraphics, int mouseX, int mouseY, boolean hovered, float partialTick) {
            Component itemName = Component.literal(resolveItemName(stack));
            Component count = Component.translatable("easybuild.gui.materials.count", stack.count());
            int x = getContentX();
            int y = getContentY();
            guiGraphics.drawString(Minecraft.getInstance().font, itemName, x, y, 0xFF5555, false);
            guiGraphics.drawString(Minecraft.getInstance().font, count, getContentRight() - Minecraft.getInstance().font.width(count), y, 0xFFAA55, false);
        }

        private static String resolveItemName(MaterialStack stack) {
            Item item = BuiltInRegistries.ITEM.getOptional(stack.itemId()).orElse(null);
            if (item == null) {
                return stack.itemId().toString();
            }
            ItemStack displayStack = new ItemStack(item);
            return displayStack.getHoverName().getString();
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean hovered) {
            return false;
        }
    }
}
