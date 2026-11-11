package revivedevelopment.reviveclient.systems.modules.player;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.common.DisconnectS2CPacket;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import revivedevelopment.reviveclient.events.world.TickEvent;
import revivedevelopment.reviveclient.settings.BlockPosSetting;
import revivedevelopment.reviveclient.settings.BoolSetting;
import revivedevelopment.reviveclient.settings.DoubleSetting;
import revivedevelopment.reviveclient.settings.IntSetting;
import revivedevelopment.reviveclient.settings.KeybindSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.systems.modules.Categories;
import revivedevelopment.reviveclient.systems.modules.Module;
import revivedevelopment.reviveclient.utils.entity.DamageUtil;
import revivedevelopment.reviveclient.utils.misc.Keybind;
import revivedevelopment.reviveclient.utils.world.TickRate;

public class AutoLogPlus extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Boolean> logOnY = this.sgGeneral
      .add(new BoolSetting.Builder().name("log-on-y").description("Logs out if you are below a certain Y level.").defaultValue(false).build());
   private final Setting<Double> yLevel = this.sgGeneral
      .add(new DoubleSetting.Builder().name("y-level").defaultValue(256.0).min(-128.0).sliderRange(-128.0, 320.0).visible(this.logOnY::get).build());
   private final Setting<Boolean> logArmor = this.sgGeneral
      .add(
         new BoolSetting.Builder().name("log-armor").description("Logs out if your armor goes below a certain durability amount.").defaultValue(false).build()
      );
   private final Setting<Boolean> ignoreElytra = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("ignore-elytra")
            .description("Ignores the elytra when checking armor durability.")
            .defaultValue(false)
            .visible(this.logArmor::get)
            .build()
      );
   private final Setting<Double> armorPercent = this.sgGeneral
      .add(new DoubleSetting.Builder().name("armor-percent").defaultValue(5.0).min(0.0).sliderRange(0.0, 100.0).visible(this.logArmor::get).build());
   private final Setting<Boolean> logPortal = this.sgGeneral
      .add(new BoolSetting.Builder().name("log-on-portal").description("Logs out if you are in a portal for too long.").defaultValue(false).build());
   private final Setting<Integer> portalTicks = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("portal-ticks")
            .description("The amount of ticks in a portal before logging out (it takes 80 ticks to go through a portal).")
            .defaultValue(30)
            .min(1)
            .sliderMax(70)
            .visible(this.logPortal::get)
            .build()
      );
   private final Setting<Boolean> logPosition = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("log-position")
            .description("Logs out if you are within x blocks of this position. Y is not included.")
            .defaultValue(false)
            .build()
      );
   private final Setting<BlockPos> position = this.sgGeneral
      .add(
         new BlockPosSetting.Builder()
            .name("position")
            .description("The position to log out at. Y position is ignored.")
            .defaultValue(new BlockPos(0, 0, 0))
            .visible(this.logPosition::get)
            .build()
      );
   private final Setting<Double> distance = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("distance")
            .description("The distance from the position to log out at.")
            .defaultValue(100.0)
            .sliderRange(0.0, 1000.0)
            .visible(this.logPosition::get)
            .build()
      );
   private final Setting<Boolean> serverNotResponding = this.sgGeneral
      .add(new BoolSetting.Builder().name("server-not-responding").description("Logs out if the server is not responding.").defaultValue(false).build());
   private final Setting<Double> serverNotRespondingSecs = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("seconds-not-responding")
            .description("The amount of seconds the server is not responding before you log out.")
            .defaultValue(10.0)
            .min(1.0)
            .sliderMax(60.0)
            .visible(this.serverNotResponding::get)
            .build()
      );
   private final Setting<Boolean> reconnectAfterNotResponding = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("reconnect-after-not-responding")
            .description("Reconnects to the server after logging out because it was not responding.")
            .defaultValue(false)
            .visible(this.serverNotResponding::get)
            .build()
      );
   private final Setting<Double> secondsToReconnect = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("reconnect-seconds")
            .description("The amount of seconds to wait before reconnecting.")
            .defaultValue(60.0)
            .min(10.0)
            .sliderMax(300.0)
            .visible(() -> this.reconnectAfterNotResponding.get() && this.serverNotResponding.get())
            .build()
      );
   private final Setting<Boolean> illegalDisconnect = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("illegal-disconnect")
            .description("Disconnects from the server using the illegal-character chat message method.")
            .defaultValue(false)
            .build()
      );
   private final SettingGroup sgHealth = this.settings.createGroup("Health");
   private final Setting<Boolean> logHealth = this.sgHealth
      .add(
         new BoolSetting.Builder().name("log-on-health").description("Disconnects when your health falls to or below a threshold.").defaultValue(false).build()
      );
   private final Setting<Double> healthThreshold = this.sgHealth
      .add(
         new DoubleSetting.Builder()
            .name("health")
            .description("Disconnect when health is at or below this value.")
            .defaultValue(6.0)
            .range(0.0, 19.0)
            .sliderRange(0.0, 19.0)
            .visible(this.logHealth::get)
            .build()
      );
   private final Setting<Boolean> healthTotemCheck = this.sgHealth
      .add(
         new BoolSetting.Builder()
            .name("totem-check")
            .description("Only health-log when you hold fewer totems than the limit.")
            .defaultValue(true)
            .visible(this.logHealth::get)
            .build()
      );
   private final Setting<Integer> totemLimit = this.sgHealth
      .add(
         new IntSetting.Builder()
            .name("totem-limit")
            .description("Health disconnect only runs if you hold fewer totems than this.")
            .defaultValue(1)
            .range(0, 16)
            .sliderRange(0, 16)
            .visible(() -> this.logHealth.get() && this.healthTotemCheck.get())
            .build()
      );
   private final SettingGroup sgCrystal = this.settings.createGroup("Lethal Crystal");
   private final Setting<Boolean> logCrystal = this.sgCrystal
      .add(
         new BoolSetting.Builder()
            .name("log-on-lethal-crystal")
            .description("Disconnects when a nearby end crystal could instantly kill you.")
            .defaultValue(false)
            .build()
      );
   private final Setting<Boolean> crystalHoldingTotem = this.sgCrystal
      .add(
         new BoolSetting.Builder()
            .name("skip-if-holding-totem")
            .description("Skips lethal crystal logouts if you are holding a totem.")
            .defaultValue(true)
            .visible(this.logCrystal::get)
            .build()
      );
   private final SettingGroup sgPing = this.settings.createGroup("High Ping");
   private final Setting<Boolean> logPing = this.sgPing
      .add(new BoolSetting.Builder().name("log-on-high-ping").description("Disconnects when your ping exceeds a threshold.").defaultValue(false).build());
   private final Setting<Integer> pingThreshold = this.sgPing
      .add(
         new IntSetting.Builder()
            .name("ping-threshold")
            .description("Ping (ms) that triggers a logout when exceeded.")
            .defaultValue(1500)
            .range(200, 5000)
            .sliderRange(200, 5000)
            .visible(this.logPing::get)
            .build()
      );
   private final SettingGroup sgManual = this.settings.createGroup("Manual");
   private final Setting<Keybind> disconnectBind = this.sgManual
      .add(
         new KeybindSetting.Builder()
            .name("disconnect-bind")
            .description("Press to immediately disconnect.")
            .defaultValue(Keybind.none())
            .action(this::manualDisconnect)
            .build()
      );
   private int currPortalTicks = 0;
   private boolean waitingForReconnection = false;

   public AutoLogPlus() {
      super(Categories.Player, "auto-log-plus", "Provides some additional triggers to log out.");
   }

   @Override
   public void onActivate() {
      this.currPortalTicks = 0;
      this.waitingForReconnection = false;
   }

   @EventHandler
   private void onTick(TickEvent.Post event) {
      if (this.mc.player != null && !this.mc.player.getAbilities().allowFlying) {
         if (this.serverNotResponding.get() && !this.waitingForReconnection && TickRate.INSTANCE.getTimeSinceLastTick() > this.serverNotRespondingSecs.get()) {
            if (this.reconnectAfterNotResponding.get()) {
               this.scheduleReconnect();
            }

            this.logOut("Server was not responding for " + this.serverNotRespondingSecs.get() + " seconds.");
         } else {
            if (this.logPortal.get() && this.mc.player.portalManager != null) {
               if (this.mc.player.portalManager.isInPortal()) {
                  this.currPortalTicks++;
                  if (this.currPortalTicks > this.portalTicks.get()) {
                     this.logOut("Player was in a portal for " + this.currPortalTicks + " ticks.");
                     return;
                  }
               } else {
                  this.currPortalTicks = 0;
               }
            }

            if (this.logOnY.get() && this.mc.player.getY() < this.yLevel.get()) {
               this.logOut("Player was at Y=" + this.mc.player.getY() + " which is below your limit of Y=" + this.yLevel.get());
            } else {
               if (this.logArmor.get()) {
                  for (int i = 0; i < 4; i++) {
                     ItemStack armorPiece = this.mc.player.getInventory().getStack(36 + i);
                     if ((!this.ignoreElytra.get() || armorPiece.getItem() != Items.ELYTRA) && armorPiece.isDamageable()) {
                        int max = armorPiece.getMaxDamage();
                        int current = armorPiece.getDamage();
                        double percentUndamaged = 100.0 - (double)current / max * 100.0;
                        if (percentUndamaged < this.armorPercent.get()) {
                           this.logOut("You had low armor");
                           return;
                        }
                     }
                  }
               }

               if (this.logPosition.get()) {
                  double distanceToTarget = this.mc
                     .player
                     .getEntityPos()
                     .multiply(1.0, 0.0, 1.0)
                     .distanceTo(this.position.get().toCenterPos().multiply(1.0, 0.0, 1.0));
                  if (distanceToTarget < this.distance.get()) {
                     this.logOut("Player was within " + distanceToTarget + " blocks of the target position.");
                     return;
                  }
               }

               float health = this.mc.player.getHealth() + this.mc.player.getAbsorptionAmount();
               if (!this.logHealth.get()
                  || !(health <= this.healthThreshold.get())
                  || this.healthTotemCheck.get() && this.countTotems() >= this.totemLimit.get()) {
                  if (this.logCrystal.get() && (!this.crystalHoldingTotem.get() || !this.isHoldingTotem())) {
                     for (Entity entity : this.mc.world.getEntities()) {
                        if (entity instanceof EndCrystalEntity crystal && !(this.mc.player.squaredDistanceTo(crystal) > 144.0)) {
                           float damage = DamageUtil.crystalDamage(this.mc.player, crystal.getEntityPos());
                           if (damage >= health) {
                              this.logOut(String.format("Nearby crystal could deal %.1f damage.", damage));
                              return;
                           }
                        }
                     }
                  }

                  if (this.logPing.get() && this.mc.getNetworkHandler() != null) {
                     PlayerListEntry entry = this.mc.getNetworkHandler().getPlayerListEntry(this.mc.player.getUuid());
                     if (entry != null && entry.getLatency() > this.pingThreshold.get()) {
                        this.logOut("Ping exceeded " + this.pingThreshold.get() + " ms (was " + entry.getLatency() + " ms).");
                        return;
                     }
                  }
               } else {
                  this.logOut("Health was at or below " + this.healthThreshold.get() + ".");
               }
            }
         }
      }
   }

   private void manualDisconnect() {
      if (this.mc.player != null) {
         this.mc.player.networkHandler.onDisconnect(new DisconnectS2CPacket(Text.literal("[AutoLogPlus] Manual disconnect.")));
      }
   }

   private int countTotems() {
      if (this.mc.player == null) {
         return 0;
      } else {
         int count = 0;

         for (int i = 0; i < this.mc.player.getInventory().size(); i++) {
            if (this.mc.player.getInventory().getStack(i).isOf(Items.TOTEM_OF_UNDYING)) {
               count += this.mc.player.getInventory().getStack(i).getCount();
            }
         }

         return count;
      }
   }

   private boolean isHoldingTotem() {
      return this.mc.player != null
         && (this.mc.player.getMainHandStack().isOf(Items.TOTEM_OF_UNDYING) || this.mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING));
   }

   private void scheduleReconnect() {
      ServerInfo server = this.mc.getCurrentServerEntry();
      if (server != null) {
         this.waitingForReconnection = true;
         long delayMs = (long)(this.secondsToReconnect.get() * 1000.0);
         Thread thread = new Thread(() -> {
            try {
               Thread.sleep(delayMs);
            } catch (InterruptedException var5) {
            }

            this.mc.execute(() -> {
               this.waitingForReconnection = false;
               if (this.mc.world == null) {
                  ConnectScreen.connect(new TitleScreen(), this.mc, ServerAddress.parse(server.address), server, false, null);
               }
            });
         }, "AutoLogPlus-reconnect");
         thread.setDaemon(true);
         thread.start();
      }
   }

   private void logOut(String reason) {
      if (this.mc.player != null) {
         if (this.illegalDisconnect.get()) {
            this.mc.player.networkHandler.sendChatMessage(String.valueOf('\u0000'));
         } else {
            this.mc.player.networkHandler.onDisconnect(new DisconnectS2CPacket(Text.literal("[AutoLogPlus] " + reason)));
         }
      }
   }
}
