package revivedevelopment.reviveclient.systems.config;

import revivedevelopment.reviveclient.settings.EnumSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.systems.modules.Categories;
import revivedevelopment.reviveclient.systems.modules.Module;
import revivedevelopment.reviveclient.systems.modules.Modules;

public class MultiTask extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<MultiTask.PhaseMode> phaseMode = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("phase"))
                  .description("How the Phase module brings a pearl into a hand before throwing."))
               .defaultValue(MultiTask.PhaseMode.Normal))
            .build()
      );
   private final Setting<MultiTask.PearlMode> pearlMode = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("pearl"))
                  .description("Shared swap mode for pearl-throwing features."))
               .defaultValue(MultiTask.PearlMode.AltSilent))
            .build()
      );
   private final Setting<MultiTask.KillAuraMode> killAuraMode = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("kill-aura"))
                  .description("Shared swap mode for aura / combat item use."))
               .defaultValue(MultiTask.KillAuraMode.None))
            .build()
      );

   public MultiTask() {
      super(Categories.Gui, "multi-task", "Shared multitask behavior for combat and mining modules.");
   }

   public static MultiTask get() {
      return Modules.get().get(MultiTask.class);
   }

   public static MultiTask.PhaseMode phaseMode() {
      AntiCheatConfig ac = AntiCheatConfig.get();
      if (ac != null) {
         return ac.phaseMode.get();
      } else {
         MultiTask mt = get();
         return mt != null ? mt.phaseMode.get() : MultiTask.PhaseMode.Normal;
      }
   }

   public static MultiTask.PearlMode pearlMode() {
      AntiCheatConfig ac = AntiCheatConfig.get();
      if (ac != null) {
         return ac.pearlMode.get();
      } else {
         MultiTask mt = get();
         return mt != null ? mt.pearlMode.get() : MultiTask.PearlMode.AltSilent;
      }
   }

   public static MultiTask.KillAuraMode killAuraMode() {
      AntiCheatConfig ac = AntiCheatConfig.get();
      if (ac != null) {
         return ac.killAuraMode.get();
      } else {
         MultiTask mt = get();
         return mt != null ? mt.killAuraMode.get() : MultiTask.KillAuraMode.None;
      }
   }

   public static enum KillAuraMode {
      AltSilent,
      Offhand,
      None;
   }

   public static enum PearlMode {
      Offhand,
      AltSilent,
      None;
   }

   public static enum PhaseMode {
      Normal,
      Offhand,
      AltSilent;
   }
}
