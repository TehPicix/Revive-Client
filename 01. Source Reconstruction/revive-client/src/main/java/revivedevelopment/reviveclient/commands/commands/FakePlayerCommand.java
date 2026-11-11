package revivedevelopment.reviveclient.commands.commands;

import java.util.concurrent.ThreadLocalRandom;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.commands.Command;
import revivedevelopment.reviveclient.utils.entity.fakeplayer.FakePlayerEntity;
import revivedevelopment.reviveclient.utils.entity.fakeplayer.FakePlayerManager;

public class FakePlayerCommand extends Command {
   public FakePlayerCommand() {
      super("fakeplayer", "Manages fake players you can use for testing.", "fp");
   }

   @Override
   public void execute(String[] args) {
      if (ReviveClient.mc.world != null && ReviveClient.mc.player != null) {
         if (args.length == 0) {
            if (FakePlayerManager.count() > 0) {
               FakePlayerManager.clear();
               this.info("Cleared all fake players.", new Object[0]);
            } else {
               String name = "FakePlayer" + ThreadLocalRandom.current().nextInt(100, 1000);
               FakePlayerManager.add(name, 20.0F, true);
               this.info("Added %s.", new Object[]{name});
            }
         } else {
            String name = args[0].toLowerCase();
            switch (name) {
               case "add": {
                  String namexx = args.length >= 2 ? args[1] : ReviveClient.mc.player.getGameProfile().name();
                  FakePlayerManager.add(namexx, 20.0F, true);
                  this.info("Added %s.", new Object[]{namexx});
                  break;
               }
               case "weak": {
                  String namex = args.length >= 2 ? args[1] : "Weak" + ThreadLocalRandom.current().nextInt(100, 1000);
                  FakePlayerManager.add(namex, 20.0F, true, true);
                  this.info("Added weak %s.", new Object[]{namex});
                  break;
               }
               case "del":
               case "delete":
               case "remove":
               case "rm":
                  if (args.length < 2) {
                     this.error("Usage: .fakeplayer del <name>", new Object[0]);
                     return;
                  }

                  FakePlayerEntity fp = FakePlayerManager.get(args[1]);
                  if (fp == null || !FakePlayerManager.contains(fp)) {
                     this.error("Couldn't find a fake player with that name.", new Object[0]);
                     return;
                  }

                  FakePlayerManager.remove(fp);
                  this.info("Removed %s.", new Object[]{fp.getName().getString()});
                  break;
               case "damage":
               case "dmg":
                  if (args.length < 2) {
                     FakePlayerManager.takeDamage = !FakePlayerManager.takeDamage;
                  } else {
                     String v = args[1].toLowerCase();
                     FakePlayerManager.takeDamage = v.equals("on") || v.equals("true") || v.equals("1");
                  }

                  this.info("Fake players take damage: %s.", new Object[]{FakePlayerManager.takeDamage ? "ON" : "OFF"});
                  break;
               case "clear":
                  FakePlayerManager.clear();
                  this.info("Cleared all fake players.", new Object[0]);
                  break;
               case "list":
               case "ls":
                  this.info("--- Fake Players (%d) ---", new Object[]{FakePlayerManager.count()});
                  FakePlayerManager.forEach(listed -> this.info(listed.getName().getString(), new Object[0]));
                  break;
               default:
                  this.error("Unknown subcommand. Usage: .fakeplayer [add|weak|damage|del|clear|list]", new Object[0]);
            }
         }
      }
   }
}
