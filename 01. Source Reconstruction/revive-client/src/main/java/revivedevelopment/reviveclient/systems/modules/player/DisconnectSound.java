package revivedevelopment.reviveclient.systems.modules.player;

import java.util.List;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import revivedevelopment.reviveclient.events.game.OpenScreenEvent;
import revivedevelopment.reviveclient.gui.GuiTheme;
import revivedevelopment.reviveclient.gui.widgets.WWidget;
import revivedevelopment.reviveclient.gui.widgets.pressable.WButton;
import revivedevelopment.reviveclient.settings.DoubleSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.settings.SoundEventListSetting;
import revivedevelopment.reviveclient.systems.modules.Categories;
import revivedevelopment.reviveclient.systems.modules.Module;

public class DisconnectSound extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<List<SoundEvent>> sound = this.sgGeneral
      .add(
         new SoundEventListSetting.Builder()
            .name("sound")
            .description("Sound to play.")
            .defaultValue(List.of((SoundEvent)SoundEvents.BLOCK_NOTE_BLOCK_PLING.value()))
            .build()
      );
   private final Setting<Double> soundPitch = this.sgGeneral
      .add(new DoubleSetting.Builder().name("sound-pitch").description("Pitch of the sound.").defaultValue(0.5).min(0.0).sliderRange(0.5, 2.0).build());
   private final Setting<Double> soundVolume = this.sgGeneral
      .add(new DoubleSetting.Builder().name("sound-volume").description("Volume of the sound.").defaultValue(1.0).min(0.0).sliderRange(0.0, 1.0).build());

   public DisconnectSound() {
      super(Categories.Player, "disconnect-sound", "Plays a sound when the Disconnected screen appears (e.g. when kicked).");
   }

   private void playSound() {
      if (!this.sound.get().isEmpty()) {
         this.mc
            .getSoundManager()
            .play(PositionedSoundInstance.master(this.sound.get().getFirst(), this.soundPitch.get().floatValue(), this.soundVolume.get().floatValue()));
      }
   }

   @Override
   public WWidget getWidget(GuiTheme theme) {
      WButton button = theme.button("Preview");
      button.action = this::playSound;
      return button;
   }

   @EventHandler
   private void onOpenScreen(OpenScreenEvent event) {
      if (event.screen instanceof DisconnectedScreen) {
         this.playSound();
      }
   }
}
