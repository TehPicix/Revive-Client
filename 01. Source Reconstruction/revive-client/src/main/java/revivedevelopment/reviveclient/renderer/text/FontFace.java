package revivedevelopment.reviveclient.renderer.text;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import org.jspecify.annotations.NullMarked;
import org.lwjgl.BufferUtils;
import revivedevelopment.reviveclient.utils.files.ByteBufferUtils;

@NullMarked
public abstract sealed class FontFace permits BuiltinFontFace, SystemFontFace {
   public final FontInfo info;

   protected FontFace(FontInfo info) {
      this.info = info;
   }

   public abstract ReadableByteChannel byteChannelForRead() throws IOException;

   public final ByteBuffer readToDirectByteBuffer() throws IOException {
      ByteBuffer var2;
      try (ReadableByteChannel channel = this.byteChannelForRead()) {
         var2 = ByteBufferUtils.readFully(channel, BufferUtils::createByteBuffer);
      }

      return var2;
   }

   @Override
   public String toString() {
      return this.info.toString();
   }
}
