package revivedevelopment.reviveclient.commands.commands;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.ClickEvent.CopyToClipboard;
import net.minecraft.text.HoverEvent.ShowText;
import net.minecraft.util.Formatting;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.commands.Command;
import revivedevelopment.reviveclient.utils.misc.ItemNbt;
import revivedevelopment.reviveclient.utils.player.ChatUtils;

public class NbtCommand extends Command {
   public NbtCommand() {
      super("nbt", "Prints the NBT of the item you're holding. Use '.nbt copy' to copy it to the clipboard.");
   }

   @Override
   public void execute(String[] args) {
      if (ReviveClient.mc.player != null && ReviveClient.mc.world != null) {
         ItemStack stack = ReviveClient.mc.player.getMainHandStack();
         if (stack.isEmpty()) {
            stack = ReviveClient.mc.player.getOffHandStack();
         }

         if (stack.isEmpty()) {
            this.error("You're not holding an item.", new Object[0]);
         } else {
            NbtElement nbt = ItemNbt.encode(stack);
            if (nbt == null) {
               this.error("Failed to encode the held item.", new Object[0]);
            } else {
               String snbt = nbt.toString();
               if (args.length > 0 && args[0].equalsIgnoreCase("copy")) {
                  ReviveClient.mc.keyboard.setClipboard(snbt);
                  this.info(
                     "Copied the NBT of (highlight)%s(default) to the clipboard (%s).",
                     new Object[]{stack.getName().getString(), ItemNbt.formatBytes(ItemNbt.byteSize(stack))}
                  );
               } else {
                  MutableText text = Text.empty()
                     .append(NbtHelper.toPrettyPrintedText(nbt))
                     .styled(
                        style -> style.withClickEvent(new CopyToClipboard(snbt))
                           .withHoverEvent(new ShowText(Text.literal("Click to copy").formatted(Formatting.GRAY)))
                     );
                  ChatUtils.sendMsg("Nbt", text);
                  this.info(
                     "(highlight)%s(default) - %s. Click the NBT above to copy it.",
                     new Object[]{stack.getName().getString(), ItemNbt.formatBytes(ItemNbt.byteSize(stack))}
                  );
               }
            }
         }
      } else {
         this.error("Not in a world.", new Object[0]);
      }
   }
}
