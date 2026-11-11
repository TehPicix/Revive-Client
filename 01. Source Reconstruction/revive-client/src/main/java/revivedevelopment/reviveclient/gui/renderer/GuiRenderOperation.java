package revivedevelopment.reviveclient.gui.renderer;

import revivedevelopment.reviveclient.utils.misc.Pool;
import revivedevelopment.reviveclient.utils.render.color.Color;

public abstract class GuiRenderOperation<T extends GuiRenderOperation<T>> {
   protected double x;
   protected double y;
   protected Color color;

   public void set(double x, double y, Color color) {
      this.x = x;
      this.y = y;
      this.color = color;
   }

   public void run(Pool<T> pool) {
      this.onRun();
      pool.free((T)this);
   }

   protected abstract void onRun();
}
