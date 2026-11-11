package revivedevelopment.reviveclient.gui.renderer;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.gui.utils.Cell;
import revivedevelopment.reviveclient.gui.widgets.WWidget;
import revivedevelopment.reviveclient.gui.widgets.containers.WContainer;
import revivedevelopment.reviveclient.renderer.MeshBuilder;
import revivedevelopment.reviveclient.renderer.MeshRenderer;
import revivedevelopment.reviveclient.renderer.ReviveRenderPipelines;
import revivedevelopment.reviveclient.utils.render.color.Color;

public class GuiDebugRenderer {
   private static final Color CELL_COLOR = new Color(25, 225, 25);
   private static final Color WIDGET_COLOR = new Color(25, 25, 225);
   private final MeshBuilder mesh = new MeshBuilder(ReviveRenderPipelines.WORLD_COLORED_LINES);

   public void render(WWidget widget) {
      if (widget != null) {
         this.mesh.begin();
         this.renderWidget(widget);
         this.mesh.end();
         MeshRenderer.begin()
            .attachments(MinecraftClient.getInstance().getFramebuffer())
            .pipeline(ReviveRenderPipelines.WORLD_COLORED_LINES)
            .mesh(this.mesh)
            .end();
      }
   }

   public void mouseReleased(WWidget widget, Click click, int i) {
      if (widget != null) {
         ReviveClient.LOG.info("{} {}", widget.getClass(), i);
         if (widget instanceof WContainer container) {
            for (Cell<?> cell : container.cells) {
               if (cell.widget().isOver(click.x(), click.y())) {
                  this.mouseReleased(cell.widget(), click, i + 1);
               }
            }
         }
      }
   }

   private void renderWidget(WWidget widget) {
      this.lineBox(widget.x, widget.y, widget.width, widget.height, WIDGET_COLOR);
      if (widget instanceof WContainer container) {
         for (Cell<?> cell : container.cells) {
            this.lineBox(cell.x, cell.y, cell.width, cell.height, CELL_COLOR);
            this.renderWidget(cell.widget());
         }
      }
   }

   private void lineBox(double x, double y, double width, double height, Color color) {
      this.line(x, y, x + width, y, color);
      this.line(x + width, y, x + width, y + height, color);
      this.line(x, y, x, y + height, color);
      this.line(x, y + height, x + width, y + height, color);
   }

   private void line(double x1, double y1, double x2, double y2, Color color) {
      this.mesh.ensureLineCapacity();
      this.mesh.line(this.mesh.vec3(x1, y1, 0.0).color(color).next(), this.mesh.vec3(x2, y2, 0.0).color(color).next());
   }
}
