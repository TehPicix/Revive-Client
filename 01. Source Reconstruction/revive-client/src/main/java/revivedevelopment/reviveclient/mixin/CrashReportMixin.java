package revivedevelopment.reviveclient.mixin;

import java.util.List;
import net.minecraft.util.crash.CrashReport;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.systems.hud.Hud;
import revivedevelopment.reviveclient.systems.hud.HudElement;
import revivedevelopment.reviveclient.systems.hud.elements.TextHud;
import revivedevelopment.reviveclient.systems.modules.Category;
import revivedevelopment.reviveclient.systems.modules.Module;
import revivedevelopment.reviveclient.systems.modules.Modules;

@Mixin({CrashReport.class})
public abstract class CrashReportMixin {
   @Inject(
      method = {"method_555"},
      at = {@At("TAIL")}
   )
   private void onAddDetails(StringBuilder sb, CallbackInfo info) {
      sb.append("\n\n-- Revive Client --\n\n");
      sb.append("Version: ").append(ReviveClient.VERSION).append("\n");
      if (!ReviveClient.BUILD_NUMBER.isEmpty()) {
         sb.append("Build: ").append(ReviveClient.BUILD_NUMBER).append("\n");
      }

      if (Modules.get() != null) {
         boolean modulesActive = false;

         for (Category category : Modules.loopCategories()) {
            List<Module> modules = Modules.get().getGroup(category);
            boolean categoryActive = false;

            for (Module module : modules) {
               if (module != null && module.isActive()) {
                  if (!modulesActive) {
                     modulesActive = true;
                     sb.append("\n[[ Active Modules ]]\n");
                  }

                  if (!categoryActive) {
                     categoryActive = true;
                     sb.append("\n[").append(category).append("]:\n");
                  }

                  sb.append(module.name).append("\n");
               }
            }
         }
      }

      if (Hud.get() != null && Hud.get().active) {
         boolean hudActive = false;

         for (HudElement element : Hud.get()) {
            if (element != null && element.isActive()) {
               if (!hudActive) {
                  hudActive = true;
                  sb.append("\n[[ Active Hud Elements ]]\n");
               }

               if (element instanceof TextHud textHud) {
                  sb.append("Text\n{").append(textHud.text.get()).append("}\n");
                  if (textHud.shown.get() != TextHud.Shown.Always) {
                     sb.append("(").append(textHud.shown.get()).append(textHud.condition.get()).append(")\n");
                  }
               } else {
                  sb.append(element.info.name).append("\n");
               }
            }
         }
      }
   }
}
