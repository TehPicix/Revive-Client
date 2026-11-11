package revivedevelopment.reviveclient.gui.renderer;

import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.List;
import revivedevelopment.reviveclient.mixininterface.IGpuDevice;
import revivedevelopment.reviveclient.utils.Utils;

public class Scissor {
   public int x;
   public int y;
   public int width;
   public int height;
   public final List<Runnable> postTasks = new ArrayList<>();

   public Scissor set(double x, double y, double width, double height) {
      if (width < 0.0) {
         width = 0.0;
      }

      if (height < 0.0) {
         height = 0.0;
      }

      this.x = (int)Math.round(x);
      this.y = (int)Math.round(y);
      this.width = (int)Math.round(width);
      this.height = (int)Math.round(height);
      this.postTasks.clear();
      return this;
   }

   public void push() {
      ((IGpuDevice)RenderSystem.getDevice()).revive$pushScissor(this.x, Utils.getWindowHeight() - this.y - this.height, this.width, this.height);
   }

   public void pop() {
      ((IGpuDevice)RenderSystem.getDevice()).revive$popScissor();
   }
}
