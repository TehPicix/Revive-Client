package revivedevelopment.reviveclient.renderer;

import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.TextureFormat;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImage.Format;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

public class Texture extends AbstractTexture {
   public Texture(int width, int height, TextureFormat format, FilterMode min, FilterMode mag) {
      this.glTexture = RenderSystem.getDevice().createTexture("", 15, format, width, height, 1, 1);
      this.sampler = RenderSystem.getSamplerCache().get(AddressMode.REPEAT, AddressMode.REPEAT, min, mag, false);
      this.glTextureView = RenderSystem.getDevice().createTextureView(this.glTexture);
   }

   public int getWidth() {
      return this.getGlTexture().getWidth(0);
   }

   public int getHeight() {
      return this.getGlTexture().getHeight(0);
   }

   public void upload(byte[] bytes) {
      this.upload(BufferUtils.createByteBuffer(bytes.length).put(bytes));
   }

   public void upload(ByteBuffer buffer) {
      NativeImage image = this.getImage();
      buffer.rewind();
      MemoryUtil.memCopy(MemoryUtil.memAddress(buffer), image.imageId(), buffer.remaining());
      RenderSystem.getDevice().createCommandEncoder().writeToTexture(this.glTexture, image);
      image.close();
   }

   @NotNull
   private NativeImage getImage() {
      Format imageFormat = switch (this.glTexture.getFormat()) {
         case RGBA8 -> Format.RGBA;
         case RED8 -> Format.LUMINANCE;
         default -> throw new IllegalArgumentException();
      };
      return new NativeImage(imageFormat, this.getWidth(), this.getHeight(), false);
   }

   public static Texture readResource(String path, boolean flipY, FilterMode filter) {
      try {
         Texture var11;
         try (InputStream in = Texture.class.getResourceAsStream(path)) {
            if (in == null) {
               return null;
            }

            ByteBuffer data = TextureUtil.readResource(in).rewind();

            try {
               MemoryStack stack = MemoryStack.stackPush();

               try {
                  IntBuffer width = stack.mallocInt(1);
                  IntBuffer height = stack.mallocInt(1);
                  IntBuffer comp = stack.mallocInt(1);
                  STBImage.stbi_set_flip_vertically_on_load(flipY);
                  ByteBuffer image = STBImage.stbi_load_from_memory(data, width, height, comp, 4);
                  Texture texture = new Texture(width.get(0), height.get(0), TextureFormat.RGBA8, filter, filter);
                  texture.upload(image);
                  STBImage.stbi_image_free(image);
                  STBImage.stbi_set_flip_vertically_on_load(false);
                  var11 = texture;
               } catch (Throwable var21) {
                  if (stack != null) {
                     try {
                        stack.close();
                     } catch (Throwable var20) {
                        var21.addSuppressed(var20);
                     }
                  }

                  throw var21;
               }

               if (stack != null) {
                  stack.close();
               }
            } finally {
               MemoryUtil.memFree(data);
            }
         }

         return var11;
      } catch (IOException var24) {
         return null;
      }
   }
}
