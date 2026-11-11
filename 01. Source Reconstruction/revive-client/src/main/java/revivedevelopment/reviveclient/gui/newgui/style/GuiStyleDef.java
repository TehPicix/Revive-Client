package revivedevelopment.reviveclient.gui.newgui.style;

import net.minecraft.nbt.NbtCompound;
import revivedevelopment.reviveclient.systems.modules.gui.Gui;

public final class GuiStyleDef {
   public String name;
   public final boolean builtin;
   public Gui.GuiStyle layout;
   public NbtCompound settings;

   private GuiStyleDef(String name, boolean builtin, Gui.GuiStyle layout, NbtCompound settings) {
      this.name = name;
      this.builtin = builtin;
      this.layout = layout;
      this.settings = settings;
   }

   public static GuiStyleDef builtin(String name, Gui.GuiStyle layout) {
      return new GuiStyleDef(name, true, layout, null);
   }

   public static GuiStyleDef custom(String name, Gui.GuiStyle layout, NbtCompound settings) {
      return new GuiStyleDef(name, false, layout, settings);
   }

   public GuiStyleDef copyAs(String newName) {
      NbtCompound s = this.settings == null ? null : this.settings.copy();
      return new GuiStyleDef(newName, false, this.layout, s);
   }

   public NbtCompound toTag() {
      NbtCompound t = new NbtCompound();
      t.putString("name", this.name);
      t.putString("layout", this.layout.name());
      if (this.settings != null) {
         t.put("settings", this.settings);
      }

      return t;
   }

   public static GuiStyleDef fromTag(NbtCompound t) {
      String name = t.getString("name", "Style");
      Gui.GuiStyle layout = parseLayout(t.getString("layout", Gui.GuiStyle.Modern.name()));
      NbtCompound settings = t.getCompound("settings").orElse(new NbtCompound());
      return custom(name, layout, settings);
   }

   private static Gui.GuiStyle parseLayout(String s) {
      try {
         return Gui.GuiStyle.valueOf(s);
      } catch (IllegalArgumentException var2) {
         return Gui.GuiStyle.Modern;
      }
   }
}
