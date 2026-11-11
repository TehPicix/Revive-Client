package revivedevelopment.reviveclient.systems.modules.movement;

import java.util.ArrayList;
import java.util.List;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.ingame.AbstractCommandBlockScreen;
import net.minecraft.client.gui.screen.ingame.AbstractSignEditScreen;
import net.minecraft.client.gui.screen.ingame.AnvilScreen;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.StructureBlockScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.item.ItemGroups;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.MathHelper;
import revivedevelopment.reviveclient.events.entity.player.SendMovementPacketsEvent;
import revivedevelopment.reviveclient.events.packets.PacketEvent;
import revivedevelopment.reviveclient.events.render.Render3DEvent;
import revivedevelopment.reviveclient.events.revive.KeyEvent;
import revivedevelopment.reviveclient.events.revive.MouseButtonEvent;
import revivedevelopment.reviveclient.gui.WidgetScreen;
import revivedevelopment.reviveclient.mixin.CreativeInventoryScreenAccessor;
import revivedevelopment.reviveclient.settings.BoolSetting;
import revivedevelopment.reviveclient.settings.DoubleSetting;
import revivedevelopment.reviveclient.settings.EnumSetting;
import revivedevelopment.reviveclient.settings.IntSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.systems.modules.Categories;
import revivedevelopment.reviveclient.systems.modules.Module;
import revivedevelopment.reviveclient.systems.modules.Modules;
import revivedevelopment.reviveclient.systems.modules.render.Freecam;
import revivedevelopment.reviveclient.utils.misc.input.Input;
import revivedevelopment.reviveclient.utils.misc.input.KeyAction;

public class GUIMove extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<GUIMove.Screens> screens = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("guis")).description("Which GUIs to move in."))
               .defaultValue(GUIMove.Screens.Inventory))
            .build()
      );
   public final Setting<Boolean> jump = this.sgGeneral
      .add(new BoolSetting.Builder().name("jump").description("Allows you to jump while in GUIs.").defaultValue(true).onChanged(aBoolean -> {
         if (this.isActive() && !aBoolean) {
            this.mc.options.jumpKey.setPressed(false);
         }
      }).build());
   public final Setting<Boolean> sneak = this.sgGeneral
      .add(new BoolSetting.Builder().name("sneak").description("Allows you to sneak while in GUIs.").defaultValue(true).onChanged(aBoolean -> {
         if (this.isActive() && !aBoolean) {
            this.mc.options.sneakKey.setPressed(false);
         }
      }).build());
   public final Setting<Boolean> sprint = this.sgGeneral
      .add(new BoolSetting.Builder().name("sprint").description("Allows you to sprint while in GUIs.").defaultValue(true).onChanged(aBoolean -> {
         if (this.isActive() && !aBoolean) {
            this.mc.options.sprintKey.setPressed(false);
         }
      }).build());
   private final Setting<Boolean> arrowsRotate = this.sgGeneral
      .add(new BoolSetting.Builder().name("arrows-rotate").description("Allows you to use your arrow keys to rotate while in GUIs.").defaultValue(true).build());
   private final Setting<Double> rotateSpeed = this.sgGeneral
      .add(new DoubleSetting.Builder().name("rotate-speed").description("Rotation speed while in GUIs.").defaultValue(4.0).min(0.0).build());
   private final SettingGroup sgGrim = this.settings.createGroup("Grim");
   private final Setting<Boolean> grim = this.sgGrim
      .add(
         new BoolSetting.Builder()
            .name("grim")
            .description("Pauses movement while you move items in the inventory so Grim accepts the clicks. Also pauses jump, sneak and sprint.")
            .defaultValue(false)
            .build()
      );
   private final Setting<Integer> resumeDelay = this.sgGrim
      .add(
         new IntSetting.Builder()
            .name("resume-delay")
            .description("How many ticks after the last inventory click movement resumes.")
            .defaultValue(4)
            .min(0)
            .sliderMax(20)
            .visible(this.grim::get)
            .build()
      );
   private final Setting<Boolean> pauseOnPickup = this.sgGrim
      .add(
         new BoolSetting.Builder()
            .name("pause-on-pickup")
            .description("Also pause for the click that picks an item up. Off: only the click that puts it down pauses — Grim may only check that one.")
            .defaultValue(false)
            .visible(this.grim::get)
            .build()
      );
   private final List<ClickSlotC2SPacket> grimQueuedClicks = new ArrayList<>();
   private boolean grimPaused;
   private boolean grimServerClear;
   private int grimResumeTimer;
   private int grimFlushTimeout;
   private static final int GRIM_FLUSH_TIMEOUT = 10;
   private boolean grimBypassIntercept;
   private boolean grimWasSprinting;
   private boolean grimCursorEmptyAtClick;

   public GUIMove() {
      super(Categories.Movement, "gui-move", "Allows you to perform various actions while in GUIs.");
   }

   @Override
   public void onActivate() {
      this.grimServerClear = false;
   }

   @Override
   public void onDeactivate() {
      this.flushGrimQueue();
      this.grimPaused = false;
      this.grimResumeTimer = 0;
      this.grimWasSprinting = false;
      this.mc.options.forwardKey.setPressed(false);
      this.mc.options.backKey.setPressed(false);
      this.mc.options.leftKey.setPressed(false);
      this.mc.options.rightKey.setPressed(false);
      if (this.jump.get()) {
         this.mc.options.jumpKey.setPressed(false);
      }

      if (this.sneak.get()) {
         this.mc.options.sneakKey.setPressed(false);
      }

      if (this.sprint.get()) {
         this.mc.options.sprintKey.setPressed(false);
      }
   }

   public boolean disableSpace() {
      return this.isActive() && this.jump.get() && this.mc.options.jumpKey.isDefault();
   }

   public boolean disableArrows() {
      return this.isActive() && this.arrowsRotate.get();
   }

   @EventHandler
   private void onKey(KeyEvent event) {
      this.recordGrimCursorState();
      this.onInput(event.key, event.action);
   }

   @EventHandler
   private void onButton(MouseButtonEvent event) {
      this.recordGrimCursorState();
      this.onInput(event.button, event.action);
   }

   private void recordGrimCursorState() {
      if (this.mc.player != null && this.mc.currentScreen instanceof HandledScreen) {
         this.grimCursorEmptyAtClick = this.mc.player.currentScreenHandler.getCursorStack().isEmpty();
      }
   }

   private void onInput(int key, KeyAction action) {
      if (!this.skip()) {
         if (!this.grimPaused) {
            this.pass(this.mc.options.forwardKey, key, action);
            this.pass(this.mc.options.backKey, key, action);
            this.pass(this.mc.options.leftKey, key, action);
            this.pass(this.mc.options.rightKey, key, action);
            if (this.jump.get()) {
               this.pass(this.mc.options.jumpKey, key, action);
            }

            if (this.sneak.get()) {
               this.pass(this.mc.options.sneakKey, key, action);
            }

            if (this.sprint.get()) {
               this.pass(this.mc.options.sprintKey, key, action);
            }
         }
      }
   }

   @EventHandler
   private void onRender3D(Render3DEvent event) {
      if (!this.skip()) {
         float rotationDelta = Math.min((float)(this.rotateSpeed.get() * event.frameTime * 20.0), 100.0F);
         Freecam freecam = Modules.get().get(Freecam.class);
         if (this.arrowsRotate.get()) {
            if (!freecam.isActive()) {
               float yaw = this.mc.player.getYaw();
               float pitch = this.mc.player.getPitch();
               if (Input.isKeyPressed(263)) {
                  yaw -= rotationDelta;
               }

               if (Input.isKeyPressed(262)) {
                  yaw += rotationDelta;
               }

               if (Input.isKeyPressed(265)) {
                  pitch -= rotationDelta;
               }

               if (Input.isKeyPressed(264)) {
                  pitch += rotationDelta;
               }

               pitch = MathHelper.clamp(pitch, -90.0F, 90.0F);
               this.mc.player.setYaw(yaw);
               this.mc.player.setPitch(pitch);
            } else {
               double dy = 0.0;
               double dx = 0.0;
               if (Input.isKeyPressed(263)) {
                  dy = -rotationDelta;
               }

               if (Input.isKeyPressed(262)) {
                  dy = rotationDelta;
               }

               if (Input.isKeyPressed(265)) {
                  dx = -rotationDelta;
               }

               if (Input.isKeyPressed(264)) {
                  dx = rotationDelta;
               }

               freecam.changeLookDirection(dy, dx);
            }
         }
      }
   }

   private void pass(KeyBinding bind, int key, KeyAction action) {
      if (Input.getKey(bind) == key) {
         if (action == KeyAction.Press) {
            bind.setPressed(true);
         }

         if (action == KeyAction.Release) {
            bind.setPressed(false);
         }
      }
   }

   @EventHandler
   private void onPacketSend(PacketEvent.Send event) {
      if (!this.grimBypassIntercept && !event.isCancelled() && this.grim.get()) {
         if (event.packet instanceof CloseHandledScreenC2SPacket) {
            this.flushGrimQueue();
         } else if (event.packet instanceof ClickSlotC2SPacket click) {
            if (!this.skip() && this.mc.currentScreen instanceof HandledScreen) {
               if (this.grimServerClear && this.grimQueuedClicks.isEmpty()) {
                  if (this.grimPaused) {
                     this.grimResumeTimer = this.resumeDelay.get();
                  }
               } else if (!this.pauseOnPickup.get()
                  && this.grimQueuedClicks.isEmpty()
                  && this.grimCursorEmptyAtClick
                  && click.actionType() == SlotActionType.PICKUP) {
                  if (this.grimPaused) {
                     this.grimResumeTimer = this.resumeDelay.get();
                  }
               } else {
                  this.engageGrimPause();
                  this.grimQueuedClicks.add(click);
                  event.setCancelled(true);
               }
            }
         }
      }
   }

   @EventHandler
   private void onSendMovementPacketsPost(SendMovementPacketsEvent.Post event) {
      ClientPlayerEntity player = this.mc.player;
      if (player != null) {
         PlayerInput input = player.input.playerInput;
         this.grimServerClear = !input.forward()
            && !input.backward()
            && !input.left()
            && !input.right()
            && !input.jump()
            && !input.sneak()
            && !input.sprint()
            && !player.isSprinting();
         if (this.grimPaused) {
            if (!this.grim.get()) {
               this.flushGrimQueue();
               this.resumeGrim();
            } else if (this.grimQueuedClicks.isEmpty()) {
               if (!player.currentScreenHandler.getCursorStack().isEmpty()) {
                  this.grimResumeTimer = this.resumeDelay.get();
               } else {
                  if (--this.grimResumeTimer <= 0) {
                     this.resumeGrim();
                  }
               }
            } else {
               if (this.grimServerClear || ++this.grimFlushTimeout > 10) {
                  this.flushGrimQueue();
                  this.grimResumeTimer = this.resumeDelay.get();
               }
            }
         }
      }
   }

   private void engageGrimPause() {
      this.grimResumeTimer = this.resumeDelay.get();
      if (!this.grimPaused) {
         this.grimPaused = true;
         this.grimFlushTimeout = 0;
         this.grimWasSprinting = this.mc.player != null && this.mc.player.isSprinting();
         this.mc.options.forwardKey.setPressed(false);
         this.mc.options.backKey.setPressed(false);
         this.mc.options.leftKey.setPressed(false);
         this.mc.options.rightKey.setPressed(false);
         this.mc.options.jumpKey.setPressed(false);
         this.mc.options.sneakKey.setPressed(false);
         this.mc.options.sprintKey.setPressed(false);
         if (this.mc.player != null) {
            this.mc.player.setSprinting(false);
         }
      }
   }

   private void flushGrimQueue() {
      this.grimFlushTimeout = 0;
      if (!this.grimQueuedClicks.isEmpty()) {
         if (this.mc.getNetworkHandler() == null) {
            this.grimQueuedClicks.clear();
         } else {
            this.grimBypassIntercept = true;

            for (ClickSlotC2SPacket click : this.grimQueuedClicks) {
               this.mc.getNetworkHandler().sendPacket(click);
            }

            this.grimBypassIntercept = false;
            this.grimQueuedClicks.clear();
         }
      }
   }

   private void resumeGrim() {
      this.grimPaused = false;
      this.grimResumeTimer = 0;
      boolean restoreSprint = this.grimWasSprinting;
      this.grimWasSprinting = false;
      if (!this.skip()) {
         this.mc.options.forwardKey.setPressed(Input.isPressed(this.mc.options.forwardKey));
         this.mc.options.backKey.setPressed(Input.isPressed(this.mc.options.backKey));
         this.mc.options.leftKey.setPressed(Input.isPressed(this.mc.options.leftKey));
         this.mc.options.rightKey.setPressed(Input.isPressed(this.mc.options.rightKey));
         if (this.jump.get()) {
            this.mc.options.jumpKey.setPressed(Input.isPressed(this.mc.options.jumpKey));
         }

         if (this.sneak.get()) {
            this.mc.options.sneakKey.setPressed(Input.isPressed(this.mc.options.sneakKey));
         }

         if (this.sprint.get()) {
            this.mc.options.sprintKey.setPressed(Input.isPressed(this.mc.options.sprintKey));
         }
      }

      if (restoreSprint && this.mc.player != null && Input.isPressed(this.mc.options.forwardKey)) {
         this.mc.player.setSprinting(true);
      }
   }

   public boolean skip() {
      if (this.mc.currentScreen == null
         || this.mc.currentScreen instanceof CreativeInventoryScreen && CreativeInventoryScreenAccessor.getSelectedTab() == ItemGroups.getSearchGroup()
         || this.mc.currentScreen instanceof ChatScreen
         || this.mc.currentScreen instanceof AbstractSignEditScreen
         || this.mc.currentScreen instanceof AnvilScreen
         || this.mc.currentScreen instanceof AbstractCommandBlockScreen
         || this.mc.currentScreen instanceof StructureBlockScreen) {
         return true;
      } else {
         return this.screens.get() == GUIMove.Screens.GUI && !(this.mc.currentScreen instanceof WidgetScreen)
            ? true
            : this.screens.get() == GUIMove.Screens.Inventory && this.mc.currentScreen instanceof WidgetScreen;
      }
   }

   public static enum Screens {
      GUI,
      Inventory,
      Both;
   }
}
