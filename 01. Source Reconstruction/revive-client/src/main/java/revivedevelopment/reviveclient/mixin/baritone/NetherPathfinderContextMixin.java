package revivedevelopment.reviveclient.mixin.baritone;

import dev.babbaj.pathfinder.Octree;
import java.lang.ref.SoftReference;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import revivedevelopment.reviveclient.systems.modules.movement.BaritoneElytraOverworld;
import revivedevelopment.reviveclient.utils.BaritoneOctreePacker;

@Mixin(
   targets = {"baritone/process/elytra/NetherPathfinderContext"},
   remap = false
)
public abstract class NetherPathfinderContextMixin {
   @Shadow
   @Final
   @Dynamic("Baritone implementation class; obfuscated member verified at runtime by BaritoneMixinGuard")
   long a;

   @Inject(
      method = {"a(Ljava/lang/ref/SoftReference;)V"},
      at = {@At("HEAD")},
      cancellable = true
   )
   @Dynamic("Baritone implementation class; obfuscated member verified at runtime by BaritoneMixinGuard")
   private void revive$packOverworldChunk(SoftReference<WorldChunk> chunkRef, CallbackInfo ci) {
      if (BaritoneElytraOverworld.isUnlocked()) {
         ci.cancel();
         WorldChunk chunk = chunkRef.get();
         if (chunk != null) {
            BaritoneOctreePacker.pack(this.a, chunk);
         }
      }
   }

   @Redirect(
      method = {"a(JLbaritone/api/utils/Pair;)V"},
      at = @At(
         value = "INVOKE",
         target = "Ldev/babbaj/pathfinder/Octree;setBlock(JIIIZ)V"
      )
   )
   @Dynamic("Baritone implementation class; obfuscated member verified at runtime by BaritoneMixinGuard")
   private static void revive$boundedSetBlock(long chunkPtr, int x, int y, int z, boolean solid) {
      if (y >= 0 && y < 128) {
         Octree.setBlock(chunkPtr, x, y, z, solid);
      }
   }
}
