package revivedevelopment.reviveclient.systems.emotes;

import net.minecraft.client.model.ModelPart;

public final class EmoteApplier {
   private EmoteApplier() {
   }

   public static void apply(EmotePose pose, String boneName, ModelPart part) {
      EmotePose.Bone bone = pose.bone(boneName);
      if (bone != null && bone.animated && part != null) {
         float ox = part.originX;
         float oy = part.originY;
         float oz = part.originZ;
         float pitch = part.pitch;
         float yaw = part.yaw;
         float roll = part.roll;
         float sx = part.xScale;
         float sy = part.yScale;
         float sz = part.zScale;
         part.resetTransform();
         float dx = part.originX;
         float dy = part.originY;
         float dz = part.originZ;
         float dyaw = part.yaw;
         part.xScale = sx;
         part.yScale = sy;
         part.zScale = sz;
         float weight = pose.weight();
         part.originX = lerp(weight, ox, bone.x + dx);
         part.originY = lerp(weight, oy, -bone.y + dy);
         part.originZ = lerp(weight, oz, bone.z + dz);
         part.pitch = lerp(weight, pitch, bone.rotX);
         part.yaw = lerp(weight, yaw, bone.rotY + dyaw);
         part.roll = lerp(weight, roll, bone.rotZ);
      }
   }

   public static void applyCape(EmotePose pose, ModelPart part) {
      EmotePose.Bone bone = pose.bone("cape");
      if (bone != null && bone.animated && part != null) {
         float weight = pose.weight();
         part.originX = part.originX + bone.x * weight;
         part.originY = part.originY + -bone.y * weight;
         part.originZ = part.originZ + bone.z * weight;
         part.pitch = part.pitch + bone.rotX * weight;
         part.yaw = part.yaw + bone.rotY * weight;
         part.roll = part.roll + bone.rotZ * weight;
      }
   }

   private static float lerp(float weight, float from, float to) {
      return from + (to - from) * weight;
   }
}
