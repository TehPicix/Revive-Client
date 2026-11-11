package revivedevelopment.reviveclient.events.entity.player;

public class SendMovementPacketsEvent {
   public static class Post {
      private static final SendMovementPacketsEvent.Post INSTANCE = new SendMovementPacketsEvent.Post();

      public static SendMovementPacketsEvent.Post get() {
         return INSTANCE;
      }
   }

   public static class Pre {
      private static final SendMovementPacketsEvent.Pre INSTANCE = new SendMovementPacketsEvent.Pre();

      public static SendMovementPacketsEvent.Pre get() {
         return INSTANCE;
      }
   }

   public static class Rotation {
      public float yaw;
      public float pitch;
      public boolean forceFull;
      public boolean forceFullOnRotate;

      public Rotation(float yaw, float pitch) {
         this.yaw = yaw;
         this.pitch = pitch;
      }
   }
}
