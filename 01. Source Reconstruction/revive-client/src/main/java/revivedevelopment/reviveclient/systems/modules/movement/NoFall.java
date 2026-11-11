package revivedevelopment.reviveclient.systems.modules.movement;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import revivedevelopment.reviveclient.events.packets.PacketEvent;
import revivedevelopment.reviveclient.mixin.PlayerMoveC2SPacketAccessor;
import revivedevelopment.reviveclient.settings.BoolSetting;
import revivedevelopment.reviveclient.settings.DoubleSetting;
import revivedevelopment.reviveclient.settings.EnumSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.systems.modules.Categories;
import revivedevelopment.reviveclient.systems.modules.Module;

public class NoFall extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<NoFall.Mode> mode = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("mode"))
                  .description("When to spoof the on-ground flag."))
               .defaultValue(NoFall.Mode.OnFall))
            .build()
      );
   private final Setting<Double> minFall = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("min-fall-distance")
            .description("Fall distance from which packets get spoofed.")
            .defaultValue(3.0)
            .min(0.0)
            .sliderRange(0.0, 10.0)
            .visible(() -> this.mode.get() == NoFall.Mode.OnFall)
            .build()
      );
   private final Setting<Boolean> pauseOnElytra = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("pause-on-elytra")
            .description("Doesn't spoof packets while gliding, since landing resets fall damage anyway.")
            .defaultValue(true)
            .build()
      );

   public NoFall() {
      super(Categories.Movement, "no-fall", "Prevents fall damage by spoofing the on-ground flag.");
   }

   @EventHandler
   private void onSendPacket(PacketEvent.Send event) {
      if (event.packet instanceof PlayerMoveC2SPacket packet) {
         if (this.mc.player != null && !this.mc.player.getAbilities().creativeMode) {
            if (!this.pauseOnElytra.get() || !this.mc.player.isGliding()) {
               if (this.mode.get() != NoFall.Mode.OnFall || !(this.mc.player.fallDistance <= this.minFall.get())) {
                  ((PlayerMoveC2SPacketAccessor)packet).setOnGround(true);
               }
            }
         }
      }
   }

   public static enum Mode {
      Always,
      OnFall;
   }
}
