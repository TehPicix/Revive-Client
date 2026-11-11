package revivedevelopment.reviveclient.commands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import revivedevelopment.reviveclient.commands.commands.ConfigCommand;
import revivedevelopment.reviveclient.commands.commands.EmoteCommand;
import revivedevelopment.reviveclient.commands.commands.FakePlayerCommand;
import revivedevelopment.reviveclient.commands.commands.FriendCommand;
import revivedevelopment.reviveclient.commands.commands.NbtCommand;
import revivedevelopment.reviveclient.commands.commands.SurfaceCommand;
import revivedevelopment.reviveclient.commands.commands.TeamCommand;
import revivedevelopment.reviveclient.utils.player.ChatUtils;

public class Commands {
   private static final Commands INSTANCE = new Commands();
   private final List<Command> commands = new ArrayList<>();

   public static Commands get() {
      return INSTANCE;
   }

   private Commands() {
      this.register(new ConfigCommand());
      this.register(new EmoteCommand());
      this.register(new FakePlayerCommand());
      this.register(new FriendCommand());
      this.register(new NbtCommand());
      this.register(new SurfaceCommand());
      this.register(new TeamCommand());
   }

   public void register(Command command) {
      this.commands.add(command);
   }

   public List<Command> getCommands() {
      return this.commands;
   }

   public Command get(String name) {
      for (Command command : this.commands) {
         if (command.getName().equalsIgnoreCase(name) || command.getAliases().contains(name.toLowerCase())) {
            return command;
         }
      }

      return null;
   }

   public boolean dispatch(String input) {
      String stripped = input.trim();
      if (stripped.isEmpty()) {
         return true;
      } else {
         String[] parts = stripped.split("\\s+");
         String name = parts[0].toLowerCase();
         String[] args = parts.length > 1 ? Arrays.copyOfRange(parts, 1, parts.length) : new String[0];
         Command command = this.get(name);
         if (command == null) {
            ChatUtils.error("Unknown command: %s", name);
            return true;
         } else {
            try {
               command.execute(args);
            } catch (Exception var8) {
               ChatUtils.error("Error running command '%s': %s", name, var8.getMessage());
            }

            return true;
         }
      }
   }
}
