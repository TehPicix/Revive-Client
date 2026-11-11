package revivedevelopment.reviveclient.gui.widgets.input;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.BlockPos;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.events.entity.player.InteractBlockEvent;
import revivedevelopment.reviveclient.events.entity.player.StartBreakingBlockEvent;
import revivedevelopment.reviveclient.gui.widgets.containers.WHorizontalList;
import revivedevelopment.reviveclient.gui.widgets.pressable.WButton;
import revivedevelopment.reviveclient.utils.Utils;

public class WBlockPosEdit extends WHorizontalList {
   public Runnable action;
   public Runnable actionOnRelease;
   private WTextBox textBoxX;
   private WTextBox textBoxY;
   private WTextBox textBoxZ;
   private Screen previousScreen;
   private BlockPos value;
   private BlockPos lastValue;
   private boolean clicking;

   public WBlockPosEdit(BlockPos value) {
      this.value = value;
   }

   @Override
   public void init() {
      this.addTextBox();
      if (Utils.canUpdate()) {
         WButton click = this.add(this.theme.button("Click")).expandX().widget();
         click.action = () -> {
            this.clicking = true;
            ReviveClient.EVENT_BUS.subscribe(this);
            this.previousScreen = ReviveClient.mc.currentScreen;
            ReviveClient.mc.setScreen(null);
         };
         WButton here = this.add(this.theme.button("Set Here")).expandX().widget();
         here.action = () -> {
            this.lastValue = this.value;
            this.set(new BlockPos(ReviveClient.mc.player.getBlockPos()));
            this.newValueCheck();
            this.clear();
            this.init();
         };
      }
   }

   @EventHandler
   private void onStartBreakingBlock(StartBreakingBlockEvent event) {
      if (this.clicking) {
         this.clicking = false;
         event.cancel();
         ReviveClient.EVENT_BUS.unsubscribe(this);
         ReviveClient.mc.setScreen(this.previousScreen);
      }
   }

   @EventHandler
   private void onInteractBlock(InteractBlockEvent event) {
      if (this.clicking) {
         if (event.result.getType() == Type.MISS) {
            return;
         }

         this.lastValue = this.value;
         this.set(event.result.getBlockPos());
         this.newValueCheck();
         this.clear();
         this.init();
         this.clicking = false;
         event.cancel();
         ReviveClient.EVENT_BUS.unsubscribe(this);
         ReviveClient.mc.setScreen(this.previousScreen);
      }
   }

   private boolean filter(String text, char c) {
      boolean validate = true;
      boolean good;
      if (c == '-' && text.isEmpty()) {
         good = true;
         validate = false;
      } else {
         good = Character.isDigit(c);
      }

      if (good && validate) {
         try {
            Integer.parseInt(text + c);
         } catch (NumberFormatException var6) {
            good = false;
         }
      }

      return good;
   }

   public BlockPos get() {
      return this.value;
   }

   public void set(BlockPos value) {
      this.value = value;
   }

   private void addTextBox() {
      this.textBoxX = this.add(this.theme.textBox(Integer.toString(this.value.getX()), this::filter)).minWidth(75.0).widget();
      this.textBoxY = this.add(this.theme.textBox(Integer.toString(this.value.getY()), this::filter)).minWidth(75.0).widget();
      this.textBoxZ = this.add(this.theme.textBox(Integer.toString(this.value.getZ()), this::filter)).minWidth(75.0).widget();
      this.textBoxX.actionOnUnfocused = () -> {
         this.lastValue = this.value;
         if (this.textBoxX.get().isEmpty()) {
            this.set(new BlockPos(0, 0, 0));
         } else {
            try {
               this.set(new BlockPos(Integer.parseInt(this.textBoxX.get()), this.value.getY(), this.value.getZ()));
            } catch (NumberFormatException var2) {
            }
         }

         this.newValueCheck();
      };
      this.textBoxY.actionOnUnfocused = () -> {
         this.lastValue = this.value;
         if (this.textBoxY.get().isEmpty()) {
            this.set(new BlockPos(0, 0, 0));
         } else {
            try {
               this.set(new BlockPos(this.value.getX(), Integer.parseInt(this.textBoxY.get()), this.value.getZ()));
            } catch (NumberFormatException var2) {
            }
         }

         this.newValueCheck();
      };
      this.textBoxZ.actionOnUnfocused = () -> {
         this.lastValue = this.value;
         if (this.textBoxZ.get().isEmpty()) {
            this.set(new BlockPos(0, 0, 0));
         } else {
            try {
               this.set(new BlockPos(this.value.getX(), this.value.getY(), Integer.parseInt(this.textBoxZ.get())));
            } catch (NumberFormatException var2) {
            }
         }

         this.newValueCheck();
      };
   }

   private void newValueCheck() {
      if (this.value != this.lastValue) {
         if (this.action != null) {
            this.action.run();
         }

         if (this.actionOnRelease != null) {
            this.actionOnRelease.run();
         }
      }
   }
}
