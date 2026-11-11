package revivedevelopment.reviveclient.events.entity;

import net.minecraft.entity.player.PlayerEntity;

public class PlayerDeathEvent {
   public static void release() {
      PlayerDeathEvent.TotemPop.INSTANCE.player = null;
      PlayerDeathEvent.Death.INSTANCE.player = null;
   }

   public static class Death extends PlayerDeathEvent {
      private static final PlayerDeathEvent.Death INSTANCE = new PlayerDeathEvent.Death();
      private PlayerEntity player;
      private int pops;

      public static PlayerDeathEvent.Death get(PlayerEntity player, int pop) {
         INSTANCE.player = player;
         INSTANCE.pops = pop;
         return INSTANCE;
      }

      public PlayerEntity getPlayer() {
         return this.player;
      }

      public int getPops() {
         return this.pops;
      }
   }

   public static class TotemPop extends PlayerDeathEvent {
      private static final PlayerDeathEvent.TotemPop INSTANCE = new PlayerDeathEvent.TotemPop();
      private PlayerEntity player;
      private int pops;

      public static PlayerDeathEvent get(PlayerEntity player, int pop) {
         INSTANCE.player = player;
         INSTANCE.pops = pop;
         return INSTANCE;
      }

      public PlayerEntity getPlayer() {
         return this.player;
      }

      public int getPops() {
         return this.pops;
      }
   }
}
