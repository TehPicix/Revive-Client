package revivedevelopment.reviveclient.systems.modules;

import java.util.Objects;
import net.minecraft.client.MinecraftClient;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.NotNull;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.addons.AddonManager;
import revivedevelopment.reviveclient.addons.ReviveAddon;
import revivedevelopment.reviveclient.gui.GuiTheme;
import revivedevelopment.reviveclient.gui.widgets.WWidget;
import revivedevelopment.reviveclient.pingbypass.PbRenderable;
import revivedevelopment.reviveclient.settings.Settings;
import revivedevelopment.reviveclient.systems.config.Config;
import revivedevelopment.reviveclient.utils.Utils;
import revivedevelopment.reviveclient.utils.misc.ISerializable;
import revivedevelopment.reviveclient.utils.misc.Keybind;
import revivedevelopment.reviveclient.utils.player.ChatUtils;
import revivedevelopment.reviveclient.utils.render.color.Color;

public abstract class Module implements ISerializable<Module>, Comparable<Module> {
   protected final MinecraftClient mc;
   public final Category category;
   public final String name;
   public final String title;
   public final String description;
   public final String[] aliases;
   public final Color color;
   public final ReviveAddon addon;
   public final Settings settings = new Settings();
   private boolean active;
   private boolean remote;
   public String remoteInfo;
   public boolean serialize = true;
   public boolean runInMainMenu = false;
   public boolean autoSubscribe = true;
   public final Keybind keybind = Keybind.none();
   public boolean toggleOnBindRelease = false;
   public boolean chatFeedback = true;
   public boolean favorite = false;

   public Module(Category category, String name, String description, String... aliases) {
      if (name.contains(" ")) {
         ReviveClient.LOG.warn("Module '{}' contains invalid characters in its name making it incompatible with Revive Client commands.", name);
      }

      this.mc = MinecraftClient.getInstance();
      this.category = category;
      this.name = name;
      this.title = Utils.nameToTitle(name);
      this.description = description;
      this.aliases = aliases;
      this.color = Color.fromHsv(Utils.random(0.0, 360.0), 0.35, 1.0);
      String classname = this.getClass().getName();

      for (ReviveAddon addon : AddonManager.ADDONS) {
         if (classname.startsWith(addon.getPackage())) {
            this.addon = addon;
            return;
         }
      }

      this.addon = null;
   }

   public Module(Category category, String name, String desc) {
      // NOTE (source reconstruction): disambiguated to explicitly call the varargs constructor
      // above. "this(category, name, desc)" is ambiguous here: javac always prefers the
      // non-varargs exact match, so it resolved to itself (recursive constructor invocation).
      this(category, name, desc, new String[0]);
   }

   public WWidget getWidget(GuiTheme theme) {
      return null;
   }

   public void onActivate() {
   }

   public void onDeactivate() {
   }

   public void toggle() {
      if (!this.active) {
         this.active = true;
         Modules.get().addActive(this);
         this.settings.onActivated();
         if (!this.remote && (this.runInMainMenu || Utils.canUpdate())) {
            if (this.autoSubscribe) {
               ReviveClient.EVENT_BUS.subscribe(this);
            }

            this.onActivate();
         }
      } else {
         if (!this.remote && (this.runInMainMenu || Utils.canUpdate())) {
            if (this.autoSubscribe) {
               ReviveClient.EVENT_BUS.unsubscribe(this);
            }

            this.onDeactivate();
         }

         this.active = false;
         Modules.get().removeActive(this);
      }
   }

   public void sendToggledMsg() {
      if (Config.get().chatFeedback.get() && this.chatFeedback) {
         ChatUtils.forceNextPrefixClass(this.getClass());
         ChatUtils.sendMsg(
            this.hashCode(), this.isActive() ? Formatting.GREEN : Formatting.RED, "%s (highlight)%s(default)", this.isActive() ? "+" : "-", this.title
         );
      }
   }

   public void info(Text message) {
      ChatUtils.forceNextPrefixClass(this.getClass());
      ChatUtils.sendMsg(this.title, message);
   }

   public void info(String message, Object... args) {
      ChatUtils.forceNextPrefixClass(this.getClass());
      ChatUtils.infoPrefix(this.title, message, args);
   }

   public void warning(String message, Object... args) {
      ChatUtils.forceNextPrefixClass(this.getClass());
      ChatUtils.warningPrefix(this.title, message, args);
   }

   public void error(String message, Object... args) {
      ChatUtils.forceNextPrefixClass(this.getClass());
      ChatUtils.errorPrefix(this.title, message, args);
   }

   public boolean isActive() {
      return this.active;
   }

   public boolean activeByDefault() {
      return false;
   }

   public String getInfoString() {
      return null;
   }

   public String getDisplayInfo() {
      return this.remote ? this.remoteInfo : this.getInfoString();
   }

   public boolean isRemote() {
      return this.remote;
   }

   public boolean isRunningHere() {
      return this.active && !this.remote;
   }

   public void setRemote(boolean remote, boolean lifecycle) {
      if (this.remote != remote) {
         boolean running = lifecycle && this.active && (this.runInMainMenu || Utils.canUpdate());
         if (remote) {
            if (running) {
               if (this.autoSubscribe) {
                  ReviveClient.EVENT_BUS.unsubscribe(this);
               }

               this.onDeactivate();
            }

            this.remote = true;
         } else {
            this.remote = false;
            this.remoteInfo = null;
            if (this instanceof PbRenderable renderable) {
               renderable.clearRemote();
            }

            if (running) {
               if (this.autoSubscribe) {
                  ReviveClient.EVENT_BUS.subscribe(this);
               }

               this.onActivate();
            }
         }
      }
   }

   public void setActiveFromRemote(boolean value) {
      if (this.remote && this.active != value) {
         this.active = value;
         if (value) {
            Modules.get().addActive(this);
         } else {
            Modules.get().removeActive(this);
         }
      }
   }

   @Override
   public NbtCompound toTag() {
      if (!this.serialize) {
         return null;
      } else {
         NbtCompound tag = new NbtCompound();
         tag.putString("name", this.name);
         tag.put("keybind", this.keybind.toTag());
         tag.putBoolean("toggleOnKeyRelease", this.toggleOnBindRelease);
         tag.putBoolean("chatFeedback", this.chatFeedback);
         tag.putBoolean("favorite", this.favorite);
         tag.put("settings", this.settings.toTag());
         tag.putBoolean("active", this.active);
         return tag;
      }
   }

   public Module fromTag(NbtCompound tag) {
      this.keybind.fromTag(tag.getCompoundOrEmpty("keybind"));
      this.toggleOnBindRelease = tag.getBoolean("toggleOnKeyRelease", false);
      this.chatFeedback = !tag.contains("chatFeedback") || tag.getBoolean("chatFeedback", false);
      this.favorite = tag.getBoolean("favorite", false);
      NbtElement settingsTag = tag.get("settings");
      if (settingsTag instanceof NbtCompound) {
         this.settings.fromTag((NbtCompound)settingsTag);
      }

      boolean active = tag.getBoolean("active", false);
      if (active != this.isActive()) {
         this.toggle();
      }

      return this;
   }

   @Override
   public boolean equals(Object o) {
      if (this == o) {
         return true;
      } else if (o != null && this.getClass() == o.getClass()) {
         Module module = (Module)o;
         return Objects.equals(this.name, module.name);
      } else {
         return false;
      }
   }

   @Override
   public int hashCode() {
      return Objects.hash(this.name);
   }

   public int compareTo(@NotNull Module o) {
      return this.name.compareTo(o.name);
   }
}
