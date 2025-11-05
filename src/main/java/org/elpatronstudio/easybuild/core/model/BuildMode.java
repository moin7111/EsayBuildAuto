package org.elpatronstudio.easybuild.core.model;

import net.minecraft.network.chat.Component;

/**
 * High level build modes exposed to the player via the schematic builder UI.
 */
public enum BuildMode {

    SELF(Component.translatable("easybuild.mode.self")),
    AUTO(Component.translatable("easybuild.mode.auto")),
    INSTA(Component.translatable("easybuild.mode.insta"));

    private final Component title;

    BuildMode(Component title) {
        this.title = title;
    }

    public Component title() {
        return title;
    }
}
