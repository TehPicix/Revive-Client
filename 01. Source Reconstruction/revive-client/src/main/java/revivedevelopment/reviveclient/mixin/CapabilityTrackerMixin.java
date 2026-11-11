package revivedevelopment.reviveclient.mixin;

import com.mojang.blaze3d.opengl.GlStateManager.CapabilityTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import revivedevelopment.reviveclient.mixininterface.ICapabilityTracker;

@Mixin({CapabilityTracker.class})
public abstract class CapabilityTrackerMixin implements ICapabilityTracker {
   @Shadow
   private boolean field_5051;

   @Shadow
   public abstract void method_4470(boolean var1);

   @Override
   public boolean revive$get() {
      return this.field_5051;
   }

   @Override
   public void revive$set(boolean state) {
      this.method_4470(state);
   }
}
