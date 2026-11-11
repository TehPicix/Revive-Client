package revivedevelopment.reviveclient.systems.managers.team;

import java.util.UUID;

public final class RemoteLogout {
   public final UUID uuid;
   public final String name;
   public final String dim;
   public final double x;
   public final double y;
   public final double z;
   public final float hp;
   public final int pops;
   public final long at;
   public final String by;

   public RemoteLogout(UUID uuid, String name, String dim, double x, double y, double z, float hp, int pops, long at, String by) {
      this.uuid = uuid;
      this.name = name;
      this.dim = dim;
      this.x = x;
      this.y = y;
      this.z = z;
      this.hp = hp;
      this.pops = pops;
      this.at = at;
      this.by = by;
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

   public float hp() {
      return this.hp;
   }

   public int pops() {
      return this.pops;
   }

   public long at() {
      return this.at;
   }

   public String by() {
      return this.by;
   }
}
