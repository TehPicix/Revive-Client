package revivedevelopment.reviveclient.commands;

import java.util.Arrays;
import java.util.List;
import revivedevelopment.reviveclient.utils.player.ChatUtils;

public abstract class Command {
   private final String name;
   private final String description;
   private final List<String> aliases;

   protected Command(String name, String description, String... aliases) {
      this.name = name;
      this.description = description;
      this.aliases = Arrays.asList(aliases);
   }

   public String getName() {
      return this.name;
   }

   public String getDescription() {
      return this.description;
   }

   public List<String> getAliases() {
      return this.aliases;
   }

   public abstract void execute(String[] var1);

   protected void info(String message, Object... args) {
      ChatUtils.infoPrefix(this.title(), message, args);
   }

   protected void warning(String message, Object... args) {
      ChatUtils.warningPrefix(this.title(), message, args);
   }

   protected void error(String message, Object... args) {
      ChatUtils.errorPrefix(this.title(), message, args);
   }

   private String title() {
      return this.name.isEmpty() ? this.name : Character.toUpperCase(this.name.charAt(0)) + this.name.substring(1);
   }
}
