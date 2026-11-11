package revivedevelopment.reviveclient.util.animation;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.util.audio.TitleAudioAnalyzer;

public class Ascii {
   private final List<Ascii.ColorChar[][]> frames = new ArrayList<>();
   private float hairSpectrum = 0.0F;
   private final List<BufferedImage> rawFrames = new ArrayList<>();
   private int currentFrame = 0;
   private int frameCount = 0;
   private boolean isLoaded = false;
   private String errorMessage = null;
   private long lastFrameTime = 0L;
   private final long frameDelay;
   private int x;
   private int y;
   private int width;
   private int height;
   private float textScale = 0.43F;
   private float animationTime = 0.0F;
   private final Random random = new Random();
   private boolean useOriginalColors = true;
   private String lastLoadedResource;
   private static final char[] BRIGHT_CHARS = new char[]{'@', '#', '&', '$', 'X', 'L'};
   private static final char[] MEDIUM_CHARS = new char[]{'S', '8', 'O', 'o', '*', '+'};
   private static final char[] DARK_CHARS = new char[]{':', ';', ',', '.', '-', '='};
   private static final char[] SKIN_CHARS = new char[]{'@', '#', '$', '&', '%', 'O'};
   private static final char[] GRADIENT = new char[]{'@', 'M', '#', 'B', '&', '$', '%', 'X', 'W', '*', 'o', 'a', 'c', '+', '=', ';', ':', '~', '-', '.', ','};
   private static final char[] SKIN_STROKES = new char[]{'L', 'l', 'I', 'i', '|', '!', ':', ';', '.'};
   private static final char[] HAIR_STROKES = new char[]{'N', '#', '/', '|', '\\', 'l', 'I', '!', ':', '.'};

   public Ascii(int screenWidth, int screenHeight, String resourcePath, int frameDelayMs) {
      this.x = screenWidth - 480;
      this.y = screenHeight / 2 - 200;
      this.frameDelay = frameDelayMs;
      this.lastLoadedResource = resourcePath;
      this.loadGifFromResource(resourcePath);
   }

   private void loadGifFromResource(String resourcePath) {
      this.lastLoadedResource = resourcePath;
      ReviveClient.LOG.info("[TitleScreen] Loading GIF: {}", resourcePath);

      try {
         Identifier id = Identifier.of("revive-client", resourcePath);
         Optional<Resource> resourceOptional = MinecraftClient.getInstance().getResourceManager().getResource(id);
         if (resourceOptional.isPresent()) {
            ReviveClient.LOG.debug("[TitleScreen] GIF resource stream found: {}", resourcePath);
            Resource resource = resourceOptional.get();
            InputStream inputStream = resource.getInputStream();
            Iterator<ImageReader> gifReaders = ImageIO.getImageReadersByFormatName("gif");
            if (!gifReaders.hasNext()) {
               ReviveClient.LOG.error("[TitleScreen] No GIF ImageReader registered, cannot load: {}", resourcePath);
               this.errorMessage = "No GIF reader available";
               return;
            }

            ImageReader reader = gifReaders.next();
            ImageInputStream imageInputStream = ImageIO.createImageInputStream(inputStream);
            reader.setInput(imageInputStream);
            int numFrames = reader.getNumImages(true);
            this.width = 104;
            this.height = 112;
            this.frames.clear();
            this.rawFrames.clear();

            for (int i = 0; i < numFrames; i++) {
               BufferedImage frame = reader.read(i);
               this.rawFrames.add(frame);
               this.frames.add(this.convertToAscii(frame));
            }

            imageInputStream.close();
            inputStream.close();
            reader.dispose();
            ReviveClient.LOG.info("[TitleScreen] GIF loaded: {} frame(s) from {}", this.frames.size(), resourcePath);
            if (this.frames.isEmpty() && System.getProperty("os.name").toLowerCase().contains("linux")) {
               ReviveClient.LOG.warn("[TitleScreen] GIF reader returned 0 frames on Linux, trying ImageIO.read() fallback");

               try {
                  InputStream fallbackStream = resource.getInputStream();
                  BufferedImage fallbackFrame = ImageIO.read(fallbackStream);
                  fallbackStream.close();
                  if (fallbackFrame != null) {
                     this.rawFrames.add(fallbackFrame);
                     this.frames.add(this.convertToAscii(fallbackFrame));
                     ReviveClient.LOG.info("[TitleScreen] Linux fallback loaded {} frame(s)", this.frames.size());
                  } else {
                     ReviveClient.LOG.error("[TitleScreen] Linux fallback ImageIO.read() returned null");
                  }
               } catch (Exception var12) {
                  ReviveClient.LOG.error("[TitleScreen] Linux fallback failed: {}", var12.getMessage());
               }
            }

            this.frameCount = this.frames.size();
            this.isLoaded = this.frameCount > 0;
         } else {
            ReviveClient.LOG.warn("[TitleScreen] GIF resource not found: {}", resourcePath);
            this.errorMessage = "Resource not found: " + resourcePath;
         }
      } catch (Exception var13) {
         this.errorMessage = "Error loading GIF: " + var13.getMessage();
         ReviveClient.LOG.error("[TitleScreen] Failed to load GIF: {}", resourcePath, var13);
      }
   }

   private Ascii.ColorChar[][] convertToAscii(BufferedImage image) {
      int srcW = image.getWidth();
      int srcH = image.getHeight();
      int[] pixels = new int[this.width * this.height];

      for (int py = 0; py < this.height; py++) {
         for (int px = 0; px < this.width; px++) {
            float srcX = px * (srcW - 1.0F) / Math.max(1, this.width - 1);
            float srcY = py * (srcH - 1.0F) / Math.max(1, this.height - 1);
            int x0 = (int)srcX;
            int y0 = (int)srcY;
            int x1 = Math.min(x0 + 1, srcW - 1);
            int y1 = Math.min(y0 + 1, srcH - 1);
            float fx = srcX - x0;
            float fy = srcY - y0;
            int p00 = image.getRGB(x0, y0);
            int p10 = image.getRGB(x1, y0);
            int p01 = image.getRGB(x0, y1);
            int p11 = image.getRGB(x1, y1);
            int a = this.blendChannel(p00 >> 24 & 0xFF, p10 >> 24 & 0xFF, p01 >> 24 & 0xFF, p11 >> 24 & 0xFF, fx, fy);
            int r = this.blendChannel(p00 >> 16 & 0xFF, p10 >> 16 & 0xFF, p01 >> 16 & 0xFF, p11 >> 16 & 0xFF, fx, fy);
            int g = this.blendChannel(p00 >> 8 & 0xFF, p10 >> 8 & 0xFF, p01 >> 8 & 0xFF, p11 >> 8 & 0xFF, fx, fy);
            int b = this.blendChannel(p00 & 0xFF, p10 & 0xFF, p01 & 0xFF, p11 & 0xFF, fx, fy);
            pixels[py * this.width + px] = a << 24 | r << 16 | g << 8 | b;
         }
      }

      BufferedImage resized = new BufferedImage(this.width, this.height, 2);
      resized.setRGB(0, 0, this.width, this.height, pixels, 0, this.width);
      BufferedImage enhanced = this.enhanceImage(resized);
      boolean[][] isFaceRegion = new boolean[this.height][this.width];
      boolean[][] isSkinRegion = new boolean[this.height][this.width];
      int faceTop = this.height;
      int faceBottom = 0;
      int faceLeft = this.width;
      int faceRight = 0;

      for (int y = 0; y < this.height; y++) {
         for (int x = 0; x < this.width; x++) {
            int pixel = enhanced.getRGB(x, y);
            int alpha = pixel >> 24 & 0xFF;
            if (alpha >= 50) {
               int red = pixel >> 16 & 0xFF;
               int green = pixel >> 8 & 0xFF;
               int blue = pixel & 0xFF;
               boolean isSkin = this.isSkin(red, green, blue);
               if (isSkin) {
                  isSkinRegion[y][x] = true;
                  boolean possiblyFace = x > this.width * 0.25 && x < this.width * 0.75 && y > this.height * 0.1 && y < this.height * 0.5;
                  if (possiblyFace) {
                     isFaceRegion[y][x] = true;
                     faceTop = Math.min(faceTop, y);
                     faceBottom = Math.max(faceBottom, y);
                     faceLeft = Math.min(faceLeft, x);
                     faceRight = Math.max(faceRight, x);
                  }
               }
            }
         }
      }

      faceTop = Math.max(0, faceTop - 5);
      faceBottom = Math.min(this.height - 1, faceBottom + 5);
      faceLeft = Math.max(0, faceLeft - 5);
      faceRight = Math.min(this.width - 1, faceRight + 5);
      Ascii.ColorChar[][] result = new Ascii.ColorChar[this.height][this.width];

      for (int y = 0; y < this.height; y++) {
         for (int xx = 0; xx < this.width; xx++) {
            int pixel = enhanced.getRGB(xx, y);
            int alpha = pixel >> 24 & 0xFF;
            boolean isInFaceRegion = y >= faceTop && y <= faceBottom && xx >= faceLeft && xx <= faceRight;
            if (alpha < 30) {
               result[y][xx] = new Ascii.ColorChar(' ', 0);
            } else {
               int red = pixel >> 16 & 0xFF;
               int green = pixel >> 8 & 0xFF;
               int blue = pixel & 0xFF;
               if (red == 0 && green == 0 && blue == 0) {
                  result[y][xx] = new Ascii.ColorChar(' ', 0);
               } else {
                  double brightness = 0.299 * red + 0.587 * green + 0.114 * blue;
                  boolean isPurple = this.isPurple(red, green, blue);
                  boolean isSkin = this.isSkin(red, green, blue) || isSkinRegion[y][xx] || isInFaceRegion && brightness > 30.0;
                  char asciiChar;
                  if (isPurple) {
                     asciiChar = this.strokeChar(HAIR_STROKES, brightness);
                     red = Math.min(255, (int)(red * 1.3));
                     green = Math.min(255, (int)(green * 0.8));
                     blue = Math.min(255, (int)(blue * 1.6));
                  } else if (isSkin) {
                     asciiChar = this.strokeChar(SKIN_STROKES, brightness);
                     if (isInFaceRegion) {
                        int facialBoost = 50;
                        red = Math.min(255, red + facialBoost);
                        green = Math.min(255, green + (facialBoost - 15));
                        blue = Math.min(255, blue + (facialBoost - 20));
                     } else {
                        int boost = 35;
                        red = Math.min(255, red + boost);
                        green = Math.min(255, green + (boost - 10));
                        blue = Math.min(255, blue + (boost - 15));
                     }
                  } else {
                     asciiChar = this.rampChar(brightness);
                     if (brightness <= 50.0) {
                        red = Math.min(255, red + 40);
                        green = Math.min(255, green + 30);
                        blue = Math.min(255, blue + 50);
                     }
                  }

                  int finalColor;
                  if (this.useOriginalColors) {
                     finalColor = 0xFF000000 | red << 16 | green << 8 | blue;
                  } else {
                     Color themeColor = new Color(145, 61, 226);
                     float brightnessFactor = (float)(brightness / 255.0);
                     int themeRed = Math.min(255, (int)(themeColor.getRed() * brightnessFactor * 1.2));
                     int themeGreen = Math.min(255, (int)(themeColor.getGreen() * brightnessFactor * 1.2));
                     int themeBlue = Math.min(255, (int)(themeColor.getBlue() * brightnessFactor * 1.2));
                     finalColor = 0xFF000000 | themeRed << 16 | themeGreen << 8 | themeBlue;
                  }

                  Ascii.ColorChar cc = new Ascii.ColorChar(asciiChar, finalColor);
                  cc.isHair = isPurple;
                  result[y][xx] = cc;
               }
            }
         }
      }

      return result;
   }

   private BufferedImage enhanceImage(BufferedImage original) {
      BufferedImage enhanced = new BufferedImage(original.getWidth(), original.getHeight(), 2);
      float contrastFactor = 1.25F;
      float brightnessFactor = 1.12F;
      float skinBrightnessFactor = 1.18F;
      float purpleBrightnessFactor = 1.3F;

      for (int y = 0; y < original.getHeight(); y++) {
         for (int x = 0; x < original.getWidth(); x++) {
            int rgb = original.getRGB(x, y);
            int alpha = rgb >> 24 & 0xFF;
            if (alpha < 15) {
               enhanced.setRGB(x, y, rgb);
            } else {
               int red = rgb >> 16 & 0xFF;
               int green = rgb >> 8 & 0xFF;
               int blue = rgb & 0xFF;
               boolean isSkinTone = this.isSkin(red, green, blue);
               boolean isPurple = this.isPurple(red, green, blue);
               if (isSkinTone) {
                  red = Math.min(255, Math.max(0, (int)(red * 1.18F)));
                  green = Math.min(255, Math.max(0, (int)(green * 1.18F)));
                  blue = Math.min(255, Math.max(0, (int)(blue * 1.18F)));
               } else if (isPurple) {
                  red = Math.min(255, Math.max(0, (int)(red * 1.3F)));
                  green = Math.min(255, Math.max(0, (int)(green * 0.9F)));
                  blue = Math.min(255, Math.max(0, (int)(blue * 1.3F)));
               } else {
                  red = Math.min(255, Math.max(0, (int)(red * 1.12F)));
                  green = Math.min(255, Math.max(0, (int)(green * 1.12F)));
                  blue = Math.min(255, Math.max(0, (int)(blue * 1.12F)));
               }

               if (isSkinTone) {
                  red = this.applyContrast(red, 0.75F);
                  green = this.applyContrast(green, 0.75F);
                  blue = this.applyContrast(blue, 0.75F);
               } else if (isPurple) {
                  red = this.applyContrast(red, 1.5F);
                  green = this.applyContrast(green, 1.0F);
                  blue = this.applyContrast(blue, 1.5F);
               } else {
                  red = this.applyContrast(red, 1.25F);
                  green = this.applyContrast(green, 1.25F);
                  blue = this.applyContrast(blue, 1.25F);
               }

               if (isSkinTone) {
                  red = Math.min(255, red + 22);
                  green = Math.min(255, green + 18);
                  blue = Math.min(255, blue + 16);
                  alpha = Math.max(alpha, 240);
               }

               if (red > 230 && green > 230 && blue > 230) {
                  alpha = 255;
               }

               if (red < 40 && green < 40 && blue < 40 && (red > 0 || green > 0 || blue > 0)) {
                  red = Math.max(45, red);
                  green = Math.max(45, green);
                  blue = Math.max(45, blue);
                  alpha = Math.max(alpha, 200);
               }

               int enhancedPixel = alpha << 24 | red << 16 | green << 8 | blue;
               enhanced.setRGB(x, y, enhancedPixel);
            }
         }
      }

      return enhanced;
   }

   private int applyContrast(int value, float factor) {
      float result = factor * (value - 128) + 128.0F;
      return Math.min(255, Math.max(0, (int)result));
   }

   private char rampChar(double brightness) {
      int idx = (int)Math.round((255.0 - brightness) / 255.0 * (GRADIENT.length - 1));
      return GRADIENT[Math.max(0, Math.min(GRADIENT.length - 1, idx))];
   }

   private char strokeChar(char[] palette, double brightness) {
      int idx = (int)Math.round((255.0 - brightness) / 255.0 * (palette.length - 1));
      return palette[Math.max(0, Math.min(palette.length - 1, idx))];
   }

   private int blendChannel(int c00, int c10, int c01, int c11, float fx, float fy) {
      float top = c00 + (c10 - c00) * fx;
      float bot = c01 + (c11 - c01) * fx;
      return Math.min(255, Math.max(0, Math.round(top + (bot - top) * fy)));
   }

   private boolean isPurple(int r, int g, int b) {
      boolean redBlueBalance = r > g + 30 && b > g + 30;
      boolean purpleHue = r >= 80 && r <= 200 && g <= 100 && b >= 100;
      boolean darkPurple = r < 100 && g < 80 && b > 100;
      boolean lightPurple = r > 100 && g < 100 && b > 150;
      return redBlueBalance || purpleHue || darkPurple || lightPurple;
   }

   private boolean isSkin(int r, int g, int b) {
      boolean paleSkinRatio = r > g - 5 && g > b - 5;
      boolean veryPaleSkin = r > 180 && g > 170 && b > 160;
      boolean paleSkin = r > 160 && g > 140 && b > 120;
      boolean lightSkin = r > 140 && g > 120 && b > 100;
      boolean almostWhiteSkin = r > 220 && g > 220 && b > 210;
      boolean lightGraySkin = Math.abs(r - g) < 15 && Math.abs(g - b) < 15 && Math.abs(r - b) < 15 && r > 160;
      return paleSkinRatio && (veryPaleSkin || paleSkin || lightSkin || almostWhiteSkin) || lightGraySkin;
   }

   public void updatePosition(int screenWidth, int screenHeight) {
      this.x = screenWidth - 300;
      this.y = screenHeight / 2 - 111;
   }

   public void reloadFrames() {
      if (this.lastLoadedResource != null) {
         this.frames.clear();
         this.rawFrames.clear();
         this.loadGifFromResource(this.lastLoadedResource);
      }
   }

   public void render(DrawContext context, int mouseX, int mouseY, float delta, float bassLevel, float trebleLevel) {
      this.animationTime += delta * 0.01F;
      long currentTime = System.currentTimeMillis();
      if (currentTime - this.lastFrameTime > this.frameDelay && this.frameCount > 0) {
         this.currentFrame = (this.currentFrame + 1) % this.frameCount;
         this.lastFrameTime = currentTime;
      }

      if (this.errorMessage != null) {
         context.drawText(MinecraftClient.getInstance().textRenderer, this.errorMessage, this.x, this.y, Color.RED.hashCode(), false);
      } else if (!this.frames.isEmpty() && this.currentFrame < this.frames.size()) {
         Ascii.ColorChar[][] currentFrameData = this.frames.get(this.currentFrame);
         float vol = Math.max(0.0F, Math.min(1.0F, TitleAudioAnalyzer.getInstance().getVolumeLevel()));
         this.hairSpectrum = this.hairSpectrum + (vol - this.hairSpectrum) * 0.3F;
         context.getMatrices().pushMatrix();
         context.getMatrices().scale(this.textScale, this.textScale);
         float scaledX = this.x / this.textScale;
         float scaledY = this.y / this.textScale;
         int charWidth = MinecraftClient.getInstance().textRenderer.getWidth("X");
         int charHeight = 8;

         for (int i = 0; i < currentFrameData.length; i++) {
            for (int j = 0; j < currentFrameData[i].length; j++) {
               Ascii.ColorChar colorChar = currentFrameData[i][j];
               if (colorChar.character != ' ') {
                  float posX = scaledX + j * charWidth;
                  float posY = scaledY + i * charHeight;
                  int drawColor = colorChar.isHair ? this.hairColor(colorChar.colorValue, this.hairSpectrum) : colorChar.colorValue;
                  context.drawText(MinecraftClient.getInstance().textRenderer, String.valueOf(colorChar.character), (int)posX, (int)posY, drawColor, false);
               }
            }
         }

         context.getMatrices().popMatrix();
      }
   }

   public void render(DrawContext context, int mouseX, int mouseY, float delta) {
      this.render(context, mouseX, mouseY, delta, 0.0F, 0.0F);
   }

   private int hairColor(int baseColor, float t) {
      int br = (baseColor >> 16 & 0xFF) + (baseColor >> 8 & 0xFF) + (baseColor & 0xFF);
      float lum = 0.45F + 0.55F * (br / 765.0F);
      float shade = lum * (0.7F + 0.45F * t);
      int tr = (int)(130.0F + 125.0F * t);
      int tg = (int)(35.0F + 60.0F * t);
      int tb = (int)(225.0F + -60.0F * t);
      int r = Math.min(255, (int)(tr * shade));
      int g = Math.min(255, (int)(tg * shade));
      int b = Math.min(255, (int)(tb * shade));
      return 0xFF000000 | r << 16 | g << 8 | b;
   }

   public void toggleColorMode() {
      this.useOriginalColors = !this.useOriginalColors;
      this.reloadFrames();
   }

   private static class ColorChar {
      char character;
      int colorValue;
      boolean isHair;

      ColorChar(char character, int colorValue) {
         this.character = character;
         this.colorValue = colorValue;
      }
   }
}
