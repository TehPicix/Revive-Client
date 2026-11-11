package revivedevelopment.reviveclient.systems.hud;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import org.joml.Matrix3x2fStack;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.events.revive.CustomFontChangedEvent;
import revivedevelopment.reviveclient.renderer.Fonts;
import revivedevelopment.reviveclient.renderer.MeshBuilder;
import revivedevelopment.reviveclient.renderer.MeshRenderer;
import revivedevelopment.reviveclient.renderer.Renderer2D;
import revivedevelopment.reviveclient.renderer.ReviveRenderPipelines;
import revivedevelopment.reviveclient.renderer.text.CustomTextRenderer;
import revivedevelopment.reviveclient.renderer.text.Font;
import revivedevelopment.reviveclient.renderer.text.VanillaTextRenderer;
import revivedevelopment.reviveclient.systems.hud.screens.HudEditorScreen;
import revivedevelopment.reviveclient.utils.Utils;
import revivedevelopment.reviveclient.utils.render.RenderUtils;
import revivedevelopment.reviveclient.utils.render.color.Color;

public class HudRenderer {
   public static final HudRenderer INSTANCE = new HudRenderer();
   private static final double SCALE_TO_HEIGHT = 0.05555555555555555;
   private final Hud hud = Hud.get();
   private final List<Runnable> postTasks = new ArrayList<>();
   private final Int2ObjectMap<HudRenderer.FontHolder> fontsInUse = new Int2ObjectOpenHashMap();
   private final LoadingCache<Integer, HudRenderer.FontHolder> fontCache = CacheBuilder.newBuilder()
      .maximumSize(4L)
      .expireAfterAccess(Duration.ofMinutes(10L))
      .removalListener(notification -> {
         if (notification.wasEvicted()) {
            ((HudRenderer.FontHolder)notification.getValue()).destroy();
         }
      })
      .build(CacheLoader.from(HudRenderer::loadFont));
   public DrawContext drawContext;
   public double delta;
   public boolean deferText = false;

   private boolean useDeferredMetrics() {
      return this.deferText || HudEditorScreen.isOpen();
   }

   private HudRenderer() {
      ReviveClient.EVENT_BUS.subscribe(this);
   }

   public void begin(DrawContext drawContext) {
      HudStyle.update();
      Renderer2D.color().begin();
      this.drawContext = drawContext;
      this.delta = Utils.frameTime;
      if (!this.deferText && !this.hud.hasCustomFont()) {
         VanillaTextRenderer.INSTANCE.scaleIndividually = true;
         VanillaTextRenderer.INSTANCE.begin();
      }
   }

   public void end() {
      Renderer2D.color().render();
      if (!this.deferText) {
         if (this.hud.hasCustomFont()) {
            Iterator<HudRenderer.FontHolder> it = this.fontsInUse.values().iterator();

            while (it.hasNext()) {
               HudRenderer.FontHolder fontHolder = it.next();
               if (fontHolder.visited) {
                  MeshRenderer.begin()
                     .attachments(MinecraftClient.getInstance().getFramebuffer())
                     .pipeline(ReviveRenderPipelines.UI_TEXT)
                     .mesh(fontHolder.getMesh())
                     .sampler("u_Texture", fontHolder.font.texture.getGlTextureView(), fontHolder.font.texture.getSampler())
                     .end();
               } else {
                  it.remove();
                  this.fontCache.put(fontHolder.font.getHeight(), fontHolder);
               }

               fontHolder.visited = false;
            }
         } else {
            VanillaTextRenderer.INSTANCE.end();
            VanillaTextRenderer.INSTANCE.scaleIndividually = false;
         }
      }

      for (Runnable task : this.postTasks) {
         task.run();
      }

      this.postTasks.clear();
      this.drawContext = null;
   }

   public void line(double x1, double y1, double x2, double y2, Color color) {
      Renderer2D.color().line(x1, y1, x2, y2, color);
   }

   public void quad(double x, double y, double width, double height, Color color) {
      Renderer2D.color().quad(x, y, width, height, color);
   }

   public void quad(double x, double y, double width, double height, Color cTopLeft, Color cTopRight, Color cBottomRight, Color cBottomLeft) {
      Renderer2D.color().quad(x, y, width, height, cTopLeft, cTopRight, cBottomRight, cBottomLeft);
   }

   public void triangle(double x1, double y1, double x2, double y2, double x3, double y3, Color color) {
      Renderer2D.color().triangle(x1, y1, x2, y2, x3, y3, color);
   }

   public void roundedQuad(double x, double y, double w, double h, double r, Color color) {
      r = Math.max(0.0, Math.min(r, Math.min(w, h) / 2.0));
      if (r <= 0.01) {
         this.quad(x, y, w, h, color);
      } else {
         this.quad(x, y + r, w, h - 2.0 * r, color);
         this.quad(x + r, y, w - 2.0 * r, r, color);
         this.quad(x + r, y + h - r, w - 2.0 * r, r, color);
         this.cornerFan(x + r, y + r, r, Math.PI, Math.PI * 3.0 / 2.0, color);
         this.cornerFan(x + w - r, y + r, r, Math.PI * 3.0 / 2.0, Math.PI * 2, color);
         this.cornerFan(x + w - r, y + h - r, r, 0.0, Math.PI / 2, color);
         this.cornerFan(x + r, y + h - r, r, Math.PI / 2, Math.PI, color);
      }
   }

   private void cornerFan(double cx, double cy, double r, double a0, double a1, Color color) {
      int segments = Math.max(2, (int)Math.ceil(r));
      double step = (a1 - a0) / segments;

      for (int i = 0; i < segments; i++) {
         double sa = a0 + step * i;
         double ea = a0 + step * (i + 1);
         this.triangle(cx, cy, cx + Math.cos(sa) * r, cy + Math.sin(sa) * r, cx + Math.cos(ea) * r, cy + Math.sin(ea) * r, color);
      }
   }

   public void roundedOutline(double x, double y, double w, double h, double r, Color color) {
      r = Math.max(0.0, Math.min(r, Math.min(w, h) / 2.0));
      this.quad(x + r, y, w - 2.0 * r, 1.0, color);
      this.quad(x + r, y + h - 1.0, w - 2.0 * r, 1.0, color);
      this.quad(x, y + r, 1.0, h - 2.0 * r, color);
      this.quad(x + w - 1.0, y + r, 1.0, h - 2.0 * r, color);
      if (!(r <= 0.01)) {
         this.cornerArc(x + r, y + r, r, Math.PI, Math.PI * 3.0 / 2.0, color);
         this.cornerArc(x + w - r, y + r, r, Math.PI * 3.0 / 2.0, Math.PI * 2, color);
         this.cornerArc(x + w - r, y + h - r, r, 0.0, Math.PI / 2, color);
         this.cornerArc(x + r, y + h - r, r, Math.PI / 2, Math.PI, color);
      }
   }

   private void cornerArc(double cx, double cy, double r, double a0, double a1, Color color) {
      int segments = Math.max(2, (int)Math.ceil(r * 1.5));
      double step = (a1 - a0) / segments;

      for (int i = 0; i < segments; i++) {
         double sa = a0 + step * i;
         double ea = a0 + step * (i + 1);
         this.line(cx + Math.cos(sa) * r, cy + Math.sin(sa) * r, cx + Math.cos(ea) * r, cy + Math.sin(ea) * r, color);
      }
   }

   public void panel(double x, double y, double w, double h, Color background, Color border, Color accent) {
      this.roundedQuad(x, y, w, h, 2.0, background);
      if (accent != null) {
         this.quad(x, y + 2.0, 1.0, h - 4.0, accent);
      }

      if (border != null) {
         this.roundedOutline(x, y, w, h, 2.0, border);
      }
   }

   public void panel(double x, double y, double w, double h) {
      this.panel(x, y, w, h, HudStyle.BG_PANEL, HudStyle.BORDER, null);
   }

   public void texture(Identifier id, double x, double y, double width, double height, Color color) {
      Renderer2D.textured().begin();
      Renderer2D.textured().texQuad(x, y, width, height, color);
      Renderer2D.textured()
         .render(
            MinecraftClient.getInstance().getTextureManager().getTexture(id).getGlTextureView(),
            MinecraftClient.getInstance().getTextureManager().getTexture(id).getSampler()
         );
   }

   public double text(String text, double x, double y, Color color, boolean shadow, double scale) {
      if (scale == -1.0) {
         scale = this.hud.getTextScale();
      }

      if (this.deferText && this.drawContext != null) {
         MinecraftClient client = MinecraftClient.getInstance();
         double s = scale * 2.0;
         Matrix3x2fStack matrices = this.drawContext.getMatrices();
         matrices.pushMatrix();
         matrices.scale(1.0F / client.getWindow().getScaleFactor());
         matrices.scale((float)s, (float)s);
         this.drawContext.drawText(client.textRenderer, text, (int)Math.round(x / s), (int)Math.round(y / s), color.getPacked(), shadow);
         matrices.popMatrix();
         return x + client.textRenderer.getWidth(text) * s;
      } else if (!this.hud.hasCustomFont()) {
         VanillaTextRenderer.INSTANCE.scale = scale * 2.0;
         return VanillaTextRenderer.INSTANCE.render(text, x, y, color, shadow);
      } else {
         HudRenderer.FontHolder fontHolder = this.getFontHolder(scale, true);
         Font font = fontHolder.font;
         MeshBuilder mesh = fontHolder.getMesh();
         double width;
         if (shadow) {
            int preShadowA = CustomTextRenderer.SHADOW_COLOR.a;
            CustomTextRenderer.SHADOW_COLOR.a = (int)(color.a / 255.0 * preShadowA);
            width = font.render(mesh, text, x + 1.0, y + 1.0, CustomTextRenderer.SHADOW_COLOR, scale);
            font.render(mesh, text, x, y, color, scale);
            CustomTextRenderer.SHADOW_COLOR.a = preShadowA;
         } else {
            width = font.render(mesh, text, x, y, color, scale);
         }

         return width;
      }
   }

   public double text(String text, double x, double y, Color color, boolean shadow) {
      return this.text(text, x, y, color, shadow, -1.0);
   }

   public double textWidth(String text, boolean shadow, double scale) {
      if (text.isEmpty()) {
         return 0.0;
      } else if (this.useDeferredMetrics()) {
         double s = (scale == -1.0 ? this.hud.getTextScale() : scale) * 2.0;
         return MinecraftClient.getInstance().textRenderer.getWidth(text) * s;
      } else if (this.hud.hasCustomFont()) {
         double width = this.getFont(scale).getWidth(text, text.length());
         return (width + (shadow ? 1 : 0)) * (scale == -1.0 ? this.hud.getTextScale() : scale) + (shadow ? 1 : 0);
      } else {
         VanillaTextRenderer.INSTANCE.scale = (scale == -1.0 ? this.hud.getTextScale() : scale) * 2.0;
         return VanillaTextRenderer.INSTANCE.getWidth(text, shadow);
      }
   }

   public double textWidth(String text, boolean shadow) {
      return this.textWidth(text, shadow, -1.0);
   }

   public double textWidth(String text, double scale) {
      return this.textWidth(text, false, scale);
   }

   public double textWidth(String text) {
      return this.textWidth(text, false, -1.0);
   }

   public double textHeight(boolean shadow, double scale) {
      if (this.useDeferredMetrics()) {
         double s = (scale == -1.0 ? this.hud.getTextScale() : scale) * 2.0;
         return 9.0 * s;
      } else if (this.hud.hasCustomFont()) {
         double height = this.getFont(scale).getHeight() + 1;
         return (height + (shadow ? 1 : 0)) * (scale == -1.0 ? this.hud.getTextScale() : scale);
      } else {
         VanillaTextRenderer.INSTANCE.scale = (scale == -1.0 ? this.hud.getTextScale() : scale) * 2.0;
         return VanillaTextRenderer.INSTANCE.getHeight(shadow);
      }
   }

   public double textHeight(boolean shadow) {
      return this.textHeight(shadow, -1.0);
   }

   public double textHeight() {
      return this.textHeight(false, -1.0);
   }

   public void post(Runnable task) {
      this.postTasks.add(task);
   }

   public void item(ItemStack itemStack, int x, int y, float scale, boolean overlay, String countOverlay) {
      RenderUtils.drawItem(this.drawContext, itemStack, x, y, scale, overlay, countOverlay, true);
   }

   public void item(ItemStack itemStack, int x, int y, float scale, boolean overlay) {
      RenderUtils.drawItem(this.drawContext, itemStack, x, y, scale, overlay);
   }

   private HudRenderer.FontHolder getFontHolder(double scale, boolean render) {
      if (scale == -1.0) {
         scale = this.hud.getTextScale();
      }

      int height = (int)Math.round(scale / 0.05555555555555555);
      HudRenderer.FontHolder fontHolder = (HudRenderer.FontHolder)this.fontsInUse.get(height);
      if (fontHolder != null) {
         if (render) {
            fontHolder.visited = true;
         }

         return fontHolder;
      } else if (render) {
         fontHolder = (HudRenderer.FontHolder)this.fontCache.getIfPresent(height);
         if (fontHolder == null) {
            fontHolder = loadFont(height);
         } else {
            this.fontCache.invalidate(height);
         }

         this.fontsInUse.put(height, fontHolder);
         fontHolder.visited = true;
         return fontHolder;
      } else {
         return (HudRenderer.FontHolder)this.fontCache.getUnchecked(height);
      }
   }

   private Font getFont(double scale) {
      return this.getFontHolder(scale, false).font;
   }

   @EventHandler
   private void onCustomFontChanged(CustomFontChangedEvent event) {
      ObjectIterator var2 = this.fontsInUse.values().iterator();

      while (var2.hasNext()) {
         HudRenderer.FontHolder fontHolder = (HudRenderer.FontHolder)var2.next();
         fontHolder.destroy();
      }

      for (HudRenderer.FontHolder fontHolder : this.fontCache.asMap().values()) {
         fontHolder.destroy();
      }

      this.fontsInUse.clear();
      this.fontCache.invalidateAll();
   }

   private static HudRenderer.FontHolder loadFont(int height) {
      Fonts.ensureLoaded();

      try {
         ByteBuffer buffer = Fonts.RENDERER.fontFace.readToDirectByteBuffer();
         return new HudRenderer.FontHolder(new Font(buffer, height));
      } catch (IOException var2) {
         throw new RuntimeException("Failed to load font: " + Fonts.RENDERER.fontFace, var2);
      }
   }

   private static class FontHolder {
      public final Font font;
      public boolean visited;
      private MeshBuilder mesh;

      public FontHolder(Font font) {
         this.font = font;
      }

      public MeshBuilder getMesh() {
         if (this.mesh == null) {
            this.mesh = new MeshBuilder(ReviveRenderPipelines.UI_TEXT);
         }

         if (!this.mesh.isBuilding()) {
            this.mesh.begin();
         }

         return this.mesh;
      }

      public void destroy() {
         this.font.texture.close();
      }
   }
}
