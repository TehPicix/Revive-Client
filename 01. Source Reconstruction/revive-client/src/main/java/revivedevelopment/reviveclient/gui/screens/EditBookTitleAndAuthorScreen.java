package revivedevelopment.reviveclient.gui.screens;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.gui.screen.ingame.BookScreen.Contents;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.WrittenBookContentComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.BookUpdateC2SPacket;
import net.minecraft.text.RawFilteredPair;
import net.minecraft.util.Hand;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.gui.GuiTheme;
import revivedevelopment.reviveclient.gui.WindowScreen;
import revivedevelopment.reviveclient.gui.widgets.containers.WTable;
import revivedevelopment.reviveclient.gui.widgets.input.WTextBox;

public class EditBookTitleAndAuthorScreen extends WindowScreen {
   private final ItemStack itemStack;
   private final Hand hand;

   public EditBookTitleAndAuthorScreen(GuiTheme theme, ItemStack itemStack, Hand hand) {
      super(theme, "Edit title & author");
      this.itemStack = itemStack;
      this.hand = hand;
   }

   @Override
   public void initWidgets() {
      WTable t = this.add(this.theme.table()).expandX().widget();
      t.add(this.theme.label("Title"));
      WTextBox title = t.add(
            this.theme
               .textBox(
                  (String)((WrittenBookContentComponent)this.itemStack.get(DataComponentTypes.WRITTEN_BOOK_CONTENT))
                     .title()
                     .get(ReviveClient.mc.shouldFilterText())
               )
         )
         .minWidth(220.0)
         .expandX()
         .widget();
      t.row();
      t.add(this.theme.label("Author"));
      WTextBox author = t.add(this.theme.textBox(((WrittenBookContentComponent)this.itemStack.get(DataComponentTypes.WRITTEN_BOOK_CONTENT)).author()))
         .minWidth(220.0)
         .expandX()
         .widget();
      t.row();
      t.add(this.theme.button("Done")).expandX().widget().action = () -> {
         WrittenBookContentComponent component = (WrittenBookContentComponent)this.itemStack.get(DataComponentTypes.WRITTEN_BOOK_CONTENT);
         WrittenBookContentComponent newComponent = new WrittenBookContentComponent(
            RawFilteredPair.of(title.get()), author.get(), component.generation(), component.pages(), component.resolved()
         );
         this.itemStack.set(DataComponentTypes.WRITTEN_BOOK_CONTENT, newComponent);
         Contents contents = new Contents(
            ((WrittenBookContentComponent)this.itemStack.get(DataComponentTypes.WRITTEN_BOOK_CONTENT)).getPages(ReviveClient.mc.shouldFilterText())
         );
         List<String> pages = new ArrayList<>(contents.getPageCount());

         for (int i = 0; i < contents.getPageCount(); i++) {
            pages.add(contents.getPage(i).getString());
         }

         ReviveClient.mc
            .getNetworkHandler()
            .sendPacket(
               new BookUpdateC2SPacket(
                  this.hand == Hand.MAIN_HAND ? ReviveClient.mc.player.getInventory().getSelectedSlot() : 40, pages, Optional.of(title.get())
               )
            );
         this.close();
      };
   }
}
