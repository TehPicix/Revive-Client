package revivedevelopment.reviveclient.systems.modules.player;

import java.util.List;
import java.util.Random;
import meteordevelopment.orbit.EventHandler;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.events.world.TickEvent;
import revivedevelopment.reviveclient.settings.BoolSetting;
import revivedevelopment.reviveclient.settings.EnumSetting;
import revivedevelopment.reviveclient.settings.IntSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.settings.StringListSetting;
import revivedevelopment.reviveclient.systems.modules.Categories;
import revivedevelopment.reviveclient.systems.modules.Module;
import revivedevelopment.reviveclient.utils.Utils;
import revivedevelopment.reviveclient.utils.player.ChatUtils;

public class AntiAFK extends Module {
   private final SettingGroup sgActions = this.settings.createGroup("Actions");
   private final SettingGroup sgMessages = this.settings.createGroup("Messages");
   private final Setting<Boolean> jump = this.sgActions.add(new BoolSetting.Builder().name("jump").description("Jump randomly.").defaultValue(true).build());
   private final Setting<Boolean> swing = this.sgActions
      .add(new BoolSetting.Builder().name("swing").description("Swings your hand.").defaultValue(false).build());
   private final Setting<Boolean> sneak = this.sgActions
      .add(new BoolSetting.Builder().name("sneak").description("Sneaks and unsneaks quickly.").defaultValue(false).build());
   private final Setting<Integer> sneakTime = this.sgActions
      .add(
         new IntSetting.Builder()
            .name("sneak-time")
            .description("How many ticks to stay sneaked.")
            .defaultValue(5)
            .min(1)
            .sliderMin(1)
            .visible(this.sneak::get)
            .build()
      );
   private final Setting<Boolean> strafe = this.sgActions
      .add(new BoolSetting.Builder().name("strafe").description("Strafe right and left.").defaultValue(false).onChanged(aBoolean -> {
         this.strafeTimer = 0;
         this.direction = false;
         if (this.isActive()) {
            this.mc.options.leftKey.setPressed(false);
            this.mc.options.rightKey.setPressed(false);
         }
      }).build());
   private final Setting<Boolean> spin = this.sgActions
      .add(new BoolSetting.Builder().name("spin").description("Spins the player in place.").defaultValue(true).build());
   private final Setting<AntiAFK.SpinMode> spinMode = this.sgActions
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("spin-mode"))
                     .description("Server = only the server sees the spin (rides the move packet, camera untouched). Client = actually turns you."))
                  .defaultValue(AntiAFK.SpinMode.Server))
               .visible(this.spin::get))
            .build()
      );
   private final Setting<Integer> spinSpeed = this.sgActions
      .add(new IntSetting.Builder().name("speed").description("The speed to spin you (degrees per tick).").defaultValue(7).visible(this.spin::get).build());
   private final Setting<Integer> pitch = this.sgActions
      .add(
         new IntSetting.Builder()
            .name("pitch")
            .description("The pitch to send to the server.")
            .defaultValue(0)
            .range(-90, 90)
            .sliderRange(-90, 90)
            .visible(() -> this.spin.get() && this.spinMode.get() == AntiAFK.SpinMode.Server)
            .build()
      );
   private final Setting<Boolean> sendMessages = this.sgMessages
      .add(new BoolSetting.Builder().name("send-messages").description("Sends chat messages to prevent getting kicked for AFK.").defaultValue(false).build());
   private final Setting<Boolean> randomMessage = this.sgMessages
      .add(
         new BoolSetting.Builder()
            .name("random")
            .description("Selects a random message from your message list.")
            .defaultValue(false)
            .visible(this.sendMessages::get)
            .build()
      );
   private final Setting<Integer> delay = this.sgMessages
      .add(
         new IntSetting.Builder()
            .name("delay")
            .description("The delay between messages in seconds.")
            .defaultValue(15)
            .min(0)
            .sliderMax(30)
            .visible(this.sendMessages::get)
            .build()
      );
   private final Setting<List<String>> messages = this.sgMessages
      .add(
         new StringListSetting.Builder()
            .name("messages")
            .description("The messages to choose from.")
            .defaultValue("I'm not AFK!", "Still here.")
            .visible(this.sendMessages::get)
            .build()
      );
   private final Random random = new Random();
   private int messageTimer = 0;
   private int messageI = 0;
   private int sneakTimer = 0;
   private int strafeTimer = 0;
   private boolean direction = false;
   private float spinYaw;

   public AntiAFK() {
      super(Categories.Player, "anti-afk", "Performs different actions to prevent getting kicked while AFK.");
   }

   @Override
   public void onActivate() {
      if (this.sendMessages.get() && this.messages.get().isEmpty()) {
         this.warning("Message list is empty, disabling messages...", new Object[0]);
         this.sendMessages.set(false);
      }

      this.spinYaw = this.mc.player != null ? this.mc.player.getYaw() : 0.0F;
      this.messageTimer = this.delay.get() * 20;
   }

   @Override
   public void onDeactivate() {
      if (this.mc.options != null) {
         if (this.strafe.get()) {
            this.mc.options.leftKey.setPressed(false);
            this.mc.options.rightKey.setPressed(false);
         }

         if (this.jump.get()) {
            this.mc.options.jumpKey.setPressed(false);
         }

         if (this.sneak.get()) {
            this.mc.options.sneakKey.setPressed(false);
         }
      }
   }

   @EventHandler
   private void onTick(TickEvent.Pre event) {
      if (Utils.canUpdate()) {
         if (this.jump.get()) {
            if (this.mc.options.jumpKey.isPressed()) {
               this.mc.options.jumpKey.setPressed(false);
            } else if (this.random.nextInt(99) == 0) {
               this.mc.options.jumpKey.setPressed(true);
            }
         }

         if (this.swing.get() && this.random.nextInt(99) == 0) {
            this.mc.player.swingHand(this.mc.player.getActiveHand());
         }

         if (this.sneak.get()) {
            if (this.sneakTimer++ >= this.sneakTime.get()) {
               this.mc.options.sneakKey.setPressed(false);
               if (this.random.nextInt(99) == 0) {
                  this.sneakTimer = 0;
               }
            } else {
               this.mc.options.sneakKey.setPressed(true);
            }
         }

         if (this.strafe.get() && this.strafeTimer-- <= 0) {
            this.mc.options.leftKey.setPressed(!this.direction);
            this.mc.options.rightKey.setPressed(this.direction);
            this.direction = !this.direction;
            this.strafeTimer = 20;
         }

         if (this.spin.get()) {
            this.spinYaw = this.spinYaw + this.spinSpeed.get().intValue();
            switch ((AntiAFK.SpinMode)this.spinMode.get()) {
               case Server:
                  ReviveClient.ROTATION.requestMotionRotation(this.spinYaw, this.pitch.get().intValue());
                  break;
               case Client:
                  this.mc.player.setYaw(this.spinYaw);
            }
         }

         if (this.sendMessages.get() && !this.messages.get().isEmpty() && this.messageTimer-- <= 0) {
            if (this.randomMessage.get()) {
               this.messageI = this.random.nextInt(this.messages.get().size());
            } else if (++this.messageI >= this.messages.get().size()) {
               this.messageI = 0;
            }

            ChatUtils.sendPlayerMsg(this.messages.get().get(this.messageI));
            this.messageTimer = this.delay.get() * 20;
         }
      }
   }

   public static enum SpinMode {
      Server,
      Client;
   }
}
