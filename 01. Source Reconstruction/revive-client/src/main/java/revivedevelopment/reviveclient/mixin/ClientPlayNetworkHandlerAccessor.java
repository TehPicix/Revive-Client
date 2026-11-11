package revivedevelopment.reviveclient.mixin;

import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.message.LastSeenMessagesCollector;
import net.minecraft.network.message.MessageChain.Packer;
import net.minecraft.registry.DynamicRegistryManager.Immutable;
import net.minecraft.resource.featuretoggle.FeatureSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({ClientPlayNetworkHandler.class})
public interface ClientPlayNetworkHandlerAccessor {
   @Accessor("field_19144")
   int getChunkLoadDistance();

   @Accessor("field_39808")
   Packer getMessagePacker();

   @Accessor("field_39858")
   LastSeenMessagesCollector getLastSeenMessagesCollector();

   @Accessor("field_25063")
   Immutable getCombinedDynamicRegistries();

   @Accessor("field_45600")
   FeatureSet getEnabledFeatures();
}
