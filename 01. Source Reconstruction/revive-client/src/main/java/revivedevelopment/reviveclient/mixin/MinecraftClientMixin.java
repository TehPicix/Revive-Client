package revivedevelopment.reviveclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.concurrent.CompletableFuture;
import meteordevelopment.starscript.Script;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.Window;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.resource.ReloadableResourceManagerImpl;
import net.minecraft.util.profiler.Profilers;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.events.game.GameLeftEvent;
import revivedevelopment.reviveclient.events.game.OpenScreenEvent;
import revivedevelopment.reviveclient.events.game.ResourcePacksReloadedEvent;
import revivedevelopment.reviveclient.events.world.TickEvent;
import revivedevelopment.reviveclient.gui.WidgetScreen;
import revivedevelopment.reviveclient.mixininterface.IMinecraftClient;
import revivedevelopment.reviveclient.systems.config.Config;
import revivedevelopment.reviveclient.systems.modules.Modules;
import revivedevelopment.reviveclient.systems.modules.movement.GUIMove;
import revivedevelopment.reviveclient.systems.modules.player.FastUse;
import revivedevelopment.reviveclient.utils.Utils;
import revivedevelopment.reviveclient.utils.misc.CPSUtils;
import revivedevelopment.reviveclient.utils.misc.ReviveStarscript;
import revivedevelopment.reviveclient.utils.misc.WindowIcon;
import revivedevelopment.reviveclient.utils.network.OnlinePlayers;

@Mixin(
   value = {MinecraftClient.class},
   priority = 1001
)
public abstract class MinecraftClientMixin implements IMinecraftClient {
   @Unique
   private boolean doItemUseCalled;
   @Unique
   private boolean rightClick;
   @Unique
   private long lastTime;
   @Unique
   private boolean firstFrame;
   @Shadow
   public ClientWorld field_1687;
   @Shadow
   @Final
   public Mouse field_1729;
   @Shadow
   @Final
   private Window field_1704;
   @Shadow
   public Screen field_1755;
   @Shadow
   @Final
   public GameOptions field_1690;
   @Shadow
   @Nullable
   public ClientPlayerInteractionManager field_1761;
   @Shadow
   private int field_1752;
   @Shadow
   @Nullable
   public ClientPlayerEntity field_1724;
   @Shadow
   @Final
   private ReloadableResourceManagerImpl field_1745;
   @Shadow
   @Final
   @Mutable
   private Framebuffer field_1689;

   @Shadow
   protected abstract void method_1583();

   @Inject(
      method = {"<init>"},
      at = {@At("TAIL")}
   )
   private void onInit(CallbackInfo info) {
      this.firstFrame = true;
      WindowIcon.apply(this.field_1704);
   }

   @Inject(
      at = {@At("HEAD")},
      method = {"method_1574"}
   )
   private void onPreTick(CallbackInfo info) {
      OnlinePlayers.update();
      this.doItemUseCalled = false;
      Profilers.get().push("revive-client_pre_update");
      ReviveClient.EVENT_BUS.post(TickEvent.Pre.get());
      Profilers.get().pop();
      if (this.rightClick && !this.doItemUseCalled && this.field_1761 != null) {
         this.method_1583();
      }

      this.rightClick = false;
   }

   @Inject(
      at = {@At("TAIL")},
      method = {"method_1574"}
   )
   private void onTick(CallbackInfo info) {
      Profilers.get().push("revive-client_post_update");
      ReviveClient.EVENT_BUS.post(TickEvent.Post.get());
      Profilers.get().pop();
   }

   @Inject(
      method = {"method_1536"},
      at = {@At("HEAD")}
   )
   private void onAttack(CallbackInfoReturnable<Boolean> cir) {
      CPSUtils.onAttack();
   }

   @Inject(
      method = {"method_1583"},
      at = {@At("HEAD")}
   )
   private void onDoItemUse(CallbackInfo info) {
      this.doItemUseCalled = true;
   }

   @Inject(
      method = {"method_1583"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/class_1799;method_45435(Lnet/minecraft/class_7699;)Z"
      )}
   )
   private void onDoItemUseFastUse(CallbackInfo ci, @Local ItemStack itemStack) {
      FastUse fastUse = Modules.get().get(FastUse.class);
      if (fastUse.isActive()) {
         this.field_1752 = fastUse.getItemUseCooldown(itemStack);
      }
   }

   @Inject(
      method = {"method_18096"},
      at = {@At("HEAD")}
   )
   private void onDisconnect(Screen screen, boolean transferring, boolean bl, CallbackInfo info) {
      if (this.field_1687 != null) {
         ReviveClient.EVENT_BUS.post(GameLeftEvent.get());
      }
   }

   @Inject(
      method = {"method_1507"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onSetScreen(Screen screen, CallbackInfo info) {
      if (screen instanceof WidgetScreen) {
         screen.mouseMoved(this.field_1729.getX() * this.field_1704.getScaleFactor(), this.field_1729.getY() * this.field_1704.getScaleFactor());
      }

      OpenScreenEvent event = OpenScreenEvent.get(screen);
      ReviveClient.EVENT_BUS.post(event);
      if (event.isCancelled()) {
         info.cancel();
      }
   }

   @WrapOperation(
      method = {"method_1507"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/class_304;method_1437()V"
      )}
   )
   private void onSetScreenKeyBindingUnpressAll(Operation<Void> op) {
      Modules modules = Modules.get();
      if (modules == null) {
         op.call(new Object[0]);
      } else {
         GUIMove guiMove = modules.get(GUIMove.class);
         if (guiMove != null && guiMove.isActive() && !guiMove.skip()) {
            for (KeyBinding kb : KeyBindingAccessor.getKeysById().values()) {
               if (kb != this.field_1690.forwardKey
                  && kb != this.field_1690.leftKey
                  && kb != this.field_1690.rightKey
                  && kb != this.field_1690.backKey
                  && (!guiMove.sneak.get() || kb != this.field_1690.sneakKey)
                  && (!guiMove.sprint.get() || kb != this.field_1690.sprintKey)
                  && (!guiMove.jump.get() || kb != this.field_1690.jumpKey)) {
                  ((KeyBindingAccessor)kb).invokeReset();
               }
            }
         } else {
            op.call(new Object[0]);
         }
      }
   }

   @ModifyReturnValue(
      method = {"method_36561"},
      at = {@At("RETURN")}
   )
   private CompletableFuture<Void> onReloadResourcesNewCompletableFuture(CompletableFuture<Void> original) {
      return original.thenRun(() -> ReviveClient.EVENT_BUS.post(ResourcePacksReloadedEvent.get()));
   }

   @ModifyArg(
      method = {"method_24288"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/class_1041;method_24286(Ljava/lang/String;)V"
      )
   )
   private String setTitle(String original) {
      if (Config.get() != null && Config.get().customWindowTitle.get()) {
         String customTitle = Config.get().customWindowTitleText.get();
         Script script = ReviveStarscript.compile(customTitle);
         if (script != null) {
            String title = ReviveStarscript.run(script);
            if (title != null) {
               customTitle = title;
            }
         }

         return customTitle;
      } else {
         return revive$brandTitle(original);
      }
   }

   @Unique
   private static String revive$brandTitle(String original) {
      if (original != null && original.startsWith("Minecraft")) {
         String rest = original.substring("Minecraft".length());
         if (rest.startsWith("*")) {
            rest = rest.substring(1);
         }

         return "Revive Client" + rest;
      } else {
         return original;
      }
   }

   @WrapWithCondition(
      method = {"method_1508"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/class_636;method_2897(Lnet/minecraft/class_1657;)V"
      )}
   )
   private boolean wrapStopUsing(ClientPlayerInteractionManager instance, PlayerEntity player) {
      return true;
   }

   @Inject(
      method = {"method_1523"},
      at = {@At("HEAD")}
   )
   private void onRender(CallbackInfo info) {
      long time = System.currentTimeMillis();
      if (this.firstFrame) {
         this.lastTime = time;
         this.firstFrame = false;
      }

      Utils.frameTime = (time - this.lastTime) / 1000.0;
      this.lastTime = time;
   }

   @Override
   public void revive$rightClick() {
      this.rightClick = true;
   }

   @Override
   public void revive$setFramebuffer(Framebuffer framebuffer) {
      this.field_1689 = framebuffer;
   }
}
