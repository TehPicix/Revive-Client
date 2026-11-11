package revivedevelopment.reviveclient.commands.commands;

import java.util.Arrays;
import java.util.Locale;
import revivedevelopment.reviveclient.commands.Command;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.systems.config.Config;

public class ConfigCommand extends Command {
   public ConfigCommand() {
      super("config", "Shows or changes the client-wide settings (prefix, unsigned-chat, ...).", "cfg");
   }

   @Override
   public void execute(String[] args) {
      Config config = Config.get();
      if (config == null) {
         this.error("Config isn't loaded yet.", new Object[0]);
      } else if (args.length != 0) {
         Setting<?> setting = config.settings.get(args[0]);
         if (setting == null) {
            this.error("No setting called '%s'. Run .config to list them.", new Object[]{args[0]});
         } else if (args.length == 1) {
            this.info("%s: (highlight)%s(default) - %s", new Object[]{setting.name, setting.get(), setting.description});
         } else {
            String value = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
            String v = value.toLowerCase(Locale.ROOT);
            if (v.equals("on")) {
               value = "true";
            } else if (v.equals("off")) {
               value = "false";
            }

            if (!setting.parse(value)) {
               this.error("'%s' isn't a valid value for %s.", new Object[]{value, setting.name});
            } else {
               config.save();
               this.info("%s set to (highlight)%s", new Object[]{setting.name, setting.get()});
            }
         }
      } else {
         for (SettingGroup group : config.settings) {
            for (Setting<?> s : group) {
               this.info("%s: (highlight)%s", new Object[]{s.name, s.get()});
            }
         }

         this.info("Change one with .config <setting> <value>", new Object[0]);
      }
   }
}
