package revivedevelopment.reviveclient.mixin;

import it.unimi.dsi.fastutil.io.FastByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.List;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.BookEditScreen;
import net.minecraft.client.gui.widget.ButtonWidget.Builder;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.nbt.NbtString;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import revivedevelopment.reviveclient.ReviveClient;

@Mixin({BookEditScreen.class})
public abstract class BookEditScreenMixin extends Screen {
   @Shadow
   @Final
   private List<String> field_17116;
   @Shadow
   private int field_2840;

   public BookEditScreenMixin(Text title) {
      super(title);
   }

   @Shadow
   protected abstract void method_71537();

   @Inject(
      method = {"method_25426"},
      at = {@At("TAIL")}
   )
   private void onInit(CallbackInfo info) {
      this.addDrawableChild(new Builder(Text.literal("Copy"), button -> {
         NbtList listTag = new NbtList();
         this.field_17116.stream().map(NbtString::of).forEach(listTag::add);
         NbtCompound tag = new NbtCompound();
         tag.put("pages", listTag);
         tag.putInt("currentPage", this.field_2840);
         FastByteArrayOutputStream bytes = new FastByteArrayOutputStream();
         DataOutputStream out = new DataOutputStream(bytes);

         try {
            NbtIo.write(tag, out);
         } catch (IOException var8) {
            var8.printStackTrace();
         }

         try {
            GLFW.glfwSetClipboardString(ReviveClient.mc.getWindow().getHandle(), Base64.getEncoder().encodeToString(bytes.array));
         } catch (OutOfMemoryError var7) {
            GLFW.glfwSetClipboardString(ReviveClient.mc.getWindow().getHandle(), var7.toString());
         }
      }).position(4, 4).size(120, 20).build());
      this.addDrawableChild(new Builder(Text.literal("Paste"), button -> {
         String clipboard = GLFW.glfwGetClipboardString(ReviveClient.mc.getWindow().getHandle());
         if (clipboard != null) {
            byte[] bytes;
            try {
               bytes = Base64.getDecoder().decode(clipboard);
            } catch (IllegalArgumentException var8) {
               return;
            }

            DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));

            try {
               NbtCompound tag = NbtIo.readCompressed(in, NbtSizeTracker.ofUnlimitedBytes());
               NbtList listTag = tag.getListOrEmpty("pages").copy();
               this.field_17116.clear();

               for (int i = 0; i < listTag.size(); i++) {
                  this.field_17116.add(listTag.getString(i, ""));
               }

               if (this.field_17116.isEmpty()) {
                  this.field_17116.add("");
               }

               this.field_2840 = tag.getInt("currentPage", 0);
               this.method_71537();
            } catch (IOException var9) {
               var9.printStackTrace();
            }
         }
      }).position(4, 26).size(120, 20).build());
   }
}
