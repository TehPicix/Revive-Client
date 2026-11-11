package revivedevelopment.reviveclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.events.entity.player.AttackEntityEvent;
import revivedevelopment.reviveclient.events.entity.player.BlockBreakingCooldownEvent;
import revivedevelopment.reviveclient.events.entity.player.InteractBlockEvent;
import revivedevelopment.reviveclient.events.entity.player.PostAttackEntityEvent;
import revivedevelopment.reviveclient.events.entity.player.StartBreakingBlockEvent;
import revivedevelopment.reviveclient.mixininterface.IClientPlayerInteractionManager;
import revivedevelopment.reviveclient.utils.world.BlockUtils;

@Mixin({ClientPlayerInteractionManager.class})
public abstract class ClientPlayerInteractionManagerMixin implements IClientPlayerInteractionManager {
   @Shadow
   private int field_3716;
   @Shadow
   @Final
   private ClientPlayNetworkHandler field_3720;

   @Shadow
   protected abstract void method_2911();

   @Shadow
   public abstract boolean method_2899(BlockPos var1);

   @Inject(
      method = {"method_2910"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onAttackBlock(BlockPos blockPos, Direction direction, CallbackInfoReturnable<Boolean> info) {
      if (((StartBreakingBlockEvent)ReviveClient.EVENT_BUS.post(StartBreakingBlockEvent.get(blockPos, direction))).isCancelled()) {
         info.cancel();
      }
   }

   @Inject(
      method = {"method_2896"},
      at = {@At("HEAD")},
      cancellable = true
   )
   public void interactBlock(ClientPlayerEntity player, Hand hand, BlockHitResult hitResult, CallbackInfoReturnable<ActionResult> cir) {
      if (((InteractBlockEvent)ReviveClient.EVENT_BUS.post(InteractBlockEvent.get(player.getMainHandStack().isEmpty() ? Hand.OFF_HAND : hand, hitResult)))
         .isCancelled()) {
         cir.setReturnValue(ActionResult.FAIL);
      }
   }

   @Inject(
      method = {"method_2918"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onAttackEntity(PlayerEntity player, Entity target, CallbackInfo info) {
      if (((AttackEntityEvent)ReviveClient.EVENT_BUS.post(AttackEntityEvent.get(target))).isCancelled()) {
         info.cancel();
      }
   }

   @Inject(
      method = {"method_2918"},
      at = {@At("TAIL")}
   )
   private void onAttackEntityTail(PlayerEntity player, Entity target, CallbackInfo info) {
      ReviveClient.EVENT_BUS.post(PostAttackEntityEvent.get(target));
   }

   @Redirect(
      method = {"method_2902"},
      at = @At(
         value = "FIELD",
         target = "Lnet/minecraft/class_636;field_3716:I",
         opcode = 181,
         ordinal = 1
      )
   )
   private void creativeBreakDelayChange(ClientPlayerInteractionManager interactionManager, int value) {
      BlockBreakingCooldownEvent event = (BlockBreakingCooldownEvent)ReviveClient.EVENT_BUS.post(BlockBreakingCooldownEvent.get(value));
      this.field_3716 = event.cooldown;
   }

   @Redirect(
      method = {"method_2902"},
      at = @At(
         value = "FIELD",
         target = "Lnet/minecraft/class_636;field_3716:I",
         opcode = 181,
         ordinal = 2
      )
   )
   private void survivalBreakDelayChange(ClientPlayerInteractionManager interactionManager, int value) {
      BlockBreakingCooldownEvent event = (BlockBreakingCooldownEvent)ReviveClient.EVENT_BUS.post(BlockBreakingCooldownEvent.get(value));
      this.field_3716 = event.cooldown;
   }

   @Redirect(
      method = {"method_2910"},
      at = @At(
         value = "FIELD",
         target = "Lnet/minecraft/class_636;field_3716:I",
         opcode = 181
      )
   )
   private void creativeBreakDelayChange2(ClientPlayerInteractionManager interactionManager, int value) {
      BlockBreakingCooldownEvent event = (BlockBreakingCooldownEvent)ReviveClient.EVENT_BUS.post(BlockBreakingCooldownEvent.get(value));
      this.field_3716 = event.cooldown;
   }

   @ModifyExpressionValue(
      method = {"method_41930"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/class_2680;method_26165(Lnet/minecraft/class_1657;Lnet/minecraft/class_1922;Lnet/minecraft/class_2338;)F"
      )}
   )
   private float modifyBlockBreakingDelta(float original) {
      return original;
   }

   @Inject(
      method = {"method_2925"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onCancelBlockBreaking(CallbackInfo info) {
      if (BlockUtils.breaking) {
         info.cancel();
      }
   }

   @Override
   public void revive$syncSelected() {
      this.method_2911();
   }
}
