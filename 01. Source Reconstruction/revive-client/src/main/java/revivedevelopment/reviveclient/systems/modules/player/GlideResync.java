package revivedevelopment.reviveclient.systems.modules.player;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.AbstractFurnaceBlock;
import net.minecraft.block.BarrelBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.ButtonBlock;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.DispenserBlock;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.EnderChestBlock;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.block.HopperBlock;
import net.minecraft.block.LeverBlock;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.common.DisconnectS2CPacket;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.OpenScreenS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerActionResponseS2CPacket;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import revivedevelopment.reviveclient.events.entity.player.InteractBlockEvent;
import revivedevelopment.reviveclient.events.packets.PacketEvent;
import revivedevelopment.reviveclient.events.revive.MouseButtonEvent;
import revivedevelopment.reviveclient.events.world.TickEvent;
import revivedevelopment.reviveclient.settings.BoolSetting;
import revivedevelopment.reviveclient.settings.DoubleSetting;
import revivedevelopment.reviveclient.settings.EnumSetting;
import revivedevelopment.reviveclient.settings.IntSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.systems.modules.Categories;
import revivedevelopment.reviveclient.systems.modules.Module;
import revivedevelopment.reviveclient.utils.misc.input.KeyAction;

public class GlideResync extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<GlideResync.Mode> mode = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("mode"))
                  .description("Notify: alert you to relog. AutoReconnect: relog automatically (re-queues you on 2b2t)."))
               .defaultValue(GlideResync.Mode.Notify))
            .build()
      );
   private final Setting<Double> reconnectDelay = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("reconnect-delay")
            .description("Seconds to wait before reconnecting.")
            .defaultValue(3.0)
            .min(0.0)
            .sliderRange(0.0, 15.0)
            .visible(() -> this.mode.get() == GlideResync.Mode.AutoReconnect)
            .build()
      );
   private final Setting<Boolean> sound = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("alert-sound")
            .description("Plays a sound when the desync is detected.")
            .defaultValue(true)
            .visible(() -> this.mode.get() == GlideResync.Mode.Notify)
            .build()
      );
   private final Setting<Boolean> onlyAfterFlight = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("only-after-flight")
            .description(
               "Only react to refused opens shortly after flying. Off by default: packet flight modes never register as gliding client side, so the flight window can miss real desyncs."
            )
            .defaultValue(false)
            .build()
      );
   private final Setting<Integer> flightWindow = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("flight-window")
            .description("Only react to failed opens within this many seconds after a glide ended.")
            .defaultValue(60)
            .min(5)
            .max(600)
            .sliderRange(5, 300)
            .visible(this.onlyAfterFlight::get)
            .build()
      );
   private final Setting<Integer> responseTicks = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("response-ticks")
            .description("Ticks to wait for the server's container screen before treating the open as refused. Raise on high ping.")
            .defaultValue(10)
            .min(4)
            .max(40)
            .sliderRange(4, 40)
            .build()
      );
   private final Setting<Boolean> fixStuckUse = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("fix-stuck-item-use")
            .description("If the client gets stuck in an item-using state (which silently eats every right click), force-clears it after half a second.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> notify = this.sgGeneral
      .add(new BoolSetting.Builder().name("chat-feedback").description("Says in chat when a desync is detected.").defaultValue(true).build());
   private final Setting<Boolean> debug = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("debug")
            .description("Chat-logs every stage (click seen, waiting, server answered, refusal) for diagnosis.")
            .defaultValue(false)
            .build()
      );
   private static final int ALERT_COOLDOWN_TICKS = 100;
   private long ticks;
   private long lastGlideTick = Long.MIN_VALUE;
   private boolean awaitingScreen;
   private boolean expectScreen;
   private long clickTick;
   private BlockPos clickedPos;
   private long lastAlertTick = Long.MIN_VALUE;
   private BlockHitResult pendingInputHit;
   private long pendingInputTick = Long.MIN_VALUE;
   private long lastInteractTick = Long.MIN_VALUE;
   private int stuckUseTicks;

   public GlideResync() {
      super(Categories.Player, "glide-resync", "Detects the post-elytra container desync and alerts you to relog (or relogs automatically).");
   }

   @Override
   public void onActivate() {
      this.reset();
   }

   @Override
   public void onDeactivate() {
      this.reset();
   }

   private void reset() {
      this.lastGlideTick = Long.MIN_VALUE;
      this.awaitingScreen = false;
      this.clickedPos = null;
      this.lastAlertTick = Long.MIN_VALUE;
      this.pendingInputHit = null;
      this.pendingInputTick = Long.MIN_VALUE;
      this.lastInteractTick = Long.MIN_VALUE;
      this.stuckUseTicks = 0;
   }

   private void debug(String message, Object... args) {
      if (this.debug.get()) {
         if (this.mc.isOnThread()) {
            this.info("[debug] " + message, args);
         } else {
            this.mc.execute(() -> this.info("[debug] " + message, args));
         }
      }
   }

   private static boolean isContainer(Block block) {
      return block instanceof ChestBlock
         || block instanceof EnderChestBlock
         || block instanceof ShulkerBoxBlock
         || block instanceof BarrelBlock
         || block instanceof AbstractFurnaceBlock
         || block instanceof HopperBlock
         || block instanceof DispenserBlock;
   }

   private static boolean isWorldInteractive(Block block) {
      return block != Blocks.IRON_DOOR && block != Blocks.IRON_TRAPDOOR
         ? block instanceof DoorBlock
            || block instanceof TrapdoorBlock
            || block instanceof FenceGateBlock
            || block instanceof ButtonBlock
            || block instanceof LeverBlock
         : false;
   }

   private static boolean isWatched(Block block) {
      return isContainer(block) || isWorldInteractive(block);
   }

   @EventHandler
   private void onInteractBlock(InteractBlockEvent event) {
      if (this.mc.player != null && this.mc.world != null) {
         this.lastInteractTick = this.ticks;
         if (!event.isCancelled() && this.mc.currentScreen == null && event.result != null) {
            if (!this.onlyAfterFlight.get()
               || this.lastGlideTick != Long.MIN_VALUE && this.ticks - this.lastGlideTick <= this.flightWindow.get().intValue() * 20L) {
               BlockPos pos = event.result.getBlockPos();
               Block block = this.mc.world.getBlockState(pos).getBlock();
               if (isWatched(block)) {
                  this.arm(pos, isContainer(block));
                  this.debug(
                     "%s click at %s, waiting %d ticks for the server's %s",
                     this.expectScreen ? "container" : "interactive block",
                     pos.toShortString(),
                     this.responseTicks.get(),
                     this.expectScreen ? "screen" : "ack"
                  );
               }
            } else {
               this.debug("interact ignored: outside the post-flight window");
            }
         }
      }
   }

   private void arm(BlockPos pos, boolean screen) {
      this.awaitingScreen = true;
      this.expectScreen = screen;
      this.clickTick = this.ticks;
      this.clickedPos = pos.toImmutable();
   }

   @EventHandler
   private void onMouseButton(MouseButtonEvent event) {
      if (event.action == KeyAction.Press && event.button == 1) {
         if (this.mc.player != null && this.mc.world != null && this.mc.currentScreen == null) {
            if (this.mc.crosshairTarget instanceof BlockHitResult hit) {
               if (isWatched(this.mc.world.getBlockState(hit.getBlockPos()).getBlock())) {
                  this.pendingInputHit = hit;
                  this.pendingInputTick = this.ticks;
               }
            }
         }
      }
   }

   @EventHandler
   private void onPacketReceive(PacketEvent.Receive event) {
      if (event.packet instanceof OpenScreenS2CPacket) {
         if (this.awaitingScreen) {
            this.debug("server answered with a screen — open succeeded");
         }

         this.awaitingScreen = false;
         this.pendingInputHit = null;
      } else if (this.awaitingScreen && !this.expectScreen) {
         if (event.packet instanceof PlayerActionResponseS2CPacket) {
            this.debug("server acked the interaction — succeeded");
            this.awaitingScreen = false;
         } else if (event.packet instanceof BlockUpdateS2CPacket packet
            && this.clickedPos != null
            && packet.getPos().getManhattanDistance(this.clickedPos) <= 2) {
            this.debug("server updated the clicked block — succeeded");
            this.awaitingScreen = false;
         }
      }
   }

   @EventHandler
   private void onTick(TickEvent.Post event) {
      if (this.mc.player != null && this.mc.world != null) {
         long now = ++this.ticks;
         if (this.mc.player.isGliding()) {
            this.lastGlideTick = now;
            this.awaitingScreen = false;
         } else {
            if (!this.mc.player.isOnGround() && this.mc.player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA)) {
               this.lastGlideTick = now;
            }

            if (this.mc.currentScreen != null) {
               this.awaitingScreen = false;
               this.pendingInputHit = null;
            } else {
               if (this.fixStuckUse.get() && this.mc.player.isUsingItem() && !this.mc.options.useKey.isPressed()) {
                  if (++this.stuckUseTicks >= 10 && this.mc.interactionManager != null) {
                     this.stuckUseTicks = 0;
                     this.mc.interactionManager.stopUsingItem(this.mc.player);
                     if (this.notify.get()) {
                        this.info("Cleared a stuck item-use state that was eating right clicks.", new Object[0]);
                     }
                  }
               } else {
                  this.stuckUseTicks = 0;
               }

               if (this.pendingInputHit != null && now - this.pendingInputTick >= 2L) {
                  if (this.lastInteractTick < this.pendingInputTick && !this.awaitingScreen) {
                     this.arm(this.pendingInputHit.getBlockPos(), isContainer(this.mc.world.getBlockState(this.pendingInputHit.getBlockPos()).getBlock()));
                     this.clickTick = this.pendingInputTick;
                  }

                  this.pendingInputHit = null;
               }

               if (this.awaitingScreen) {
                  if (now - this.clickTick >= this.responseTicks.get().intValue()) {
                     if (now - this.clickTick > this.responseTicks.get() + 40) {
                        this.awaitingScreen = false;
                     } else if (this.lastAlertTick != Long.MIN_VALUE && now - this.lastAlertTick < 100L) {
                        this.awaitingScreen = false;
                     } else {
                        this.awaitingScreen = false;
                        this.lastAlertTick = now;
                        this.onDesyncDetected();
                     }
                  }
               }
            }
         }
      } else {
         this.reset();
      }
   }

   private void onDesyncDetected() {
      if (this.mode.get() == GlideResync.Mode.AutoReconnect) {
         if (this.notify.get()) {
            this.warning("Container desync detected after flight — relogging to fix.", new Object[0]);
         }

         this.reconnect();
      } else {
         if (this.notify.get()) {
            this.warning("Container desync detected after flight — this only clears with a relog on 2b2t. Relog to fix.", new Object[0]);
         }

         if (this.sound.get()) {
            try {
               this.mc.getSoundManager().play(PositionedSoundInstance.master((SoundEvent)SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), 1.5F, 1.0F));
            } catch (Exception var2) {
            }
         }
      }
   }

   private void reconnect() {
      ServerInfo server = this.mc.getCurrentServerEntry();
      if (server == null) {
         this.warning("Can't auto-reconnect: not connected through the server list.", new Object[0]);
      } else {
         if (this.mc.player != null) {
            this.mc.player.networkHandler.onDisconnect(new DisconnectS2CPacket(Text.literal("[GlideResync] relogging to fix container desync")));
         }

         long delayMs = (long)(this.reconnectDelay.get() * 1000.0);
         Thread thread = new Thread(() -> {
            try {
               Thread.sleep(delayMs);
            } catch (InterruptedException var5) {
            }

            this.mc.execute(() -> {
               if (this.mc.world == null) {
                  ConnectScreen.connect(new TitleScreen(), this.mc, ServerAddress.parse(server.address), server, false, null);
               }
            });
         }, "GlideResync-reconnect");
         thread.setDaemon(true);
         thread.start();
      }
   }

   public static enum Mode {
      Notify,
      AutoReconnect;
   }
}
