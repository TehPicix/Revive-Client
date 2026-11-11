package revivedevelopment.reviveclient.mixin;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.events.render.ArmRenderEvent;
import revivedevelopment.reviveclient.events.render.HeldItemRendererEvent;
import revivedevelopment.reviveclient.systems.modules.Modules;
import revivedevelopment.reviveclient.systems.modules.player.SilentMine;
import revivedevelopment.reviveclient.systems.modules.render.Chams;
import revivedevelopment.reviveclient.utils.render.postprocess.HandChams;
import revivedevelopment.reviveclient.utils.render.postprocess.PostProcessShaders;

@Mixin({HeldItemRenderer.class})
public abstract class HeldItemRendererMixin {
   @Shadow
   private float field_4043;
   @Shadow
   private float field_4052;
   @Shadow
   private ItemStack field_4047;
   @Shadow
   private ItemStack field_4048;
   @Unique
   private static boolean revive$capturingHand;

   @Shadow
   protected abstract boolean method_65910(ItemStack var1, ItemStack var2);

   @Invoker("method_3228")
   abstract void revive$invokeRenderFirstPersonItem(
      AbstractClientPlayerEntity var1,
      float var2,
      float var3,
      Hand var4,
      float var5,
      ItemStack var6,
      float var7,
      MatrixStack var8,
      OrderedRenderCommandQueue var9,
      int var10
   );

   @ModifyArg(
      method = {"method_3220"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/class_3532;method_15363(FFF)F",
         ordinal = 3
      ),
      index = 0
   )
   private float modifyEquipProgressOffhand(float value) {
      return (this.method_65910(this.field_4048, ReviveClient.mc.player.getOffHandStack()) ? 1 : 0) - this.field_4052;
   }

   @Inject(
      method = {"method_3220"},
      at = {@At("TAIL")}
   )
   private void reviveHideMineSwitch(CallbackInfo ci) {
      if (ReviveClient.mc.player != null) {
         SilentMine sm = Modules.get().get(SilentMine.class);
         if (sm != null) {
            ItemStack hide = sm.getHideSwitchStack();
            if (hide != null) {
               this.field_4047 = hide;
               this.field_4043 = 1.0F;
            }
         }
      }
   }

   @Inject(
      method = {"method_3228"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/class_759;method_3233(Lnet/minecraft/class_1309;Lnet/minecraft/class_1799;Lnet/minecraft/class_811;Lnet/minecraft/class_4587;Lnet/minecraft/class_11659;I)V",
         shift = Shift.BEFORE
      )}
   )
   private void onRenderItem(
      AbstractClientPlayerEntity player,
      float tickDelta,
      float pitch,
      Hand hand,
      float swingProgress,
      ItemStack item,
      float equipProgress,
      MatrixStack matrices,
      OrderedRenderCommandQueue orderedRenderCommandQueue,
      int light,
      CallbackInfo ci
   ) {
      ReviveClient.EVENT_BUS.post(HeldItemRendererEvent.get(hand, matrices));
   }

   @Inject(
      method = {"method_3228"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/class_759;method_3219(Lnet/minecraft/class_4587;Lnet/minecraft/class_11659;IFFLnet/minecraft/class_1306;)V"
      )}
   )
   private void onRenderArm(
      AbstractClientPlayerEntity player,
      float tickDelta,
      float pitch,
      Hand hand,
      float swingProgress,
      ItemStack item,
      float equipProgress,
      MatrixStack matrices,
      OrderedRenderCommandQueue orderedRenderCommandQueue,
      int light,
      CallbackInfo ci
   ) {
      ReviveClient.EVENT_BUS.post(ArmRenderEvent.get(hand, matrices));
   }

   @Inject(
      method = {"method_3228"},
      at = {@At("RETURN")}
   )
   private void revive$chamsHandCapture(
      AbstractClientPlayerEntity player,
      float tickDelta,
      float pitch,
      Hand hand,
      float swingProgress,
      ItemStack item,
      float equipProgress,
      MatrixStack matrices,
      OrderedRenderCommandQueue orderedRenderCommandQueue,
      int light,
      CallbackInfo ci
   ) {
      if (!revive$capturingHand) {
         Chams chams = Modules.get().get(Chams.class);
         if (chams != null && chams.handChamsEnabled()) {
            if (PostProcessShaders.CHAMS != null && PostProcessShaders.CHAMS.shouldDraw()) {
               revive$capturingHand = true;

               try {
                  OrderedRenderCommandQueue capture = HandChams.INSTANCE.begin();
                  this.revive$invokeRenderFirstPersonItem(player, tickDelta, pitch, hand, swingProgress, item, equipProgress, matrices, capture, light);
                  HandChams.INSTANCE.end();
               } finally {
                  revive$capturingHand = false;
               }
            }
         }
      }
   }
}
