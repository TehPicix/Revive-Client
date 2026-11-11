package revivedevelopment.reviveclient.systems.modules;

import net.minecraft.item.Items;
import revivedevelopment.reviveclient.addons.AddonManager;
import revivedevelopment.reviveclient.addons.ReviveAddon;

public class Categories {
   public static final Category Combat = new Category("Combat", Items.GOLDEN_SWORD.getDefaultStack());
   public static final Category Player = new Category("Player", Items.ARMOR_STAND.getDefaultStack());
   public static final Category Render = new Category("Render", Items.GLASS.getDefaultStack());
   public static final Category Movement = new Category("Movement", Items.DIAMOND_BOOTS.getDefaultStack());
   public static final Category Hunting = new Category("Hunting", Items.BOW.getDefaultStack());
   public static final Category Gui = new Category("Gui", Items.COMPASS.getDefaultStack());
   public static boolean REGISTERING;

   public static void init() {
      REGISTERING = true;
      Modules.registerCategory(Combat);
      Modules.registerCategory(Player);
      Modules.registerCategory(Render);
      Modules.registerCategory(Movement);
      Modules.registerCategory(Hunting);
      Modules.registerCategory(Gui);
      AddonManager.ADDONS.forEach(ReviveAddon::onRegisterCategories);
      REGISTERING = false;
   }
}
