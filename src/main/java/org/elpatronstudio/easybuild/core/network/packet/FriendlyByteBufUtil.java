package org.elpatronstudio.easybuild.core.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import org.elpatronstudio.easybuild.core.model.AnchorPos;
import org.elpatronstudio.easybuild.core.model.ChestRef;
import org.elpatronstudio.easybuild.core.model.MaterialStack;
import org.elpatronstudio.easybuild.core.model.SchematicRef;

import java.util.ArrayList;
import java.util.List;

final class FriendlyByteBufUtil {

    private FriendlyByteBufUtil() {
    }

    static void writeSchematicRef(FriendlyByteBuf buf, SchematicRef ref) {
        buf.writeUtf(ref.schematicId());
        buf.writeVarInt(ref.version());
        buf.writeLong(ref.checksum());
    }

    static SchematicRef readSchematicRef(FriendlyByteBuf buf) {
        String id = buf.readUtf();
        int version = buf.readVarInt();
        long checksum = buf.readLong();
        return new SchematicRef(id, version, checksum);
    }

    static void writeAnchor(FriendlyByteBuf buf, AnchorPos anchor) {
        buf.writeResourceLocation(anchor.dimension());
        buf.writeInt(anchor.x());
        buf.writeInt(anchor.y());
        buf.writeInt(anchor.z());
        buf.writeEnum(anchor.facing());
    }

    static AnchorPos readAnchor(FriendlyByteBuf buf) {
        return new AnchorPos(
                buf.readResourceLocation(),
                buf.readInt(),
                buf.readInt(),
                buf.readInt(),
                buf.readEnum(net.minecraft.core.Direction.class)
        );
    }

    static void writeChestList(FriendlyByteBuf buf, List<ChestRef> chests) {
        buf.writeVarInt(chests.size());
        for (ChestRef ref : chests) {
            buf.writeResourceLocation(ref.dimension());
            buf.writeBlockPos(ref.blockPos());
        }
    }

    static List<ChestRef> readChestList(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<ChestRef> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add(new ChestRef(buf.readResourceLocation(), buf.readBlockPos()));
        }
        return list;
    }

    static void writeMaterialList(FriendlyByteBuf buf, List<MaterialStack> stacks) {
        buf.writeVarInt(stacks.size());
        for (MaterialStack stack : stacks) {
            buf.writeResourceLocation(stack.itemId());
            buf.writeVarInt(stack.count());
        }
    }

    static List<MaterialStack> readMaterialList(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<MaterialStack> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add(new MaterialStack(buf.readResourceLocation(), buf.readVarInt()));
        }
        return list;
    }

    static void writeStringList(FriendlyByteBuf buf, List<String> values) {
        buf.writeVarInt(values.size());
        for (String value : values) {
            buf.writeUtf(value);
        }
    }

    static List<String> readStringList(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<String> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add(buf.readUtf());
        }
        return list;
    }
}
