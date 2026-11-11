package revivedevelopment.reviveclient.systems.modules;

import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.events.game.GameJoinedEvent;
import revivedevelopment.reviveclient.events.game.GameLeftEvent;
import revivedevelopment.reviveclient.events.game.OpenScreenEvent;
import revivedevelopment.reviveclient.events.revive.KeyEvent;
import revivedevelopment.reviveclient.events.revive.ModuleBindChangedEvent;
import revivedevelopment.reviveclient.events.revive.MouseButtonEvent;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.systems.System;
import revivedevelopment.reviveclient.systems.Systems;
import revivedevelopment.reviveclient.systems.config.AntiCheatConfig;
import revivedevelopment.reviveclient.systems.config.MultiTask;
import revivedevelopment.reviveclient.systems.modules.combat.AnchorAura;
import revivedevelopment.reviveclient.systems.modules.combat.AntiMace;
import revivedevelopment.reviveclient.systems.modules.combat.AttackSwap;
import revivedevelopment.reviveclient.systems.modules.combat.AutoArmor;
import revivedevelopment.reviveclient.systems.modules.combat.AutoCrystal;
import revivedevelopment.reviveclient.systems.modules.combat.AutoMine;
import revivedevelopment.reviveclient.systems.modules.combat.AutoTrap;
import revivedevelopment.reviveclient.systems.modules.combat.AutoWeb;
import revivedevelopment.reviveclient.systems.modules.combat.BootSwap;
import revivedevelopment.reviveclient.systems.modules.combat.BreachSwap;
import revivedevelopment.reviveclient.systems.modules.combat.ChineseAura;
import revivedevelopment.reviveclient.systems.modules.combat.Criticals;
import revivedevelopment.reviveclient.systems.modules.combat.ForceSwim;
import revivedevelopment.reviveclient.systems.modules.combat.KeyPotion;
import revivedevelopment.reviveclient.systems.modules.combat.MaceAura;
import revivedevelopment.reviveclient.systems.modules.combat.MaceDive;
import revivedevelopment.reviveclient.systems.modules.combat.Offhand;
import revivedevelopment.reviveclient.systems.modules.combat.PearlBlocker;
import revivedevelopment.reviveclient.systems.modules.combat.Phase;
import revivedevelopment.reviveclient.systems.modules.combat.PistonCrystal;
import revivedevelopment.reviveclient.systems.modules.combat.Surround;
import revivedevelopment.reviveclient.systems.modules.combat.SwordAura;
import revivedevelopment.reviveclient.systems.modules.combat.Velocity;
import revivedevelopment.reviveclient.systems.modules.gui.Gui;
import revivedevelopment.reviveclient.systems.modules.gui.HudModule;
import revivedevelopment.reviveclient.systems.modules.hunting.AFKVanillaFly;
import revivedevelopment.reviveclient.systems.modules.hunting.AutoPortal;
import revivedevelopment.reviveclient.systems.modules.hunting.AutoRegear;
import revivedevelopment.reviveclient.systems.modules.hunting.CaveAirESP;
import revivedevelopment.reviveclient.systems.modules.hunting.ChatSigns;
import revivedevelopment.reviveclient.systems.modules.hunting.DiscordNotifs;
import revivedevelopment.reviveclient.systems.modules.hunting.FlowESP;
import revivedevelopment.reviveclient.systems.modules.hunting.LavaColumns;
import revivedevelopment.reviveclient.systems.modules.hunting.LoreLocator;
import revivedevelopment.reviveclient.systems.modules.hunting.NetherMobDetection;
import revivedevelopment.reviveclient.systems.modules.hunting.NewChunks;
import revivedevelopment.reviveclient.systems.modules.hunting.Pitch40Util;
import revivedevelopment.reviveclient.systems.modules.hunting.RotationDetector;
import revivedevelopment.reviveclient.systems.modules.hunting.SignRender;
import revivedevelopment.reviveclient.systems.modules.hunting.StashFinderPlus;
import revivedevelopment.reviveclient.systems.modules.hunting.TrailFollower;
import revivedevelopment.reviveclient.systems.modules.hunting.VanityESP;
import revivedevelopment.reviveclient.systems.modules.hunting.arealoader.AreaLoader;
import revivedevelopment.reviveclient.systems.modules.movement.AngleCalculator;
import revivedevelopment.reviveclient.systems.modules.movement.BaritoneElytraOverworld;
import revivedevelopment.reviveclient.systems.modules.movement.BoatFly;
import revivedevelopment.reviveclient.systems.modules.movement.ElytraRecast;
import revivedevelopment.reviveclient.systems.modules.movement.ElytraReplace;
import revivedevelopment.reviveclient.systems.modules.movement.ElytraSaver;
import revivedevelopment.reviveclient.systems.modules.movement.EntityControl;
import revivedevelopment.reviveclient.systems.modules.movement.Follower;
import revivedevelopment.reviveclient.systems.modules.movement.GUIMove;
import revivedevelopment.reviveclient.systems.modules.movement.GrimEntityBoost;
import revivedevelopment.reviveclient.systems.modules.movement.GrimShulkerLaunch;
import revivedevelopment.reviveclient.systems.modules.movement.NoFall;
import revivedevelopment.reviveclient.systems.modules.movement.NoSlow;
import revivedevelopment.reviveclient.systems.modules.movement.RocketBoost;
import revivedevelopment.reviveclient.systems.modules.movement.Scaffold;
import revivedevelopment.reviveclient.systems.modules.movement.Sprint;
import revivedevelopment.reviveclient.systems.modules.movement.Step;
import revivedevelopment.reviveclient.systems.modules.movement.elytrafly.ElytraFly;
import revivedevelopment.reviveclient.systems.modules.player.AirPlace;
import revivedevelopment.reviveclient.systems.modules.player.AntiAFK;
import revivedevelopment.reviveclient.systems.modules.player.AntiSpawnSet;
import revivedevelopment.reviveclient.systems.modules.player.AutoEat;
import revivedevelopment.reviveclient.systems.modules.player.AutoLogPlus;
import revivedevelopment.reviveclient.systems.modules.player.AutoReplenish;
import revivedevelopment.reviveclient.systems.modules.player.AutoXP;
import revivedevelopment.reviveclient.systems.modules.player.BetterChat;
import revivedevelopment.reviveclient.systems.modules.player.ChestStealer;
import revivedevelopment.reviveclient.systems.modules.player.ChestSwap;
import revivedevelopment.reviveclient.systems.modules.player.DisconnectSound;
import revivedevelopment.reviveclient.systems.modules.player.ElytraControl;
import revivedevelopment.reviveclient.systems.modules.player.ElytraTweaks;
import revivedevelopment.reviveclient.systems.modules.player.FastUse;
import revivedevelopment.reviveclient.systems.modules.player.GlideResync;
import revivedevelopment.reviveclient.systems.modules.player.HotbarLock;
import revivedevelopment.reviveclient.systems.modules.player.InventorySearch;
import revivedevelopment.reviveclient.systems.modules.player.InventorySorter;
import revivedevelopment.reviveclient.systems.modules.player.Irc;
import revivedevelopment.reviveclient.systems.modules.player.ItemAlerts;
import revivedevelopment.reviveclient.systems.modules.player.MapDuplicator;
import revivedevelopment.reviveclient.systems.modules.player.MiddleClickExtra;
import revivedevelopment.reviveclient.systems.modules.player.NoRotate;
import revivedevelopment.reviveclient.systems.modules.player.Notifier;
import revivedevelopment.reviveclient.systems.modules.player.Nuker;
import revivedevelopment.reviveclient.systems.modules.player.PacketLimiter;
import revivedevelopment.reviveclient.systems.modules.player.PingBypass;
import revivedevelopment.reviveclient.systems.modules.player.Rotation;
import revivedevelopment.reviveclient.systems.modules.player.Rubberband;
import revivedevelopment.reviveclient.systems.modules.player.ShiftDrag;
import revivedevelopment.reviveclient.systems.modules.player.SilentMine;
import revivedevelopment.reviveclient.systems.modules.player.SpeedMine;
import revivedevelopment.reviveclient.systems.modules.player.TeamSync;
import revivedevelopment.reviveclient.systems.modules.player.Timer;
import revivedevelopment.reviveclient.systems.modules.player.XCarry;
import revivedevelopment.reviveclient.systems.modules.render.Ambiance;
import revivedevelopment.reviveclient.systems.modules.render.BetterScreenshot;
import revivedevelopment.reviveclient.systems.modules.render.BetterTooltips;
import revivedevelopment.reviveclient.systems.modules.render.Breadcrumbs;
import revivedevelopment.reviveclient.systems.modules.render.BreakIndicators;
import revivedevelopment.reviveclient.systems.modules.render.Chams;
import revivedevelopment.reviveclient.systems.modules.render.ESP;
import revivedevelopment.reviveclient.systems.modules.render.ESPPlus;
import revivedevelopment.reviveclient.systems.modules.render.Emotes;
import revivedevelopment.reviveclient.systems.modules.render.FreeLook;
import revivedevelopment.reviveclient.systems.modules.render.Freecam;
import revivedevelopment.reviveclient.systems.modules.render.Fullbright;
import revivedevelopment.reviveclient.systems.modules.render.HandView;
import revivedevelopment.reviveclient.systems.modules.render.HoleESP;
import revivedevelopment.reviveclient.systems.modules.render.LogoutSpots;
import revivedevelopment.reviveclient.systems.modules.render.Nametags;
import revivedevelopment.reviveclient.systems.modules.render.NoRender;
import revivedevelopment.reviveclient.systems.modules.render.PearlOwners;
import revivedevelopment.reviveclient.systems.modules.render.PhaseESP;
import revivedevelopment.reviveclient.systems.modules.render.PopChams;
import revivedevelopment.reviveclient.systems.modules.render.PortalESP;
import revivedevelopment.reviveclient.systems.modules.render.StorageESP;
import revivedevelopment.reviveclient.systems.modules.render.Tracers;
import revivedevelopment.reviveclient.systems.modules.render.TracersPlus;
import revivedevelopment.reviveclient.systems.modules.render.Trajectories;
import revivedevelopment.reviveclient.systems.modules.render.Zoom;
import revivedevelopment.reviveclient.utils.Utils;
import revivedevelopment.reviveclient.utils.misc.Keybind;
import revivedevelopment.reviveclient.utils.misc.input.Input;
import revivedevelopment.reviveclient.utils.misc.input.KeyAction;

public class Modules extends System<Modules> {
   private static final List<Category> CATEGORIES = new ArrayList<>();
   private final List<Module> modules = new ArrayList<>();
   private final Map<Class<? extends Module>, Module> moduleInstances = new Reference2ReferenceOpenHashMap();
   private final Map<Category, List<Module>> groups = new Reference2ReferenceOpenHashMap();
   private final List<Module> active = new ArrayList<>();
   private Module moduleToBind;

   public Modules() {
      super("modules");
   }

   public static Modules get() {
      return Systems.get(Modules.class);
   }

   @Override
   public void init() {
      this.initCombat();
      this.initPlayer();
      this.initMovement();
      this.initRender();
      this.initHunting();
      this.initGui();
   }

   private void initCombat() {
      this.add(new Surround());
      this.add(new AutoTrap());
      this.add(new Offhand());
      this.add(new Phase());
      this.add(new Velocity());
      this.add(new AutoArmor());
      this.add(new AutoMine());
      this.add(new AutoCrystal());
      this.add(new AnchorAura());
      this.add(new SwordAura());
      this.add(new MaceAura());
      this.add(new PearlBlocker());
      this.add(new AutoWeb());
      this.add(new BootSwap());
      this.add(new KeyPotion());
      this.add(new NoSlow());
      this.add(new Follower());
      this.add(new XCarry());
      this.add(new NoRotate());
      this.add(new ElytraReplace());
      this.add(new ElytraSaver());
      this.add(new AttackSwap());
      this.add(new BreachSwap());
      this.add(new Criticals());
      this.add(new MaceDive());
      this.add(new AntiMace());
      this.add(new PistonCrystal());
      this.add(new ChineseAura());
      this.add(new ForceSwim());
   }

   private void initPlayer() {
      this.add(new SilentMine());
      this.add(new SpeedMine());
      this.add(new Nuker());
      this.add(new AirPlace());
      this.add(new AutoReplenish());
      this.add(new MiddleClickExtra());
      this.add(new ChestSwap());
      this.add(new AntiSpawnSet());
      this.add(new DisconnectSound());
      this.add(new MapDuplicator());
      this.add(new ChestStealer());
      this.add(new GlideResync());
      this.add(new ShiftDrag());
      this.add(new FastUse());
      this.add(new AutoXP());
      this.add(new AntiAFK());
      this.add(new AutoEat());
      this.add(new AutoLogPlus());
      this.add(new ItemAlerts());
      this.add(new Timer());
      this.add(new Rotation());
      this.add(new Irc());
      this.add(new TeamSync());
      this.add(new Emotes());
      this.add(new BetterChat());
      this.add(new InventorySorter());
      this.add(new InventorySearch());
      this.add(new HotbarLock());
      this.add(new PacketLimiter());
      this.add(new Rubberband());
      this.add(new PingBypass());
   }

   private void initMovement() {
      this.add(new Sprint());
      this.add(new Scaffold());
      this.add(new EntityControl());
      this.add(new BoatFly());
      this.add(new ElytraFly());
      this.add(new ElytraTweaks());
      this.add(new ElytraControl());
      this.add(new TrailFollower());
      this.add(new ElytraRecast());
      this.add(new GrimShulkerLaunch());
      this.add(new GrimEntityBoost());
      this.add(new RocketBoost());
      this.add(new BaritoneElytraOverworld());
      this.add(new AngleCalculator());
      this.add(new NoFall());
      this.add(new Step());
      this.add(new GUIMove());
      this.add(new AFKVanillaFly());
   }

   private void initRender() {
      this.add(new BetterTooltips());
      this.add(new BreakIndicators());
      this.add(new Chams());
      this.add(new Ambiance());
      this.add(new ESP());
      this.add(new Fullbright());
      this.add(new HandView());
      this.add(new NoRender());
      this.add(new Tracers());
      this.add(new Nametags());
      this.add(new PearlOwners());
      this.add(new LogoutSpots());
      this.add(new ESPPlus());
      this.add(new TracersPlus());
      this.add(new StorageESP());
      this.add(new PortalESP());
      this.add(new HoleESP());
      this.add(new Trajectories());
      this.add(new Zoom());
      this.add(new Breadcrumbs());
      this.add(new Freecam());
      this.add(new PhaseESP());
      this.add(new FreeLook());
      this.add(new BetterScreenshot());
      this.add(new PopChams());
   }

   private void initHunting() {
      this.add(new AutoPortal());
      this.add(new Pitch40Util());
      this.add(new DiscordNotifs());
      this.add(new LavaColumns());
      this.add(new NetherMobDetection());
      this.add(new ChatSigns());
      this.add(new StashFinderPlus());
      this.add(new AreaLoader());
      this.add(new AutoRegear());
      this.add(new VanityESP());
      this.add(new SignRender());
      this.add(new RotationDetector());
      this.add(new CaveAirESP());
      this.add(new LoreLocator());
      this.add(new FlowESP());
      this.add(new NewChunks());
   }

   @Override
   public void load(File folder) {
      for (Module module : this.getAll()) {
         for (SettingGroup group : module.settings) {
            for (Setting<?> setting : group) {
               setting.reset();
            }
         }
      }

      super.load(folder);
   }

   public void sortModules() {
      for (List<Module> modules : this.groups.values()) {
         modules.sort(Comparator.comparing(o -> o.title));
      }

      this.modules.sort(Comparator.comparing(o -> o.title));
   }

   public static void registerCategory(Category category) {
      if (!Categories.REGISTERING) {
         throw new RuntimeException("Modules.registerCategory - Cannot register category outside of onRegisterCategories callback.");
      } else {
         CATEGORIES.add(category);
      }
   }

   public static Iterable<Category> loopCategories() {
      return CATEGORIES;
   }

   public <T extends Module> T get(Class<T> klass) {
      return (T)this.moduleInstances.get(klass);
   }

   public Module get(String name) {
      for (Module module : this.moduleInstances.values()) {
         if (module.name.equalsIgnoreCase(name)) {
            return module;
         }
      }

      return null;
   }

   public boolean isActive(Class<? extends Module> klass) {
      Module module = this.get(klass);
      return module != null && module.isActive();
   }

   public List<Module> getGroup(Category category) {
      return this.groups.computeIfAbsent(category, category1 -> new ArrayList<>());
   }

   public Collection<Module> getAll() {
      return this.moduleInstances.values();
   }

   @Deprecated(
      forRemoval = true
   )
   public List<Module> getList() {
      return this.modules;
   }

   public int getCount() {
      return this.moduleInstances.size();
   }

   public List<Module> getActive() {
      synchronized (this.active) {
         return this.active;
      }
   }

   void addActive(Module module) {
      synchronized (this.active) {
         if (!this.active.contains(module)) {
            this.active.add(module);
         }
      }
   }

   void removeActive(Module module) {
      synchronized (this.active) {
         this.active.remove(module);
      }
   }

   public void setModuleToBind(Module moduleToBind) {
      this.moduleToBind = moduleToBind;
   }

   public boolean isBinding() {
      return this.moduleToBind != null;
   }

   @EventHandler(
      priority = 200
   )
   private void onKeyBinding(KeyEvent event) {
      if (event.action == KeyAction.Press && this.isBinding() && isClearKey(event.key)) {
         this.moduleToBind.keybind.set(Keybind.none());
         this.moduleToBind.info("Removed bind.");
         ReviveClient.EVENT_BUS.post(ModuleBindChangedEvent.get(this.moduleToBind));
         this.moduleToBind = null;
         event.cancel();
      } else {
         if (event.action == KeyAction.Release && this.onBinding(true, event.key, event.modifiers)) {
            event.cancel();
         }
      }
   }

   public static boolean isClearKey(int key) {
      return key == 256 || key == 259 || key == 261;
   }

   @EventHandler(
      priority = 200
   )
   private void onButtonBinding(MouseButtonEvent event) {
      if (event.action == KeyAction.Release && this.onBinding(false, event.button, 0)) {
         event.cancel();
      }
   }

   private boolean onBinding(boolean isKey, int value, int modifiers) {
      if (!this.isBinding()) {
         return false;
      } else {
         if (this.moduleToBind.keybind.canBindTo(isKey, value, modifiers)) {
            this.moduleToBind.keybind.set(isKey, value, modifiers);
            this.moduleToBind.info("Bound to (highlight)%s(default).", this.moduleToBind.keybind);
         } else {
            if (value != 256) {
               return false;
            }

            this.moduleToBind.keybind.set(Keybind.none());
            this.moduleToBind.info("Removed bind.");
         }

         ReviveClient.EVENT_BUS.post(ModuleBindChangedEvent.get(this.moduleToBind));
         this.moduleToBind = null;
         return true;
      }
   }

   @EventHandler(
      priority = 100
   )
   private void onKey(KeyEvent event) {
      if (event.action != KeyAction.Repeat) {
         this.onAction(true, event.key, event.modifiers, event.action == KeyAction.Press);
      }
   }

   @EventHandler(
      priority = 100
   )
   private void onMouseButton(MouseButtonEvent event) {
      if (event.action != KeyAction.Repeat) {
         this.onAction(false, event.button, 0, event.action == KeyAction.Press);
      }
   }

   private void onAction(boolean isKey, int value, int modifiers, boolean isPress) {
      if (ReviveClient.mc.currentScreen == null && !Input.isKeyPressed(292)) {
         for (Module module : this.moduleInstances.values()) {
            if (module.keybind.matches(isKey, value, modifiers) && (isPress || module.toggleOnBindRelease && module.isActive())) {
               module.toggle();
               module.sendToggledMsg();
            }
         }
      }
   }

   @EventHandler(
      priority = 201
   )
   private void onOpenScreen(OpenScreenEvent event) {
      if (Utils.canUpdate()) {
         for (Module module : this.moduleInstances.values()) {
            if (module.toggleOnBindRelease && module.isActive()) {
               module.toggle();
               module.sendToggledMsg();
            }
         }
      }
   }

   @EventHandler
   private void onGameJoined(GameJoinedEvent event) {
      synchronized (this.active) {
         for (Module module : this.getAll()) {
            if (module.isActive() && !module.runInMainMenu && !module.isRemote()) {
               ReviveClient.EVENT_BUS.subscribe(module);
               module.onActivate();
            }
         }
      }
   }

   @EventHandler
   private void onGameLeft(GameLeftEvent event) {
      synchronized (this.active) {
         for (Module module : this.getAll()) {
            if (module.isActive() && !module.runInMainMenu && !module.isRemote()) {
               ReviveClient.EVENT_BUS.unsubscribe(module);
               module.onDeactivate();
            }
         }
      }
   }

   public void disableAll() {
      synchronized (this.active) {
         for (Module module : this.getAll()) {
            if (module.isActive()) {
               module.toggle();
            }
         }
      }
   }

   @Override
   public NbtCompound toTag() {
      NbtCompound tag = new NbtCompound();
      NbtList modulesTag = new NbtList();

      for (Module module : this.getAll()) {
         try {
            NbtCompound moduleTag = module.toTag();
            if (moduleTag != null) {
               modulesTag.add(moduleTag);
            }
         } catch (Throwable var6) {
            ReviveClient.LOG.error("Failed to save settings for module '{}'", module.name, var6);
         }
      }

      tag.put("modules", modulesTag);
      return tag;
   }

   public Modules fromTag(NbtCompound tag) {
      this.disableAll();
      NbtList modulesTag = tag.getListOrEmpty("modules");
      Set<String> loaded = new HashSet<>();

      for (NbtElement moduleTagI : modulesTag) {
         try {
            NbtCompound moduleTag = (NbtCompound)moduleTagI;
            Module module = this.get(moduleTag.getString("name", ""));
            if (module != null) {
               loaded.add(module.name);
               module.fromTag(moduleTag);
            }
         } catch (Throwable var8) {
            ReviveClient.LOG.error("Failed to load settings for a module", var8);
         }
      }

      for (Module module : this.getAll()) {
         if (module.activeByDefault() && !loaded.contains(module.name) && !module.isActive()) {
            module.toggle();
         }
      }

      return this;
   }

   public void add(Module module) {
      if (!CATEGORIES.contains(module.category)) {
         throw new RuntimeException("Modules.addModule - Module's category was not registered.");
      } else {
         AtomicReference<Module> removedModule = new AtomicReference<>();
         if (this.moduleInstances.values().removeIf(module1 -> {
            if (module1.name.equals(module.name)) {
               removedModule.set(module1);
               module1.settings.unregisterColorSettings();
               return true;
            } else {
               return false;
            }
         })) {
            this.getGroup(removedModule.get().category).remove(removedModule.get());
         }

         this.moduleInstances.put((Class<? extends Module>)module.getClass(), module);
         this.modules.add(module);
         this.getGroup(module.category).add(module);
         module.settings.registerColorSettings(module);
      }
   }

   private void initGui() {
      this.add(new Gui());
      this.add(new HudModule());
      this.add(new AntiCheatConfig());
      this.add(new MultiTask());
      this.add(new Notifier());
   }
}
