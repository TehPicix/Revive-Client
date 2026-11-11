package revivedevelopment.reviveclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyReceiver;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.TooltipComponent;
import net.minecraft.item.tooltip.TooltipData;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2fStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import revivedevelopment.reviveclient.gui.newgui.GuiTextMesh;
import revivedevelopment.reviveclient.utils.tooltip.ReviveTooltipData;

@Mixin({DrawContext.class})
public abstract class DrawContextMixin {
   @Inject(
      method = {"method_44379"},
      at = {@At("HEAD")},
      require = 0
   )
   private void revive$onEnableScissor(int x1, int y1, int x2, int y2, CallbackInfo ci) {
      Matrix3x2fStack m = ((DrawContext)(Object)this).getMatrices();
      float ax = m.m00() * x1 + m.m20();
      float bx = m.m00() * x2 + m.m20();
      float ay = m.m11() * y1 + m.m21();
      float by = m.m11() * y2 + m.m21();
      GuiTextMesh.pushScissor(Math.min(ax, bx), Math.min(ay, by), Math.max(ax, bx), Math.max(ay, by));
   }

   @Inject(
      method = {"method_44380"},
      at = {@At("HEAD")},
      require = 0
   )
   private void revive$onDisableScissor(CallbackInfo ci) {
      GuiTextMesh.popScissor();
   }

   @Inject(
      method = {"method_51437"},
      at = {@At(
         value = "INVOKE",
         target = "Ljava/util/Optional;ifPresent(Ljava/util/function/Consumer;)V",
         shift = Shift.BEFORE
      )}
   )
   private void onDrawTooltip(
      TextRenderer textRenderer,
      List<Text> text,
      Optional<TooltipData> data,
      int x,
      int y,
      @Nullable Identifier texture,
      CallbackInfo info,
      @Local(ordinal = 1) List<TooltipComponent> list
   ) {
      if (data.isPresent() && data.get() instanceof ReviveTooltipData reviveTooltipData) {
         list.add(reviveTooltipData.getComponent());
      }
   }

   @ModifyReceiver(
      method = {"method_51437"},
      at = {@At(
         value = "INVOKE",
         target = "Ljava/util/Optional;ifPresent(Ljava/util/function/Consumer;)V"
      )}
   )
   private Optional<TooltipData> onDrawTooltip_modifyIfPresentReceiver(Optional<TooltipData> data, Consumer<TooltipData> consumer) {
      return data.isPresent() && data.get() instanceof ReviveTooltipData ? Optional.empty() : data;
   }
}
