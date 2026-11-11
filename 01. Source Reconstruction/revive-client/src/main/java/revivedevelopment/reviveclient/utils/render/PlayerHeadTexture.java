package revivedevelopment.reviveclient.utils.render;

import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.TextureFormat;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import javax.imageio.ImageIO;
import net.minecraft.resource.Resource;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.renderer.Texture;
import revivedevelopment.reviveclient.utils.network.Http;

public class PlayerHeadTexture extends Texture {
   private boolean needsRotate;

   public PlayerHeadTexture(String url) {
      super(8, 8, TextureFormat.RGBA8, FilterMode.NEAREST, FilterMode.NEAREST);

      BufferedImage skin;
      try {
         skin = ImageIO.read(Http.get(url).sendInputStream());
      } catch (IOException var9) {
         var9.printStackTrace();
         return;
      }

      byte[] head = new byte[256];
      int[] pixel = new int[4];
      int i = 0;

      for (int x = 8; x < 16; x++) {
         for (int y = 8; y < 16; y++) {
            skin.getData().getPixel(x, y, pixel);

            for (int j = 0; j < 4; j++) {
               head[i] = (byte)pixel[j];
               i++;
            }
         }
      }

      i = 0;

      for (int x = 40; x < 48; x++) {
         for (int y = 8; y < 16; y++) {
            skin.getData().getPixel(x, y, pixel);
            if (pixel[3] != 0) {
               for (int j = 0; j < 4; j++) {
                  head[i] = (byte)pixel[j];
                  i++;
               }
            } else {
               i += 4;
            }
         }
      }

      this.upload(head);
      this.needsRotate = true;
   }

   public PlayerHeadTexture() {
      super(8, 8, TextureFormat.RGBA8, FilterMode.NEAREST, FilterMode.NEAREST);

      try (InputStream inputStream = ((Resource)ReviveClient.mc.getResourceManager().getResource(ReviveClient.identifier("textures/steve.png")).get())
            .getInputStream()) {
         ByteBuffer data = TextureUtil.readResource(inputStream);
         data.rewind();
         MemoryStack stack = MemoryStack.stackPush();

         try {
            IntBuffer width = stack.mallocInt(1);
            IntBuffer height = stack.mallocInt(1);
            IntBuffer comp = stack.mallocInt(1);
            ByteBuffer image = STBImage.stbi_load_from_memory(data, width, height, comp, 4);
            this.upload(image);
            STBImage.stbi_image_free(image);
         } catch (Throwable var10) {
            if (stack != null) {
               try {
                  stack.close();
               } catch (Throwable var9) {
                  var10.addSuppressed(var9);
               }
            }

            throw var10;
         }

         if (stack != null) {
            stack.close();
         }

         MemoryUtil.memFree(data);
      } catch (IOException var12) {
         var12.printStackTrace();
      }
   }

   public boolean needsRotate() {
      return this.needsRotate;
   }
}
