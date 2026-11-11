package revivedevelopment.reviveclient.gui.newgui;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.BufferUtils;
import revivedevelopment.reviveclient.renderer.MeshBuilder;
import revivedevelopment.reviveclient.renderer.MeshRenderer;
import revivedevelopment.reviveclient.renderer.ReviveRenderPipelines;
import revivedevelopment.reviveclient.renderer.text.Font;
import revivedevelopment.reviveclient.utils.Utils;
import revivedevelopment.reviveclient.utils.render.color.Color;

public final class GuiTextMesh {
   private static final int MAX_FONTS = 24;
   private static final long RETRY_MS = 2000L;
   private static final List<Font> RETIRED = new ArrayList<>();
   private static final Map<String, Font> FONTS = new LinkedHashMap<String, Font>(16, 0.75F, true) {
      @Override
      protected boolean removeEldestEntry(java.util.Map.Entry<String, Font> eldest) {
         if (this.size() <= 24) {
            return false;
         } else {
            if (eldest.getValue() != null) {
               GuiTextMesh.RETIRED.add(eldest.getValue());
            }

            return true;
         }
      }
   };
   private static final Map<String, Long> FAILED = new HashMap<>();
   private static final Map<String, ByteBuffer> TTF = new HashMap<>();
   private static final List<GuiTextMesh.Entry> QUEUE = new ArrayList<>();
   private static final Deque<int[]> SCISSOR = new ArrayDeque<>();
   private static final List<GuiTextMesh.Region> REGIONS = new ArrayList<>();
   private static int seq = 0;

   private GuiTextMesh() {
   }

   public static void pushScissor(float x1, float y1, float x2, float y2) {
      float sf = MinecraftClient.getInstance().getWindow().getScaleFactor();
      int nx1 = Math.round(x1 * sf);
      int ny1 = Math.round(y1 * sf);
      int nx2 = Math.round(x2 * sf);
      int ny2 = Math.round(y2 * sf);
      int[] top = SCISSOR.peek();
      if (top != null) {
         nx1 = Math.max(nx1, top[0]);
         ny1 = Math.max(ny1, top[1]);
         nx2 = Math.min(nx2, top[2]);
         ny2 = Math.min(ny2, top[3]);
      }

      int[] rect = new int[]{nx1, ny1, nx2, ny2};
      SCISSOR.push(rect);
      REGIONS.add(new GuiTextMesh.Region(nx1, ny1, nx2, ny2, seq++, SCISSOR.peekLast()));
   }

   public static void popScissor() {
      SCISSOR.poll();
   }

   public static void pushOccluder(int x1, int y1, int x2, int y2) {
      float sf = MinecraftClient.getInstance().getWindow().getScaleFactor();
      REGIONS.add(new GuiTextMesh.Region(Math.round(x1 * sf), Math.round(y1 * sf), Math.round(x2 * sf), Math.round(y2 * sf), seq++, null));
   }

   private static ByteBuffer ttf(String resource) {
      return TTF.computeIfAbsent(resource, r -> {
         try {
            ByteBuffer var4;
            try (InputStream in = GuiTextMesh.class.getResourceAsStream(r)) {
               if (in == null) {
                  return null;
               }

               byte[] bytes = in.readAllBytes();
               ByteBuffer buf = BufferUtils.createByteBuffer(bytes.length);
               buf.put(bytes).flip();
               var4 = buf;
            }

            return var4;
         } catch (Exception var7) {
            return null;
         }
      });
   }

   private static Font font(String resource, int pixelHeight) {
      String key = resource + "@" + pixelHeight;
      Font cached = FONTS.get(key);
      if (cached != null) {
         return cached;
      } else {
         Long failedAt = FAILED.get(key);
         if (failedAt != null && System.currentTimeMillis() - failedAt < 2000L) {
            return null;
         } else {
            ByteBuffer ttf = ttf(resource);
            if (ttf == null) {
               return null;
            } else {
               try {
                  Font font = new Font(ttf.duplicate(), pixelHeight, 1);
                  FAILED.remove(key);
                  FONTS.put(key, font);
                  return font;
               } catch (Throwable var7) {
                  FAILED.put(key, System.currentTimeMillis());
                  System.out.println("[GuiTextMesh] font bake failed (" + key + "): " + var7);
                  return null;
               }
            }
         }
      }
   }

   private static void closeRetired() {
      if (!RETIRED.isEmpty()) {
         for (Font f : RETIRED) {
            try {
               f.texture.close();
            } catch (Throwable var3) {
            }
         }

         RETIRED.clear();
      }
   }

   public static double width(String resource, String text, int pixelHeight) {
      if (text != null && !text.isEmpty()) {
         Font f = font(resource, Math.max(1, pixelHeight));
         return f == null ? 0.0 : f.getWidth(text, text.length());
      } else {
         return 0.0;
      }
   }

   public static boolean available(String resource) {
      return ttf(resource) != null;
   }

   public static boolean canRender(String resource, String text) {
      return text != null && available(resource) && Font.canDisplay(text);
   }

   public static boolean queue(String resource, String text, double nx, double ny, int pixelHeight, int argb) {
      if (text == null || text.isEmpty()) {
         return true;
      } else if (!Font.canDisplay(text)) {
         return false;
      } else {
         Font f = font(resource, Math.max(1, pixelHeight));
         if (f == null) {
            return false;
         } else {
            nx = Math.round(nx);
            double base = f.baselineOffset();
            ny = Math.round(ny + base) - base;
            int a = argb >>> 24 & 0xFF;
            int r = argb >> 16 & 0xFF;
            int g = argb >> 8 & 0xFF;
            int b = argb & 0xFF;
            int[] scissor = SCISSOR.peek();
            int[] root = SCISSOR.peekLast();
            QUEUE.add(new GuiTextMesh.Entry(f, text, nx, ny, new Color(r, g, b, a), scissor, root, seq++));
            return true;
         }
      }
   }

   public static void renderQueued() {
      if (QUEUE.isEmpty()) {
         SCISSOR.clear();
         REGIONS.clear();
         seq = 0;
         closeRetired();
      } else {
         boolean unscaled = false;

         try {
            Utils.unscaledProjection();
            unscaled = true;
            Map<Font, MeshBuilder> meshes = new IdentityHashMap<>();
            int n = QUEUE.size();

            for (int i = 0; i < n; i++) {
               GuiTextMesh.Entry e = QUEUE.get(i);
               double w = e.font.getWidth(e.text, e.text.length());
               double h = e.font.getHeight();
               if (e.scissor != null) {
                  int[] s = e.scissor;
                  if (e.x + w <= s[0] || e.x >= s[2] || e.y + h <= s[1] || e.y >= s[3]) {
                     continue;
                  }
               }

               double cy = e.y + h / 2.0;
               double visL = e.x;
               double visR = e.x + w;

               for (GuiTextMesh.Region rg : REGIONS) {
                  if (rg.seq > e.seq && (rg.root == null || rg.root != e.root) && !(cy < rg.y1) && !(cy >= rg.y2) && !(rg.x2 <= visL) && !(rg.x1 >= visR)) {
                     boolean coversLeft = rg.x1 <= visL;
                     boolean coversRight = rg.x2 >= visR;
                     if (coversLeft && coversRight) {
                        visR = visL;
                        break;
                     }

                     if (coversRight) {
                        visR = Math.min(visR, (double)rg.x1);
                     } else if (coversLeft) {
                        visL = Math.max(visL, (double)rg.x2);
                     } else if (rg.x1 - visL >= visR - rg.x2) {
                        visR = rg.x1;
                     } else {
                        visL = rg.x2;
                     }
                  }
               }

               if (!(visR - visL < 0.5)) {
                  MeshBuilder mb = meshes.get(e.font);
                  if (mb == null) {
                     mb = new MeshBuilder(ReviveRenderPipelines.UI_TEXT);
                     mb.begin();
                     meshes.put(e.font, mb);
                  }

                  if (visL <= e.x + 0.5 && visR >= e.x + w - 0.5) {
                     e.font.render(mb, e.text, e.x, e.y, e.color, 1.0);
                  } else {
                     e.font.renderClipped(mb, e.text, e.x, e.y, e.color, 1.0, visL, visR);
                  }
               }
            }

            for (Map.Entry<Font, MeshBuilder> en : meshes.entrySet()) {
               Font font = en.getKey();
               MeshBuilder mbx = en.getValue();
               mbx.end();
               MeshRenderer.begin()
                  .attachments(MinecraftClient.getInstance().getFramebuffer())
                  .pipeline(ReviveRenderPipelines.UI_TEXT)
                  .mesh(mbx)
                  .sampler("u_Texture", font.texture.getGlTextureView(), font.texture.getSampler())
                  .end();
            }
         } catch (Throwable var27) {
            System.out.println("[GuiTextMesh] render failed: " + var27);
         } finally {
            if (unscaled) {
               try {
                  Utils.scaledProjection();
               } catch (Throwable var26) {
               }
            }

            QUEUE.clear();
            SCISSOR.clear();
            REGIONS.clear();
            seq = 0;
            closeRetired();
         }
      }
   }

   private record Entry(Font font, String text, double x, double y, Color color, int[] scissor, int[] root, int seq) {
   }

   private record Region(int x1, int y1, int x2, int y2, int seq, int[] root) {
   }
}
