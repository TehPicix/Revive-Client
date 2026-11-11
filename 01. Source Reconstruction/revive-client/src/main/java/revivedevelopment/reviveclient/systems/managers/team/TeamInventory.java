package revivedevelopment.reviveclient.systems.managers.team;

public final class TeamInventory {
   public final int crystals;
   public final int totems;
   public final int pearls;
   public final int obsidian;
   public final int gapples;
   public final int xp;

   public TeamInventory(int crystals, int totems, int pearls, int obsidian, int gapples, int xp) {
      this.crystals = crystals;
      this.totems = totems;
      this.pearls = pearls;
      this.obsidian = obsidian;
      this.gapples = gapples;
      this.xp = xp;
   }

   public int crystals() {
      return this.crystals;
   }

   public int totems() {
      return this.totems;
   }

   public int pearls() {
      return this.pearls;
   }

   public int obsidian() {
      return this.obsidian;
   }

   public int gapples() {
      return this.gapples;
   }

   public int xp() {
      return this.xp;
   }
}
