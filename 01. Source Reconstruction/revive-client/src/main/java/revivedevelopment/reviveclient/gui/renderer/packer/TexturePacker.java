package revivedevelopment.reviveclient.gui.renderer.packer;

import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.TextureFormat;
import java.io.IOException;
import java.io.InputStream;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;
import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBImage;
import org.lwjgl.stb.STBImageResize;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.renderer.Texture;

public class TexturePacker {
   private static final int maxWidth = 2048;
   private final List<TexturePacker.Image> images = new ArrayList<>();

   public GuiTexture add(Identifier id) {
      try {
         InputStream in = ((Resource)ReviveClient.mc.getResourceManager().getResource(id).get()).getInputStream();
         GuiTexture texture = new GuiTexture();
         MemoryStack stack = MemoryStack.stackPush();

         try {
            ByteBuffer rawImageBuffer = null;

            try {
               rawImageBuffer = TextureUtil.readResource(in);
               ((Buffer)rawImageBuffer).rewind();
               IntBuffer w = stack.mallocInt(1);
               IntBuffer h = stack.mallocInt(1);
               IntBuffer ignored = stack.mallocInt(1);
               ByteBuffer imageBuffer = STBImage.stbi_load_from_memory(rawImageBuffer, w, h, ignored, 4);
               int width = w.get(0);
               int height = h.get(0);
               TextureRegion region = new TextureRegion(width, height);
               texture.add(region);
               this.images.add(new TexturePacker.Image(imageBuffer, region, width, height, true));
               if (width > 20) {
                  this.addResized(texture, imageBuffer, width, height, 20);
               }

               if (width > 32) {
                  this.addResized(texture, imageBuffer, width, height, 32);
               }

               if (width > 48) {
                  this.addResized(texture, imageBuffer, width, height, 48);
               }
            } catch (IOException var20) {
               var20.printStackTrace();
            } finally {
               MemoryUtil.memFree(rawImageBuffer);
            }
         } catch (Throwable var22) {
            if (stack != null) {
               try {
                  stack.close();
               } catch (Throwable var19) {
                  var22.addSuppressed(var19);
               }
            }

            throw var22;
         }

         if (stack != null) {
            stack.close();
         }

         return texture;
      } catch (IOException var23) {
         var23.printStackTrace();
         return null;
      }
   }

   private void addResized(GuiTexture texture, ByteBuffer srcImageBuffer, int srcWidth, int srcHeight, int width) {
      double scaleFactor = (double)width / srcWidth;
      int height = (int)(srcHeight * scaleFactor);
      ByteBuffer imageBuffer = BufferUtils.createByteBuffer(width * height * 4);
      STBImageResize.stbir_resize_uint8(srcImageBuffer, srcWidth, srcHeight, 0, imageBuffer, width, height, 0, 4);
      TextureRegion region = new TextureRegion(width, height);
      texture.add(region);
      this.images.add(new TexturePacker.Image(imageBuffer, region, width, height, false));
   }

   public Texture pack() {
      int width = 0;
      int height = 0;
      int rowWidth = 0;
      int rowHeight = 0;

      for (TexturePacker.Image image : this.images) {
         if (rowWidth + image.width > 2048) {
            width = Math.max(width, rowWidth);
            height += rowHeight;
            rowWidth = 0;
            rowHeight = 0;
         }

         image.x = 1 + rowWidth;
         image.y = 1 + height;
         rowWidth += 1 + image.width + 1;
         rowHeight = Math.max(rowHeight, 1 + image.height + 1);
      }

      width = Math.max(width, rowWidth);
      height += rowHeight;
      ByteBuffer buffer = BufferUtils.createByteBuffer(width * height * 4);

      for (TexturePacker.Image image : this.images) {
         byte[] row = new byte[image.width * 4];

         for (int i = 0; i < image.height; i++) {
            ((Buffer)image.buffer).position(i * row.length);
            image.buffer.get(row);
            ((Buffer)buffer).position(((image.y + i) * width + image.x) * 4);
            buffer.put(row);
         }

         ((Buffer)image.buffer).rewind();
         image.free();
         image.region.x1 = (double)image.x / width;
         image.region.y1 = (double)image.y / height;
         image.region.x2 = (double)(image.x + image.width) / width;
         image.region.y2 = (double)(image.y + image.height) / height;
      }

      ((Buffer)buffer).rewind();
      Texture texture = new Texture(width, height, TextureFormat.RGBA8, FilterMode.LINEAR, FilterMode.LINEAR);
      texture.upload(buffer);
      return texture;
   }

   private static class Image {
      public final ByteBuffer buffer;
      public final TextureRegion region;
      public final int width;
      public final int height;
      public int x;
      public int y;
      private final boolean stb;

      public Image(ByteBuffer buffer, TextureRegion region, int width, int height, boolean stb) {
         this.buffer = buffer;
         this.region = region;
         this.width = width;
         this.height = height;
         this.stb = stb;
      }

      public void free() {
         if (this.stb) {
            STBImage.stbi_image_free(this.buffer);
         }
      }
   }
}
