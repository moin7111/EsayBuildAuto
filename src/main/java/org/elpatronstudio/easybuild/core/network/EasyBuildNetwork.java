package org.elpatronstudio.easybuild.core.network;

import net.minecraft.resources.ResourceLocation;
import org.elpatronstudio.esaybuildauto.Esaybuildauto;

/**
 * Placeholder for EasyBuild's networking bootstrap. NeoForge 21+ uses payload-based networking, which will be wired up in a later phase.
 */
public final class EasyBuildNetwork {

    public static final String PROTOCOL_VERSION = "1";
    public static final ResourceLocation CHANNEL_ID = ResourceLocation.fromNamespaceAndPath(Esaybuildauto.MODID, "core");

    private EasyBuildNetwork() {
    }

    public static void register() {
        // TODO: Register payload codecs and handlers via NetworkRegistry during the networking phase.
    }
}
