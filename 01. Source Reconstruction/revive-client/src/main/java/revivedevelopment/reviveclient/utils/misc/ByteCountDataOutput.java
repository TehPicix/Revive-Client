package revivedevelopment.reviveclient.utils.misc;

import java.io.DataOutput;
import java.io.IOException;
import org.jetbrains.annotations.NotNull;

public class ByteCountDataOutput implements DataOutput {
   public static final ByteCountDataOutput INSTANCE = new ByteCountDataOutput();
   private int count;

   public int getCount() {
      return this.count;
   }

   public void reset() {
      this.count = 0;
   }

   @Override
   public void write(int b) throws IOException {
      this.count++;
   }

   @Override
   public void write(byte[] b) throws IOException {
      this.count += b.length;
   }

   @Override
   public void write(@NotNull byte[] b, int off, int len) throws IOException {
      this.count += len;
   }

   @Override
   public void writeBoolean(boolean v) {
      this.count++;
   }

   @Override
   public void writeByte(int v) {
      this.count++;
   }

   @Override
   public void writeShort(int v) {
      this.count += 2;
   }

   @Override
   public void writeChar(int v) {
      this.count += 2;
   }

   @Override
   public void writeInt(int v) {
      this.count += 4;
   }

   @Override
   public void writeLong(long v) {
      this.count += 8;
   }

   @Override
   public void writeFloat(float v) {
      this.count += 4;
   }

   @Override
   public void writeDouble(double v) {
      this.count += 8;
   }

   @Override
   public void writeBytes(String s) {
      this.count = this.count + s.length();
   }

   @Override
   public void writeChars(String s) {
      this.count = this.count + s.length() * 2;
   }

   @Override
   public void writeUTF(@NotNull String s) {
      this.count = (int)(this.count + 2L + this.getUTFLength(s));
   }

   long getUTFLength(String s) {
      long utflen = 0L;

      for (int cpos = 0; cpos < s.length(); cpos++) {
         char c = s.charAt(cpos);
         if (c >= 1 && c <= 127) {
            utflen++;
         } else if (c > 2047) {
            utflen += 3L;
         } else {
            utflen += 2L;
         }
      }

      return utflen;
   }
}
