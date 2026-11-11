package revivedevelopment.reviveclient.utils.render;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.io.File;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import net.minecraft.util.Util;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBTTFontinfo;
import org.lwjgl.stb.STBTruetype;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.renderer.Fonts;
import revivedevelopment.reviveclient.renderer.text.BuiltinFontFace;
import revivedevelopment.reviveclient.renderer.text.FontFace;
import revivedevelopment.reviveclient.renderer.text.FontFamily;
import revivedevelopment.reviveclient.renderer.text.FontInfo;
import revivedevelopment.reviveclient.renderer.text.SystemFontFace;
import revivedevelopment.reviveclient.utils.files.ByteBufferUtils;

@NullMarked
public final class FontUtils {
   private FontUtils() {
   }

   @Nullable
   public static FontInfo getSysFontInfo(File file) {
      return getFontInfo(file);
   }

   @Nullable
   public static FontInfo getBuiltinFontInfo(String builtin) {
      return getFontInfo(builtinFontStream(builtin));
   }

   @Nullable
   private static FontInfo getFontInfo(@Nullable File file) {
      if (file != null && file.isFile()) {
         try {
            return getFontInfo(ByteBufferUtils.readFully(file.toPath(), BufferUtils::createByteBuffer));
         } catch (Exception var2) {
            ReviveClient.LOG.warn("Failed to read font file: {}", file, var2);
            return null;
         }
      } else {
         return null;
      }
   }

   @Nullable
   public static FontInfo getFontInfo(@Nullable InputStream stream) {
      if (stream == null) {
         return null;
      } else {
         try {
            FontInfo var3;
            try (ReadableByteChannel ch = Channels.newChannel(stream)) {
               ByteBuffer buf = ByteBufferUtils.readFully(ch, BufferUtils::createByteBuffer);
               var3 = getFontInfo(buf);
            }

            return var3;
         } catch (Exception var6) {
            ReviveClient.LOG.warn("Failed to read font stream.", var6);
            return null;
         }
      }
   }

   @Nullable
   private static FontInfo getFontInfo(ByteBuffer buffer) {
      if (buffer.remaining() < 5) {
         return null;
      } else if (buffer.get(0) == 0 && buffer.get(1) == 1 && buffer.get(2) == 0 && buffer.get(3) == 0 && buffer.get(4) == 0) {
         STBTTFontinfo fontInfo = STBTTFontinfo.create();
         if (!STBTruetype.stbtt_InitFont(fontInfo, buffer)) {
            return null;
         } else {
            ByteBuffer nameBuffer = STBTruetype.stbtt_GetFontNameString(fontInfo, 3, 1, 1033, 1);
            ByteBuffer typeBuffer = STBTruetype.stbtt_GetFontNameString(fontInfo, 3, 1, 1033, 2);
            return typeBuffer != null && nameBuffer != null
               ? new FontInfo(
                  StandardCharsets.UTF_16.decode(nameBuffer).toString(), FontInfo.Type.fromString(StandardCharsets.UTF_16.decode(typeBuffer).toString())
               )
               : null;
         }
      } else {
         return null;
      }
   }

   public static Set<String> getSearchPaths() {
      Set<String> paths = new ObjectOpenHashSet();
      paths.add(System.getProperty("java.home") + "/lib/fonts");

      for (File dir : getUFontDirs()) {
         if (dir.exists()) {
            paths.add(dir.getAbsolutePath());
         }
      }

      for (File dirx : getSFontDirs()) {
         if (dirx.exists()) {
            paths.add(dirx.getAbsolutePath());
         }
      }

      return paths;
   }

   public static List<File> getUFontDirs() {
      return switch (Util.getOperatingSystem()) {
         case WINDOWS -> List.of(new File(System.getProperty("user.home") + "\\AppData\\Local\\Microsoft\\Windows\\Fonts"));
         case OSX -> List.of(new File(System.getProperty("user.home") + "/Library/Fonts/"));
         default -> List.of(new File(System.getProperty("user.home") + "/.local/share/fonts"), new File(System.getProperty("user.home") + "/.fonts"));
      };
   }

   public static List<File> getSFontDirs() {
      return switch (Util.getOperatingSystem()) {
         case WINDOWS -> List.of(new File(System.getenv("SystemRoot") + "\\Fonts"));
         case OSX -> List.of(new File("/System/Library/Fonts/"));
         default -> List.of(new File("/usr/share/fonts/"));
      };
   }

   public static void loadBuiltin(List<FontFamily> fontList, String builtin) {
      FontInfo fontInfo = getBuiltinFontInfo(builtin);
      if (fontInfo != null) {
         FontFace fontFace = new BuiltinFontFace(fontInfo, builtin);
         if (!addFont(fontList, fontFace)) {
            ReviveClient.LOG.warn("Failed to load builtin font {}", fontFace);
         }
      }
   }

   public static void loadSystem(List<FontFamily> fontList, File dir) {
      if (dir.exists() && dir.isDirectory()) {
         File[] files = dir.listFiles(filex -> filex.isFile() && filex.getName().endsWith(".ttf") || filex.isDirectory());
         if (files != null) {
            for (File file : files) {
               if (file.isDirectory()) {
                  loadSystem(fontList, file);
               } else {
                  FontInfo fontInfo = getSysFontInfo(file);
                  if (fontInfo != null) {
                     boolean isBuiltin = false;

                     for (String builtinFont : Fonts.BUILTIN_FONTS) {
                        if (builtinFont.equals(fontInfo.family())) {
                           isBuiltin = true;
                           break;
                        }
                     }

                     if (!isBuiltin) {
                        FontFace fontFace = new SystemFontFace(fontInfo, file.toPath());
                        if (!addFont(fontList, fontFace)) {
                           ReviveClient.LOG.warn("Failed to load system font {}", fontFace);
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private static boolean addFont(List<FontFamily> fontList, @Nullable FontFace font) {
      if (font == null) {
         return false;
      } else {
         FontInfo info = font.info;
         FontFamily family = Fonts.getFamily(info.family());
         if (family == null) {
            family = new FontFamily(info.family());
            fontList.add(family);
         }

         return family.hasType(info.type()) ? false : family.addFont(font);
      }
   }

   @Nullable
   public static InputStream builtinFontStream(String name) {
      return FontUtils.class.getResourceAsStream("/assets/revive-client/fonts/" + name + ".ttf");
   }
}
