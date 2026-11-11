package revivedevelopment.reviveclient.mixin;

import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.Vec2f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.systems.modules.Modules;
import revivedevelopment.reviveclient.systems.modules.movement.Scaffold;
import revivedevelopment.reviveclient.systems.modules.movement.Sprint;

@Mixin({KeyboardInput.class})
public abstract class KeyboardInputMixin extends Input {
   @Inject(
      method = {"method_3129"},
      at = {@At("TAIL")}
   )
   private void onTickSuppressMovement(CallbackInfo info) {
      Scaffold scaffold = Modules.get().get(Scaffold.class);
      if (scaffold != null && scaffold.suppressesMovementInput()) {
         this.movementVector = Vec2f.ZERO;
      } else if (!this.revive$applyMoveFix()) {
         Sprint sprint = Modules.get().get(Sprint.class);
         if (sprint != null) {
            if (sprint.shouldSuppressMovementInput()) {
               this.movementVector = Vec2f.ZERO;
            } else {
               if (sprint.isMotionSpoofActive()) {
                  Vec2f v = this.movementVector;
                  float ax = Math.abs(v.x);
                  float ay = Math.abs(v.y);
                  if (ax > 1.0E-5F && ay > 1.0E-5F) {
                     float ratio = ay > ax ? ax / ay : ay / ax;
                     float dist = (float)Math.sqrt(1.0 + ratio * ratio);
                     this.movementVector = new Vec2f(v.x / dist, v.y / dist);
                  }
               }
            }
         }
      }
   }

   private boolean revive$applyMoveFix() {
      if (!ReviveClient.ROTATION.moveFixPhysicsApplied()) {
         return false;
      } else {
         ClientPlayerEntity player = ReviveClient.mc.player;
         if (player == null) {
            return false;
         } else {
            Vec2f v = this.movementVector;
            float side = v.x;
            float fwd = v.y;
            if (Math.abs(side) < 1.0E-5F && Math.abs(fwd) < 1.0E-5F) {
               return true;
            } else {
               double camera = Math.toRadians(ReviveClient.ROTATION.getMoveFixCameraYaw());
               double cs = Math.cos(camera);
               double sn = Math.sin(camera);
               double wx = side * cs - fwd * sn;
               double wz = fwd * cs + side * sn;
               double wl = Math.sqrt(wx * wx + wz * wz);
               wx /= wl;
               wz /= wl;
               double aim = Math.toRadians(ReviveClient.ROTATION.getMotionYaw());
               double ca = Math.cos(aim);
               double sa = Math.sin(aim);
               int bestSide = 0;
               int bestFwd = 0;
               double bestDot = -2.0;

               for (int s = -1; s <= 1; s++) {
                  for (int f = -1; f <= 1; f++) {
                     if (s != 0 || f != 0) {
                        double dx = s * ca - f * sa;
                        double dz = f * ca + s * sa;
                        double dl = Math.sqrt(dx * dx + dz * dz);
                        double dot = (dx * wx + dz * wz) / dl;
                        if (dot > bestDot) {
                           bestDot = dot;
                           bestSide = s;
                           bestFwd = f;
                        }
                     }
                  }
               }

               PlayerInput in = this.playerInput;
               this.playerInput = new PlayerInput(bestFwd > 0, bestFwd < 0, bestSide > 0, bestSide < 0, in.jump(), in.sneak(), in.sprint());
               this.movementVector = new Vec2f(bestSide, bestFwd);
               return true;
            }
         }
      }
   }
}
