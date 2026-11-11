package revivedevelopment.reviveclient.systems.managers.team;

import java.util.UUID;
import javax.annotation.Nullable;

public final class TeamMate {
   public final UUID uuid;
   public final String name;
   public final String dim;
   public final double x;
   public final double y;
   public final double z;
   public final float yaw;
   public final float hp;
   public final float absorb;
   @Nullable
   public final TeamInventory inv;
   public final long receivedAt;

   public TeamMate(
      UUID uuid, String name, String dim, double x, double y, double z, float yaw, float hp, float absorb, @Nullable TeamInventory inv, long receivedAt
   ) {
      this.uuid = uuid;
      this.name = name;
      this.dim = dim;
      this.x = x;
      this.y = y;
      this.z = z;
      this.yaw = yaw;
      this.hp = hp;
      this.absorb = absorb;
      this.inv = inv;
      this.receivedAt = receivedAt;
   }

   public UUID uuid() {
      return this.uuid;
   }

   public String name() {
      return this.name;
   }

   public String dim() {
      return this.dim;
   }

   public double x() {
      return this.x;
   }

   public double y() {
      return this.y;
   }

   public double z() {
      return this.z;
   }

   public float yaw() {
      return this.yaw;
   }

   public float hp() {
      return this.hp;
   }

   public float absorb() {
      return this.absorb;
   }

   @Nullable
   public TeamInventory inv() {
      return this.inv;
   }

   public long receivedAt() {
      return this.receivedAt;
   }
}
