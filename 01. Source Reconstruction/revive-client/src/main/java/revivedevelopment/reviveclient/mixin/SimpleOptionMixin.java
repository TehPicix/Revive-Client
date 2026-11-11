package revivedevelopment.reviveclient.mixin;

import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.SimpleOption;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import revivedevelopment.reviveclient.mixininterface.ISimpleOption;

@Mixin({SimpleOption.class})
public abstract class SimpleOptionMixin implements ISimpleOption {
   @Shadow
   Object field_37868;
   @Shadow
   @Final
   private Consumer<Object> field_37867;

   @Override
   public void setValue(Object value) {
      if (!MinecraftClient.getInstance().isRunning()) {
         this.field_37868 = value;
      } else if (!Objects.equals(this.field_37868, value)) {
         this.field_37868 = value;
         this.field_37867.accept(this.field_37868);
      }
   }
}
