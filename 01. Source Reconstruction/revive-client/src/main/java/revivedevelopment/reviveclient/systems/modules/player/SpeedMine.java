package revivedevelopment.reviveclient.systems.modules.player;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.BlockPos;
import revivedevelopment.reviveclient.events.entity.player.BlockBreakingCooldownEvent;
import revivedevelopment.reviveclient.events.world.TickEvent;
import revivedevelopment.reviveclient.mixin.ClientPlayerInteractionManagerAccessor;
import revivedevelopment.reviveclient.settings.BoolSetting;
import revivedevelopment.reviveclient.settings.IntSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.systems.modules.Categories;
import revivedevelopment.reviveclient.systems.modules.Module;

public class SpeedMine extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Integer> speed = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("speed")
            .description("Minimum break progress while mining, as a percentage of the 0.3 progress floor.")
            .defaultValue(15)
            .range(0, 100)
            .sliderRange(0, 100)
            .build()
      );
   private final Setting<Integer> delay = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("delay")
            .description("Extra cooldown ticks between breaking blocks; the cooldown becomes delay + 1 (vanilla is 5).")
            .defaultValue(0)
            .range(0, 4)
            .sliderRange(0, 4)
            .build()
      );
   private final Setting<Boolean> strictServer = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("strict-server")
            .description(
               "Cap the boost so a block never finishes earlier than the server's own break-progress model allows. On servers that fully validate break progress (Grim / 2b2t), an early finish is rejected and the block re-syncs back — worst on fast blocks like stone/cobble, where mining ended up SLOWER than vanilla. The between-blocks delay cut is unaffected and is where the real speedup comes from there."
            )
            .defaultValue(true)
            .build()
      );

   public SpeedMine() {
      super(Categories.Player, "speed-mine", "Mines blocks faster by boosting break progress and cutting the break cooldown.");
   }

   @EventHandler
   private void onTick(TickEvent.Pre event) {
      if (this.canApply()) {
         if (this.mc.interactionManager.isBreakingBlock()) {
            ClientPlayerInteractionManagerAccessor accessor = (ClientPlayerInteractionManagerAccessor)this.mc.interactionManager;
            float damage = 0.3F * (this.speed.get().intValue() / 100.0F);
            if (this.strictServer.get()) {
               BlockPos pos = accessor.getCurrentBreakingBlockPos();
               BlockState state = this.mc.world.getBlockState(pos);
               float delta = state.calcBlockBreakingDelta(this.mc.player, this.mc.world, pos);
               if (delta <= 0.0F) {
                  return;
               }

               if (delta < 1.0F) {
                  int serverTicks = (int)Math.ceil(1.0 / delta);
                  float maxSafe = 1.0F - delta * (serverTicks - 1) - 0.01F;
                  damage = Math.min(damage, Math.max(0.0F, maxSafe));
               }
            }

            if (accessor.getBreakingProgress() < damage) {
               accessor.setCurrentBreakingProgress(damage);
            }
         }
      }
   }

   @EventHandler
   private void onBlockBreakingCooldown(BlockBreakingCooldownEvent event) {
      if (this.canApply()) {
         event.cooldown = Math.min(event.cooldown, this.delay.get() + 1);
      }
   }

   private boolean canApply() {
      if (this.mc.player == null || this.mc.world == null || this.mc.interactionManager == null) {
         return false;
      } else {
         return this.mc.player.isCreative() ? false : this.mc.crosshairTarget != null && this.mc.crosshairTarget.getType() == Type.BLOCK;
      }
   }

   @Override
   public String getInfoString() {
      return this.speed.get() + "%";
   }
}
