package revivedevelopment.reviveclient.events.revive;

public class CustomFontChangedEvent {
   private static final CustomFontChangedEvent INSTANCE = new CustomFontChangedEvent();

   public static CustomFontChangedEvent get() {
      return INSTANCE;
   }
}
