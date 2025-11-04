package org.elpatronstudio.easybuild.client.autobuild;

import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import org.elpatronstudio.easybuild.core.model.AnchorPos;
import org.elpatronstudio.easybuild.core.model.PasteMode;
import org.elpatronstudio.easybuild.core.model.SchematicRef;
import org.elpatronstudio.easybuild.server.job.BlockPlacementException;

import java.util.Objects;

/**
 * Lightweight placeholder controller that keeps track of the client-side auto build toggle.
 * <p>
 * The full placement pipeline will be implemented in a follow-up iteration. For now we surface
 * basic state management and user feedback so that the client keybinds can operate without
 * crashing the game or failing compilation because of a missing class.
 */
public final class ClientPlacementController {

    private static final ClientPlacementController INSTANCE = new ClientPlacementController();

    private boolean running;
    private SchematicRef schematic;
    private AnchorPos anchor;
    private PasteMode mode;
    private JsonObject options;

    private ClientPlacementController() {
    }

    public static ClientPlacementController get() {
        return INSTANCE;
    }

    public boolean isRunning() {
        return running;
    }

    public void start(SchematicRef schematic, AnchorPos anchor, PasteMode mode, JsonObject options) throws BlockPlacementException {
        Objects.requireNonNull(schematic, "schematic");
        Objects.requireNonNull(anchor, "anchor");
        Objects.requireNonNull(mode, "mode");

        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            throw new BlockPlacementException("CLIENT_PLAYER_MISSING", "Kein Spieler verfügbar");
        }

        if (running) {
            return;
        }

        this.running = true;
        this.schematic = schematic;
        this.anchor = anchor;
        this.mode = mode;
        this.options = options == null ? new JsonObject() : options.deepCopy();

        player.displayClientMessage(Component.literal("[EasyBuild] Client-Autobau aktiviert."), true);
    }

    public void stop(String reason) {
        if (!running) {
            return;
        }

        running = false;
        schematic = null;
        anchor = null;
        mode = null;
        options = null;

        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }

        Component message = (reason == null || reason.isBlank())
                ? Component.literal("[EasyBuild] Client-Autobau gestoppt.")
                : Component.literal("[EasyBuild] " + reason);
        player.displayClientMessage(message, true);
    }
}
