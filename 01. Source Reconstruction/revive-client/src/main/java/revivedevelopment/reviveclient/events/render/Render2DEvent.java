package revivedevelopment.reviveclient.events.render;

import net.minecraft.client.gui.DrawContext;
import revivedevelopment.reviveclient.utils.Utils;

public class Render2DEvent {
   private static final Render2DEvent INSTANCE = new Render2DEvent();
   public DrawContext drawContext;
   public int screenWidth;
   public int screenHeight;
   public double frameTime;
   public float tickDelta;

   public static Render2DEvent get(DrawContext drawContext, int screenWidth, int screenHeight, float tickDelta) {
      INSTANCE.drawContext = drawContext;
      INSTANCE.screenWidth = screenWidth;
      INSTANCE.screenHeight = screenHeight;
      INSTANCE.frameTime = Utils.frameTime;
      INSTANCE.tickDelta = tickDelta;
      return INSTANCE;
   }
}
