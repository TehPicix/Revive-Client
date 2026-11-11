package revivedevelopment.reviveclient.mixin.baritone;

import net.minecraft.block.Block;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import revivedevelopment.reviveclient.systems.modules.movement.BaritoneElytraOverworld;

@Mixin(
   targets = {"baritone/process/ElytraProcess"},
   remap = false
)
public abstract class ElytraProcessMixin {
   @Redirect(
      method = {"a(Lnet/minecraft/class_2338;Z)V"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/class_1937;method_27983()Lnet/minecraft/class_5321;"
      )
   )
   @Dynamic("Baritone implementation class; obfuscated member verified at runtime by BaritoneMixinGuard")
   private RegistryKey<World> revive$unlockDimension(World world) {
      RegistryKey<World> key = world.getRegistryKey();
      return BaritoneElytraOverworld.isUnlocked() ? World.NETHER : key;
   }

   @ModifyConstant(
      method = {"pathTo(Lbaritone/api/pathing/goals/Goal;)V"},
      constant = {@Constant(
         intValue = 64
      )}
   )
   @Dynamic("Baritone implementation class; obfuscated member verified at runtime by BaritoneMixinGuard")
   private int revive$goalY(int original) {
      return BaritoneElytraOverworld.isUnlocked() ? BaritoneElytraOverworld.goalY() : original;
   }

   @ModifyConstant(
      method = {"onTick(ZZ)Lbaritone/api/process/PathingCommand;"},
      constant = {@Constant(
         intValue = 15
      )},
      slice = {@Slice(
         from = @At(
            value = "CONSTANT",
            args = {"stringValue=Above the landing spot, landing..."}
         ),
         to = @At(
            value = "CONSTANT",
            args = {"stringValue=bad landing spot, trying again..."}
         )
      )}
   )
   @Dynamic("Baritone implementation class; obfuscated member verified at runtime by BaritoneMixinGuard")
   private int revive$landingRetryDrop(int original) {
      return BaritoneElytraOverworld.isUnlocked() ? BaritoneElytraOverworld.landingRetryDrop() : original;
   }

   @ModifyConstant(
      method = {"onTick(ZZ)Lbaritone/api/process/PathingCommand;"},
      constant = {@Constant(
         intValue = 31
      )},
      slice = {@Slice(
         from = @At(
            value = "CONSTANT",
            args = {"stringValue=Not taking off, because elytra durability or fireworks are so low that I would immediately emergency land anyway."}
         )
      )}
   )
   @Dynamic("Baritone implementation class; obfuscated member verified at runtime by BaritoneMixinGuard")
   private int revive$takeoffSearchY(int original) {
      return BaritoneElytraOverworld.isUnlocked() ? BaritoneElytraOverworld.takeoffSearchY(original) : original;
   }

   @Inject(
      method = {"a(Lnet/minecraft/class_2248;)Z"},
      at = {@At("HEAD")},
      cancellable = true
   )
   @Dynamic("Baritone implementation class; obfuscated member verified at runtime by BaritoneMixinGuard")
   private static void revive$overworldLandingBlock(Block block, CallbackInfoReturnable<Boolean> cir) {
      if (BaritoneElytraOverworld.isUnlocked()) {
         cir.setReturnValue(BaritoneElytraOverworld.isLandingBlock(block));
      }
   }
}
