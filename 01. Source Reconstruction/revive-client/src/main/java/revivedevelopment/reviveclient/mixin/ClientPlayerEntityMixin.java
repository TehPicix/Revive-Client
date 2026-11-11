package revivedevelopment.reviveclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.Input;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.events.entity.player.PlayerTickMovementEvent;
import revivedevelopment.reviveclient.events.entity.player.SendMovementPacketsEvent;
import revivedevelopment.reviveclient.systems.modules.Modules;
import revivedevelopment.reviveclient.systems.modules.combat.Velocity;
import revivedevelopment.reviveclient.systems.modules.movement.NoSlow;
import revivedevelopment.reviveclient.systems.modules.movement.Sprint;
import revivedevelopment.reviveclient.systems.modules.render.NoRender;

@Mixin({ClientPlayerEntity.class})
public abstract class ClientPlayerEntityMixin extends AbstractClientPlayerEntity {
   @Shadow
   public Input field_3913;
   @Shadow
   public float field_44911;

   public ClientPlayerEntityMixin(ClientWorld world, GameProfile profile) {
      super(world, profile);
   }

   @ModifyExpressionValue(
      method = {"method_60887"},
      at = {@At(
         value = "FIELD",
         target = "Lnet/minecraft/class_310;field_1755:Lnet/minecraft/class_437;"
      )}
   )
   private Screen modifyNauseaCurrentScreen(Screen original) {
      return original;
   }

   @Inject(
      method = {"method_60887"},
      at = {@At("TAIL")},
      require = 0
   )
   private void onTickNausea(boolean fromPortalEffect, CallbackInfo ci) {
      NoRender noRender = Modules.get().get(NoRender.class);
      if (noRender != null && noRender.noNausea()) {
         this.field_44911 = 0.0F;
      }
   }

   @Inject(
      method = {"method_6007"},
      at = {@At("HEAD")}
   )
   private void preTickMovement(CallbackInfo ci) {
      ReviveClient.EVENT_BUS.post(PlayerTickMovementEvent.get());
   }

   @Inject(
      method = {"method_6007"},
      at = {@At("RETURN")}
   )
   private void postTickMovement(CallbackInfo ci) {
      ReviveClient.ROTATION.onTickMovementEnd();
   }

   @WrapWithCondition(
      method = {"method_6007"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/class_746;method_5728(Z)V"
      )},
      require = 0
   )
   private boolean wrapTickMovementSetSprinting(ClientPlayerEntity player, boolean sprinting) {
      if (sprinting) {
         return true;
      } else {
         Sprint sprint = Modules.get().get(Sprint.class);
         return sprint == null || !sprint.forceSprint();
      }
   }

   @Inject(
      method = {"method_3136"},
      at = {@At("HEAD")}
   )
   private void onSendMovementPacketsHead(CallbackInfo info) {
      ReviveClient.EVENT_BUS.post(SendMovementPacketsEvent.Pre.get());
   }

   @Inject(
      method = {"method_5773"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/class_634;method_52787(Lnet/minecraft/class_2596;)V",
         ordinal = 1
      )}
   )
   private void onTickHasVehicleBeforeSendPackets(CallbackInfo info) {
      ReviveClient.EVENT_BUS.post(SendMovementPacketsEvent.Pre.get());
   }

   @Inject(
      method = {"method_3136"},
      at = {@At("TAIL")}
   )
   private void onSendMovementPacketsTail(CallbackInfo info) {
      ReviveClient.EVENT_BUS.post(SendMovementPacketsEvent.Post.get());
   }

   @Inject(
      method = {"method_5773"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/class_634;method_52787(Lnet/minecraft/class_2596;)V",
         ordinal = 1,
         shift = Shift.AFTER
      )}
   )
   private void onTickHasVehicleAfterSendPackets(CallbackInfo info) {
      ReviveClient.EVENT_BUS.post(SendMovementPacketsEvent.Post.get());
   }

   @Inject(
      method = {"method_30673"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onPushOutOfBlocks(double x, double z, CallbackInfo info) {
      Velocity velocity = Modules.get().get(Velocity.class);
      if (velocity != null && velocity.shouldCancelBlockPush()) {
         info.cancel();
      }
   }

   @ModifyExpressionValue(
      method = {"method_67270", "method_6007"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/class_746;method_6115()Z"
      )},
      require = 0
   )
   private boolean revive$noSlowUse(boolean original) {
      return original && !NoSlow.cancelUseSlow();
   }

   @Inject(
      method = {"method_20303"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void revive$noSlowCrawl(CallbackInfoReturnable<Boolean> cir) {
      if (this.isCrawling() && NoSlow.cancelCrawlSlow()) {
         cir.setReturnValue(false);
      }
   }
}
