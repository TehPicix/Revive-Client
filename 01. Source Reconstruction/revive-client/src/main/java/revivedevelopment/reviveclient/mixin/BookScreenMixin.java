package revivedevelopment.reviveclient.mixin;

import it.unimi.dsi.fastutil.io.FastByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Base64;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.BookScreen;
import net.minecraft.client.gui.screen.ingame.BookScreen.Contents;
import net.minecraft.client.gui.widget.ButtonWidget.Builder;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.gui.GuiThemes;
import revivedevelopment.reviveclient.gui.screens.EditBookTitleAndAuthorScreen;
import revivedevelopment.reviveclient.utils.player.ChatUtils;

@Mixin({BookScreen.class})
public abstract class BookScreenMixin extends Screen {
   @Shadow
   private Contents field_17418;
   @Shadow
   private int field_17119;

   public BookScreenMixin(Text title) {
      super(title);
   }

   @Inject(
      method = {"method_25426"},
      at = {@At("TAIL")}
   )
   private void onInit(CallbackInfo info) {
      this.addDrawableChild(new Builder(Text.literal("Copy"), button -> {
         NbtList listTag = new NbtList();

         for (int i = 0; i < this.field_17418.getPageCount(); i++) {
            listTag.add(NbtString.of(this.field_17418.getPage(i).getString()));
         }

         NbtCompound tag = new NbtCompound();
         tag.put("pages", listTag);
         tag.putInt("currentPage", this.field_17119);
         FastByteArrayOutputStream bytes = new FastByteArrayOutputStream();
         DataOutputStream out = new DataOutputStream(bytes);

         try {
            NbtIo.write(tag, out);
         } catch (IOException var11) {
            var11.printStackTrace();
         }

         String encoded = Base64.getEncoder().encodeToString(bytes.array);
         long available = MemoryStack.stackGet().getPointer();
         long size = MemoryUtil.memLengthUTF8(encoded, true);
         if (size > available) {
            ChatUtils.error("Could not copy to clipboard: Out of memory.");
         } else {
            GLFW.glfwSetClipboardString(ReviveClient.mc.getWindow().getHandle(), encoded);
         }
      }).position(4, 4).size(120, 20).build());
      ItemStack itemStack = ReviveClient.mc.player.getMainHandStack();
      Hand hand = Hand.MAIN_HAND;
      if (itemStack.getItem() != Items.WRITTEN_BOOK) {
         itemStack = ReviveClient.mc.player.getOffHandStack();
         hand = Hand.OFF_HAND;
      }

      if (itemStack.getItem() == Items.WRITTEN_BOOK) {
         ItemStack book = itemStack;
         Hand hand2 = hand;
         this.addDrawableChild(
            new Builder(
                  Text.literal("Edit title & author"), button -> ReviveClient.mc.setScreen(new EditBookTitleAndAuthorScreen(GuiThemes.get(), book, hand2))
               )
               .position(4, 26)
               .size(120, 20)
               .build()
         );
      }
   }
}
