package org.elpatronstudio.easybuild.core.network.packet;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import org.elpatronstudio.easybuild.core.model.AnchorPos;
import org.elpatronstudio.easybuild.core.model.PasteMode;
import org.elpatronstudio.easybuild.core.model.SchematicRef;
import org.elpatronstudio.easybuild.server.job.BuildJobManager;

import java.util.Objects;
import java.util.UUID;

/**
 * Client → Server packet requesting the server to execute a build job.
 */
public record ServerboundRequestBuild(
        UUID playerUuid,
        SchematicRef schematic,
        AnchorPos anchor,
        PasteMode mode,
        JsonObject options,
        String requestId,
        long nonce
) {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    public ServerboundRequestBuild {
        Objects.requireNonNull(playerUuid, "playerUuid");
        Objects.requireNonNull(schematic, "schematic");
        Objects.requireNonNull(anchor, "anchor");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(requestId, "requestId");
        if (options == null) {
            options = new JsonObject();
        }
    }

    public static void encode(ServerboundRequestBuild message, FriendlyByteBuf buf) {
        buf.writeUUID(message.playerUuid);
        FriendlyByteBufUtil.writeSchematicRef(buf, message.schematic);
        FriendlyByteBufUtil.writeAnchor(buf, message.anchor);
        buf.writeEnum(message.mode);
        buf.writeUtf(GSON.toJson(message.options));
        buf.writeUtf(message.requestId);
        buf.writeLong(message.nonce);
    }

    public static ServerboundRequestBuild decode(FriendlyByteBuf buf) {
        UUID playerUuid = buf.readUUID();
        SchematicRef schematic = FriendlyByteBufUtil.readSchematicRef(buf);
        AnchorPos anchor = FriendlyByteBufUtil.readAnchor(buf);
        PasteMode mode = buf.readEnum(PasteMode.class);
        JsonObject options = JsonParser.parseString(buf.readUtf()).getAsJsonObject();
        String requestId = buf.readUtf();
        long nonce = buf.readLong();
        return new ServerboundRequestBuild(playerUuid, schematic, anchor, mode, options, requestId, nonce);
    }

    public void handle(ServerPlayer player) {
        BuildJobManager.get().submitBuild(player, this);
    }
}
