package revivedevelopment.reviveclient.systems.modules.player;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BedBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.RespawnAnchorBlock;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.util.math.BlockPos;
import revivedevelopment.reviveclient.events.packets.PacketEvent;
import revivedevelopment.reviveclient.settings.BoolSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.systems.modules.Categories;
import revivedevelopment.reviveclient.systems.modules.Module;

public class AntiSpawnSet extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Boolean> blockBeds = this.sgGeneral
      .add(new BoolSetting.Builder().name("block-beds").description("Prevents setting spawn on beds.").defaultValue(true).build());
   private final Setting<Boolean> blockAnchors = this.sgGeneral
      .add(new BoolSetting.Builder().name("block-anchors").description("Prevents setting spawn on respawn anchors.").defaultValue(true).build());
   private final Setting<Boolean> notify = this.sgGeneral
      .add(new BoolSetting.Builder().name("notify").description("Sends a chat message when a spawn set attempt is blocked.").defaultValue(true).build());

   public AntiSpawnSet() {
      super(Categories.Player, "anti-spawn-set", "Prevents setting your spawn point on beds or respawn anchors.");
   }

   @EventHandler
   private void onSendPacket(PacketEvent.Send event) {
      if (this.mc.player != null && this.mc.world != null) {
         if (event.packet instanceof PlayerInteractBlockC2SPacket packet) {
            BlockPos pos = packet.getBlockHitResult().getBlockPos();
            BlockState state = this.mc.world.getBlockState(pos);
            if (this.blockBeds.get() && state.getBlock() instanceof BedBlock) {
               event.cancel();
               if (this.notify.get()) {
                  this.info("Blocked bed spawn set.", new Object[0]);
               }
            }

            if (this.blockAnchors.get() && state.getBlock() instanceof RespawnAnchorBlock) {
               event.cancel();
               if (this.notify.get()) {
                  this.info("Blocked respawn anchor interaction.", new Object[0]);
               }
            }
         }
      }
   }
}
