package revivedevelopment.reviveclient.systems.modules.player;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BowItem;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.events.entity.player.FinishUsingItemEvent;
import revivedevelopment.reviveclient.events.entity.player.StoppedUsingItemEvent;
import revivedevelopment.reviveclient.events.revive.MouseButtonEvent;
import revivedevelopment.reviveclient.events.world.TickEvent;
import revivedevelopment.reviveclient.settings.BoolSetting;
import revivedevelopment.reviveclient.settings.EnumSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.systems.friends.Friend;
import revivedevelopment.reviveclient.systems.friends.Friends;
import revivedevelopment.reviveclient.systems.managers.ElytraDurabilitySaver;
import revivedevelopment.reviveclient.systems.modules.Categories;
import revivedevelopment.reviveclient.systems.modules.Module;
import revivedevelopment.reviveclient.utils.misc.input.KeyAction;
import revivedevelopment.reviveclient.utils.player.ChatUtils;

public class MiddleClickExtra extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<MiddleClickExtra.Mode> mode = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("mode"))
                  .description("Which item to use when you middle click."))
               .defaultValue(MiddleClickExtra.Mode.Pearl))
            .build()
      );
   private final Setting<Boolean> message = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("message")
            .description("Sends a message to the player when you add them as a friend.")
            .defaultValue(false)
            .visible(() -> this.mode.get() == MiddleClickExtra.Mode.AddFriend)
            .build()
      );
   private final Setting<Boolean> notify = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("notify")
            .description("Notifies you when you don't have the specified item.")
            .defaultValue(true)
            .visible(() -> this.mode.get() != MiddleClickExtra.Mode.AddFriend)
            .build()
      );
   private final Setting<Boolean> rocketInAir = this.sgGeneral
      .add(new BoolSetting.Builder().name("rocket-in-air").description("Uses a firework rocket when gliding, regardless of mode.").defaultValue(true).build());
   private boolean isUsing;
   private boolean swapping;
   private int pendingRocketTicks;

   public MiddleClickExtra() {
      super(Categories.Player, "middle-click-extra", "Throw/use a chosen item (or friend a player) when you middle click.");
   }

   @Override
   public void onDeactivate() {
      this.stopIfUsing();
   }

   @EventHandler
   private void onMouseButton(MouseButtonEvent event) {
      if (event.action == KeyAction.Press && event.button == 2 && this.mc.currentScreen == null && this.mc.player != null) {
         if (!this.rocketInAir.get() || !this.mc.player.isGliding()) {
            if (this.mode.get() == MiddleClickExtra.Mode.AddFriend) {
               if (this.mc.targetedEntity instanceof PlayerEntity player) {
                  if (!Friends.get().isFriend(player)) {
                     Friends.get().add(new Friend(player));
                     this.info("Added %s to friends.", new Object[]{player.getName().getString()});
                     if (this.message.get()) {
                        ChatUtils.sendPlayerMsg("/msg " + player.getName().getString() + " I just friended you.");
                     }
                  } else {
                     Friends.get().remove(Friends.get().get(player));
                     this.info("Removed %s from friends.", new Object[]{player.getName().getString()});
                  }

                  event.cancel();
               }
            } else {
               if (this.useItem(this.mode.get().item, this.mode.get().immediate)) {
                  event.cancel();
               }
            }
         } else if (!ElytraDurabilitySaver.get().serverGlideSteady()) {
            ElytraDurabilitySaver.get().holdForRocket();
            this.pendingRocketTicks = 10;
            event.cancel();
         } else {
            if (this.useItem(Items.FIREWORK_ROCKET, true)) {
               event.cancel();
            }
         }
      }
   }

   private boolean useItem(Item item, boolean immediate) {
      if (this.mc.player.getMainHandStack().isOf(item)) {
         if (immediate) {
            this.mc.interactionManager.interactItem(this.mc.player, Hand.MAIN_HAND);
         } else {
            this.startHolding();
         }

         return true;
      } else if (!ReviveClient.SWAP.canSwap(item)) {
         if (this.notify.get()) {
            this.warning("Couldn't find the item to use.", new Object[0]);
         }

         return false;
      } else if (immediate) {
         if (!ReviveClient.SWAP.beginSwap(item, true)) {
            return false;
         } else {
            try {
               this.mc.interactionManager.interactItem(this.mc.player, Hand.MAIN_HAND);
            } finally {
               ReviveClient.SWAP.endSwap(true);
            }

            return true;
         }
      } else if (!ReviveClient.SWAP.beginSwap(item, false)) {
         return false;
      } else {
         this.swapping = true;
         this.startHolding();
         return true;
      }
   }

   private void startHolding() {
      this.mc.options.useKey.setPressed(true);
      this.isUsing = true;
   }

   @EventHandler
   private void onTick(TickEvent.Pre event) {
      if (this.pendingRocketTicks > 0) {
         this.pendingRocketTicks--;
         if (this.mc.player == null || !this.mc.player.isGliding()) {
            this.pendingRocketTicks = 0;
         } else if (ElytraDurabilitySaver.get().serverGlideSteady()) {
            this.pendingRocketTicks = 0;
            this.useItem(Items.FIREWORK_ROCKET, true);
         } else {
            ElytraDurabilitySaver.get().holdForRocket();
         }
      }

      if (this.isUsing && this.mc.player != null) {
         boolean pressed = true;
         if (this.mc.player.getActiveItem().getItem() instanceof BowItem) {
            pressed = BowItem.getPullProgress(this.mc.player.getItemUseTime()) < 1.0F;
         }

         this.mc.options.useKey.setPressed(pressed);
      }
   }

   @EventHandler
   private void onStoppedUsingItem(StoppedUsingItemEvent event) {
      this.stopIfUsing();
   }

   @EventHandler
   private void onFinishUsingItem(FinishUsingItemEvent event) {
      this.stopIfUsing();
   }

   private void stopIfUsing() {
      if (this.isUsing) {
         this.isUsing = false;
         this.mc.options.useKey.setPressed(false);
         if (this.swapping) {
            ReviveClient.SWAP.endSwap(false);
            this.swapping = false;
         }
      }
   }

   public static enum Mode {
      Pearl(Items.ENDER_PEARL, true),
      XP(Items.EXPERIENCE_BOTTLE, true),
      Rocket(Items.FIREWORK_ROCKET, true),
      Bow(Items.BOW, false),
      Gap(Items.GOLDEN_APPLE, false),
      EGap(Items.ENCHANTED_GOLDEN_APPLE, false),
      Chorus(Items.CHORUS_FRUIT, false),
      AddFriend(null, true);

      public final Item item;
      public final boolean immediate;

      private Mode(Item item, boolean immediate) {
         this.item = item;
         this.immediate = immediate;
      }
   }
}
