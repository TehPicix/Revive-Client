package revivedevelopment.reviveclient.utils.misc.input;

public enum KeyAction {
   Press,
   Repeat,
   Release;

   public static KeyAction get(int action) {
      return switch (action) {
         case 0 -> Release;
         case 1 -> Press;
         default -> Repeat;
      };
   }
}
