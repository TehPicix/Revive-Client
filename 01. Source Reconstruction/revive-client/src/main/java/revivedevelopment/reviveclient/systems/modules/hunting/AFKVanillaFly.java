package revivedevelopment.reviveclient.systems.modules.hunting;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.Items;
import revivedevelopment.reviveclient.events.world.TickEvent;
import revivedevelopment.reviveclient.mixin.LivingEntityAccessor;
import revivedevelopment.reviveclient.settings.BoolSetting;
import revivedevelopment.reviveclient.settings.IntSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.systems.managers.ElytraDurabilitySaver;
import revivedevelopment.reviveclient.systems.modules.Categories;
import revivedevelopment.reviveclient.systems.modules.Module;
import revivedevelopment.reviveclient.utils.player.FindItemResult;
import revivedevelopment.reviveclient.utils.player.InvUtils;

public class AFKVanillaFly extends Module {
   private long lastRocketUse = 0L;
   private boolean launched = false;
   private double yTarget = -1.0;
   private float targetPitch = 0.0F;
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Boolean> saveDurability = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("save-durability")
            .description(
               "Skip elytra durability while gliding: right before each durability tick a hotbar chestplate is swapped into the chest slot server-side, then the elytra is swapped back and the wings re-opened. The client keeps gliding the whole time. Needs a chestplate in the hotbar; the anti-cheat config's elytra-saver settings choose the method."
            )
            .defaultValue(false)
            .build()
      );
   private final Setting<Integer> fireworkDelay = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("timed-delay")
            .description("The delay between firework usages in milliseconds.")
            .defaultValue(4000)
            .min(0)
            .sliderRange(0, 10000)
            .build()
      );
   private final Setting<Boolean> useManualY = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("use-manual-y-level")
            .description("Hold a manually set Y level instead of the Y level you had when the module was activated.")
            .defaultValue(false)
            .onChanged(val -> this.launched = false)
            .build()
      );
   private final Setting<Integer> manualYLevel = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("manual-y-level")
            .description("The Y level to maintain when using manual Y level.")
            .defaultValue(256)
            .min(-64)
            .max(320)
            .sliderRange(-64, 320)
            .visible(this.useManualY::get)
            .onChanged(val -> {
               if (this.useManualY.get()) {
                  this.yTarget = val.intValue();
               }
            })
            .build()
      );

   public AFKVanillaFly() {
      super(
         Categories.Movement,
         "afk-vanilla-fly",
         "Maintains a level Y-flight with fireworks and smooth pitch control (replaces RocketFly). TrailFollower drives this in the overworld."
      );
      ElytraDurabilitySaver.get().register(this::isActive, this.saveDurability::get);
   }

   @Override
   public void onActivate() {
      this.launched = false;
      this.yTarget = -1.0;
      if (this.mc.player == null || !this.mc.player.isGliding()) {
         this.info("You must be flying before enabling AFKVanillaFly.", new Object[0]);
      }
   }

   public void tickFlyLogic() {
      if (this.mc.player != null) {
         double currentY = this.mc.player.getY();
         if (this.mc.player.isGliding()) {
            if (this.yTarget == -1.0 || !this.launched) {
               if (this.useManualY.get()) {
                  this.yTarget = this.manualYLevel.get().intValue();
               } else {
                  this.yTarget = currentY;
               }

               this.launched = true;
            }

            if (!this.useManualY.get()) {
               double yDiffFromLock = currentY - this.yTarget;
               if (Math.abs(yDiffFromLock) > 10.0) {
                  this.yTarget = currentY;
                  this.info("Y-lock reset due to altitude deviation.", new Object[0]);
               }
            }

            double yDiff = currentY - this.yTarget;
            if (Math.abs(yDiff) > 10.0) {
               this.targetPitch = (float)(Math.atan2(yDiff, 100.0) * (180.0 / Math.PI));
            } else if (yDiff > 2.0) {
               this.targetPitch = 10.0F;
            } else if (yDiff < -2.0) {
               this.targetPitch = -10.0F;
            } else {
               this.targetPitch = 0.0F;
            }

            float currentPitch = this.mc.player.getPitch();
            float pitchDiff = this.targetPitch - currentPitch;
            this.mc.player.setPitch(currentPitch + pitchDiff * 0.1F);
            if (System.currentTimeMillis() - this.lastRocketUse > this.fireworkDelay.get().intValue()) {
               this.tryUseFirework();
            }
         } else {
            if (!this.launched) {
               ((LivingEntityAccessor)this.mc.player).revive$jump();
               this.launched = true;
            } else if (System.currentTimeMillis() - this.lastRocketUse > 1000L) {
               this.tryUseFirework();
            }

            this.yTarget = -1.0;
         }
      }
   }

   public void resetYLock() {
      this.yTarget = -1.0;
      this.launched = false;
   }

   @EventHandler
   private void onTick(TickEvent.Pre event) {
      this.tickFlyLogic();
   }

   private void tryUseFirework() {
      FindItemResult hotbar = InvUtils.findInHotbar(Items.FIREWORK_ROCKET);
      if (!hotbar.found()) {
         FindItemResult inv = InvUtils.find(Items.FIREWORK_ROCKET);
         if (!inv.found()) {
            this.info("No fireworks found in hotbar or inventory.", new Object[0]);
            this.lastRocketUse = System.currentTimeMillis();
            return;
         }

         int hotbarSlot = this.findEmptyHotbarSlot();
         if (hotbarSlot == -1) {
            this.info("No empty hotbar slot available to move fireworks.", new Object[0]);
            this.lastRocketUse = System.currentTimeMillis();
            return;
         }

         InvUtils.move().from(inv.slot()).toHotbar(hotbarSlot);
      }

      if (HuntingUtils.firework(this.mc, false) != -2) {
         this.lastRocketUse = System.currentTimeMillis();
      }
   }

   private int findEmptyHotbarSlot() {
      for (int i = 0; i < 9; i++) {
         if (this.mc.player.getInventory().getStack(i).isEmpty()) {
            return i;
         }
      }

      return -1;
   }
}
