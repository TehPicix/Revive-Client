package revivedevelopment.reviveclient.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.profiler.Profilers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.events.render.Render2DEvent;
import revivedevelopment.reviveclient.systems.modules.Modules;
import revivedevelopment.reviveclient.systems.modules.player.SilentMine;
import revivedevelopment.reviveclient.systems.modules.render.BetterTooltips;
import revivedevelopment.reviveclient.systems.modules.render.NoRender;
import revivedevelopment.reviveclient.utils.Utils;
import revivedevelopment.reviveclient.utils.render.RenderUtils;

@Mixin({InGameHud.class})
public abstract class InGameHudMixin {
   @Unique
   private static final ItemStack[] REVIVE_HOTBAR_ITEMS = new ItemStack[27];

   @Shadow
   public abstract void method_1747();

   @Redirect(
      method = {"method_1759"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/class_1661;method_67532()I"
      )
   )
   private int reviveHideMineSwitchSelectedSlot(PlayerInventory inventory) {
      SilentMine sm = Modules.get().get(SilentMine.class);
      return sm != null && sm.isHideSwitchActive() ? sm.getVisualSelectedSlot() : inventory.getSelectedSlot();
   }

   @Redirect(
      method = {"method_1759"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/class_1661;method_5438(I)Lnet/minecraft/class_1799;"
      )
   )
   private ItemStack reviveHideMineSwitchStack(PlayerInventory inventory, int slot) {
      SilentMine sm = Modules.get().get(SilentMine.class);
      return sm != null && sm.isHideSwitchActive() ? sm.getVisualHotbarStack(slot) : inventory.getStack(slot);
   }

   @Inject(
      method = {"method_1759"},
      at = {@At("TAIL")}
   )
   private void revive$hotbarShulkerIcons(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
      BetterTooltips tooltips = Modules.get().get(BetterTooltips.class);
      if (tooltips != null && tooltips.shulkerIconHotbar()) {
         PlayerEntity player = MinecraftClient.getInstance().player;
         if (player != null) {
            int center = context.getScaledWindowWidth() / 2;
            int y = context.getScaledWindowHeight() - 16 - 3;
            PlayerInventory inv = player.getInventory();

            for (int i = 0; i < 9; i++) {
               revive$drawShulkerIcon(context, tooltips, inv.getStack(i), center - 90 + i * 20 + 2, y);
            }

            int offX = player.getMainArm() == Arm.RIGHT ? center - 91 - 26 : center + 91 + 10;
            revive$drawShulkerIcon(context, tooltips, player.getOffHandStack(), offX, y);
         }
      }
   }

   @Unique
   private static void revive$drawShulkerIcon(DrawContext context, BetterTooltips tooltips, ItemStack stack, int x, int y) {
      if (!stack.isEmpty() && Utils.hasItems(stack)) {
         Utils.getItemsInContainerItem(stack, REVIVE_HOTBAR_ITEMS);
         ItemStack icon = tooltips.shulkerIconFor(REVIVE_HOTBAR_ITEMS);
         if (!icon.isEmpty()) {
            RenderUtils.drawItem(context, icon, x, y, 0.5F, false, null, false);
            if (tooltips.shulkerMultiIndicator() && tooltips.shulkerHasMultipleTypes(REVIVE_HOTBAR_ITEMS)) {
               TextRenderer tr = MinecraftClient.getInstance().textRenderer;
               context.drawText(tr, "+", x + 16 - tr.getWidth("+") - 1, y + 1, -256, true);
            }
         }
      }
   }

   @Inject(
      method = {"method_1749"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void reviveHideMineSwitchTooltip(DrawContext context, CallbackInfo ci) {
      SilentMine sm = Modules.get().get(SilentMine.class);
      if (sm != null && sm.isHideSwitchActive()) {
         ci.cancel();
      } else {
         if (Modules.get().get(NoRender.class).noHeldItemName()) {
            ci.cancel();
         }
      }
   }

   @Inject(
      method = {"method_1736"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void noRenderCrosshair(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
      if (Modules.get().get(NoRender.class).noCrosshair()) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"method_1735"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void noRenderVignette(DrawContext context, Entity entity, CallbackInfo ci) {
      if (Modules.get().get(NoRender.class).noVignette()) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"method_1746"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void noRenderPortal(DrawContext context, float tickProgress, CallbackInfo ci) {
      if (Modules.get().get(NoRender.class).noPortalOverlay()) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"method_1765"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void noRenderPotionIcons(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
      if (Modules.get().get(NoRender.class).noPotionIcons()) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"method_32598"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void noRenderSpyglass(DrawContext context, float scale, CallbackInfo ci) {
      if (Modules.get().get(NoRender.class).noSpyglassOverlay()) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"method_55803"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void noRenderScoreboard(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
      if (Modules.get().get(NoRender.class).noScoreboard()) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"method_55801"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void noRenderTitle(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
      if (Modules.get().get(NoRender.class).noTitle()) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"method_1753"},
      at = {@At("TAIL")}
   )
   private void onRender(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
      Profilers.get().push("revive-client_render_2d");
      Utils.unscaledProjection();
      ReviveClient.EVENT_BUS
         .post(Render2DEvent.get(context, context.getScaledWindowWidth(), context.getScaledWindowHeight(), tickCounter.getTickProgress(true)));
      Utils.scaledProjection();
      Profilers.get().pop();
   }
}
