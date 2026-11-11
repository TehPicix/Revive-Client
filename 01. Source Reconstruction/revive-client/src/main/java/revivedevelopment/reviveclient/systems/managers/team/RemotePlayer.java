package revivedevelopment.reviveclient.systems.managers.team;

import java.util.List;
import java.util.UUID;

public final class RemotePlayer {
   public final UUID uuid;
   public final String name;
   public final String dim;
   public final double x;
   public final double y;
   public final double z;
   public final float yaw;
   public final float hp;
   public final float absorb;
   public final List<String> seenBy;
   public final boolean visible;
   public final long outMs;
   public final int pops;
   public final long receivedAt;

   public RemotePlayer(
      UUID uuid,
      String name,
      String dim,
      double x,
      double y,
      double z,
      float yaw,
      float hp,
      float absorb,
      List<String> seenBy,
      boolean visible,
      long outMs,
      int pops,
      long receivedAt
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
      this.seenBy = seenBy;
      this.visible = visible;
      this.outMs = outMs;
      this.pops = pops;
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

   public List<String> seenBy() {
      return this.seenBy;
   }

   public boolean visible() {
      return this.visible;
   }

   public long outMs() {
      return this.outMs;
   }

   public int pops() {
      return this.pops;
   }

   public long receivedAt() {
      return this.receivedAt;
   }
}
