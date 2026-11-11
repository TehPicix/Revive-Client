package revivedevelopment.reviveclient.utils.misc;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import net.minecraft.client.util.Window;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWImage;
import org.lwjgl.glfw.GLFWImage.Buffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import revivedevelopment.reviveclient.ReviveClient;

public final class WindowIcon {
   private static final int[] SIZES = new int[]{16, 32, 48, 128, 256};

   private WindowIcon() {
   }

   public static void apply(Window window) {
      if (window != null) {
         List<ByteBuffer> pixelBuffers = new ArrayList<>();

         try {
            MemoryStack stack = MemoryStack.stackPush();

            label210: {
               label211: {
                  try {
                     if (GLFW.glfwGetPlatform() != 393218) {
                        List<BufferedImage> images = new ArrayList<>();

                        for (int size : SIZES) {
                           BufferedImage image = read(size);
                           if (image != null) {
                              images.add(image);
                           }
                        }

                        if (images.isEmpty()) {
                           break label210;
                        }

                        Buffer icons = GLFWImage.malloc(images.size(), stack);

                        for (int i = 0; i < images.size(); i++) {
                           BufferedImage image = images.get(i);
                           int width = image.getWidth();
                           int height = image.getHeight();
                           ByteBuffer pixels = MemoryUtil.memAlloc(width * height * 4);
                           pixelBuffers.add(pixels);

                           for (int y = 0; y < height; y++) {
                              for (int x = 0; x < width; x++) {
                                 int argb = image.getRGB(x, y);
                                 pixels.put((byte)(argb >> 16 & 0xFF));
                                 pixels.put((byte)(argb >> 8 & 0xFF));
                                 pixels.put((byte)(argb & 0xFF));
                                 pixels.put((byte)(argb >> 24 & 0xFF));
                              }
                           }

                           pixels.flip();
                           icons.position(i);
                           icons.width(width);
                           icons.height(height);
                           icons.pixels(pixels);
                        }

                        GLFW.glfwSetWindowIcon(window.getHandle(), (Buffer)icons.position(0));
                        break label211;
                     }
                  } catch (Throwable var19) {
                     if (stack != null) {
                        try {
                           stack.close();
                        } catch (Throwable var18) {
                           var19.addSuppressed(var18);
                        }
                     }

                     throw var19;
                  }

                  if (stack != null) {
                     stack.close();
                  }

                  return;
               }

               if (stack != null) {
                  stack.close();
               }

               return;
            }

            if (stack != null) {
               stack.close();
            }
         } catch (Throwable var20) {
            ReviveClient.LOG.warn("Could not set the window icon", var20);
            return;
         } finally {
            pixelBuffers.forEach(MemoryUtil::memFree);
         }
      }
   }

   private static BufferedImage read(int size) {
      String path = "/assets/revive-client/icons/icon_" + size + "x" + size + ".png";

      try {
         BufferedImage converted;
         try (InputStream in = WindowIcon.class.getResourceAsStream(path)) {
            if (in == null) {
               return null;
            }

            BufferedImage image = ImageIO.read(in);
            if (image == null) {
               return null;
            }

            if (image.getType() != 2) {
               converted = new BufferedImage(image.getWidth(), image.getHeight(), 2);
               converted.getGraphics().drawImage(image, 0, 0, null);
               image = converted;
            }

            converted = image;
         }

         return converted;
      } catch (Exception var7) {
         ReviveClient.LOG.warn("Could not read window icon {}", path, var7);
         return null;
      }
   }
}
