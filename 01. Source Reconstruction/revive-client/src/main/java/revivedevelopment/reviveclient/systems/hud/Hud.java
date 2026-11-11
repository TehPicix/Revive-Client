package revivedevelopment.reviveclient.systems.hud;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import org.jetbrains.annotations.NotNull;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.events.render.Render2DEvent;
import revivedevelopment.reviveclient.events.revive.CustomFontChangedEvent;
import revivedevelopment.reviveclient.events.world.TickEvent;
import revivedevelopment.reviveclient.gui.WidgetScreen;
import revivedevelopment.reviveclient.settings.KeybindSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.settings.Settings;
import revivedevelopment.reviveclient.systems.System;
import revivedevelopment.reviveclient.systems.Systems;
import revivedevelopment.reviveclient.systems.hud.elements.ActiveModulesHud;
import revivedevelopment.reviveclient.systems.hud.elements.ArmorHud;
import revivedevelopment.reviveclient.systems.hud.elements.BindsHud;
import revivedevelopment.reviveclient.systems.hud.elements.CombatHud;
import revivedevelopment.reviveclient.systems.hud.elements.CompassHud;
import revivedevelopment.reviveclient.systems.hud.elements.EntityCountHud;
import revivedevelopment.reviveclient.systems.hud.elements.HoleHud;
import revivedevelopment.reviveclient.systems.hud.elements.InventoryHud;
import revivedevelopment.reviveclient.systems.hud.elements.ItemHud;
import revivedevelopment.reviveclient.systems.hud.elements.KeystrokesHud;
import revivedevelopment.reviveclient.systems.hud.elements.LagNotifierHud;
import revivedevelopment.reviveclient.systems.hud.elements.MediaHud;
import revivedevelopment.reviveclient.systems.hud.elements.NotificationsHud;
import revivedevelopment.reviveclient.systems.hud.elements.PlayerModelHud;
import revivedevelopment.reviveclient.systems.hud.elements.PlayerRadarHud;
import revivedevelopment.reviveclient.systems.hud.elements.PotionTimersHud;
import revivedevelopment.reviveclient.systems.hud.elements.ReviveTextHud;
import revivedevelopment.reviveclient.systems.hud.elements.SpotifyHud;
import revivedevelopment.reviveclient.systems.hud.elements.TargetHud;
import revivedevelopment.reviveclient.systems.hud.elements.TeamSuppliesHud;
import revivedevelopment.reviveclient.systems.hud.screens.HudEditorScreen;
import revivedevelopment.reviveclient.systems.modules.Modules;
import revivedevelopment.reviveclient.systems.modules.gui.HudModule;
import revivedevelopment.reviveclient.utils.Utils;
import revivedevelopment.reviveclient.utils.misc.NbtUtils;
import revivedevelopment.reviveclient.utils.render.color.Color;
import revivedevelopment.reviveclient.utils.render.color.SettingColor;

public class Hud extends System<Hud> implements Iterable<HudElement> {
   public static final HudGroup GROUP = new HudGroup("Revive");
   public boolean active;
   public Settings settings = new Settings();
   public final Map<String, HudElementInfo<?>> infos = new TreeMap<>();
   private final List<HudElement> elements = new ArrayList<>();
   private boolean resetToDefaultElements;

   private HudModule module() {
      return Modules.get() == null ? null : Modules.get().get(HudModule.class);
   }

   public boolean hasCustomFont() {
      HudModule m = this.module();
      return m == null || m.customFont();
   }

   public double getTextScale() {
      HudModule m = this.module();
      return (m == null ? 1.0 : m.textScale()) * this.getHudScale();
   }

   public double getHudScale() {
      HudModule m = this.module();
      double manual = m == null ? 1.0 : m.hudScale();
      boolean auto = m == null || m.autoScale();
      if (auto && ReviveClient.mc.getWindow() != null) {
         double fbHeight = ReviveClient.mc.getWindow().getFramebufferHeight();
         if (fbHeight <= 0.0) {
            return manual;
         } else {
            double autoFactor = Math.max(1.0, Math.min(3.0, fbHeight / 1080.0));
            return autoFactor * manual;
         }
      } else {
         return manual;
      }
   }

   public List<SettingColor> getTextColors() {
      return List.of(toSetting(HudStyle.TEXT_PRIMARY), toSetting(HudStyle.TEXT_SECONDARY), toSetting(HudStyle.ENABLED), toSetting(HudStyle.DISABLED));
   }

   private static SettingColor toSetting(Color c) {
      return new SettingColor(c.r, c.g, c.b, c.a);
   }

   public int getBorder() {
      HudModule m = this.module();
      return m == null ? 4 : m.border();
   }

   public int getSnappingRange() {
      HudModule m = this.module();
      return m == null ? 10 : m.snappingRange();
   }

   private boolean hideInMenus() {
      HudModule m = this.module();
      return m != null && m.hideInMenus();
   }

   public Hud() {
      super("hud");
   }

   public static Hud get() {
      return Systems.get(Hud.class);
   }

   @Override
   public void init() {
      this.settings.registerColorSettings(null);
      this.register(ReviveTextHud.INFO);
      this.register(ItemHud.INFO);
      this.register(InventoryHud.INFO);
      this.register(CompassHud.INFO);
      this.register(SpotifyHud.INFO);
      this.register(MediaHud.INFO);
      this.register(ArmorHud.INFO);
      this.register(HoleHud.INFO);
      this.register(PlayerModelHud.INFO);
      this.register(ActiveModulesHud.INFO);
      this.register(LagNotifierHud.INFO);
      this.register(PlayerRadarHud.INFO);
      this.register(TeamSuppliesHud.INFO);
      this.register(PotionTimersHud.INFO);
      this.register(CombatHud.INFO);
      this.register(TargetHud.INFO);
      this.register(KeystrokesHud.INFO);
      this.register(BindsHud.INFO);
      this.register(EntityCountHud.INFO);
      this.register(NotificationsHud.INFO);
      if (this.isFirstInit) {
         this.resetToDefaultElements();
      }
   }

   public void register(HudElementInfo<?> info) {
      this.infos.put(info.name, info);
   }

   private void add(HudElement element, int x, int y, XAnchor xAnchor, YAnchor yAnchor) {
      element.box.setPos(x, y);
      if (xAnchor != null && yAnchor != null) {
         element.box.xAnchor = xAnchor;
         element.box.yAnchor = yAnchor;
      } else {
         element.box.updateAnchors();
      }

      element.settings.registerColorSettings(null);
      this.elements.add(element);
   }

   public void add(HudElementInfo<?> info, int x, int y, XAnchor xAnchor, YAnchor yAnchor) {
      this.add(info.create(), x, y, xAnchor, yAnchor);
   }

   public void add(HudElementInfo<?> info, int x, int y) {
      this.add(info, x, y, null, null);
   }

   public void add(HudElementInfo.Preset preset, int x, int y, XAnchor xAnchor, YAnchor yAnchor) {
      HudElement element = preset.info.create();
      preset.callback.accept(element);
      this.add(element, x, y, xAnchor, yAnchor);
   }

   public void add(HudElementInfo<?>.Preset preset, int x, int y) {
      this.add(preset, x, y, null, null);
   }

   void remove(HudElement element) {
      if (this.elements.remove(element)) {
         discard(element);
      }
   }

   public void clear() {
      this.clearElements();
   }

   private void clearElements() {
      for (HudElement element : this.elements) {
         discard(element);
      }

      this.elements.clear();
   }

   private static void discard(HudElement element) {
      element.settings.unregisterColorSettings();

      for (SettingGroup group : element.settings) {
         for (Setting<?> setting : group) {
            if (setting instanceof KeybindSetting) {
               ReviveClient.EVENT_BUS.unsubscribe(setting);
            }
         }
      }

      element.onRemoved();
   }

   public void resetToDefaultElements() {
      this.resetToDefaultElements = true;
   }

   private void resetToDefaultElementsImpl() {
      this.clearElements();
      int h = (int)Math.ceil(HudRenderer.INSTANCE.textHeight(true));
      this.add(ReviveTextHud.WATERMARK, 4, 4, XAnchor.Left, YAnchor.Top);
      this.add(ReviveTextHud.FPS, 4, 4 + h, XAnchor.Left, YAnchor.Top);
      this.add(ReviveTextHud.TPS, 4, 4 + h * 2, XAnchor.Left, YAnchor.Top);
      this.add(ReviveTextHud.PING, 4, 4 + h * 3, XAnchor.Left, YAnchor.Top);
      this.add(ReviveTextHud.SPEED, 4, 4 + h * 4, XAnchor.Left, YAnchor.Top);
      this.add(ActiveModulesHud.INFO, -4, 4, XAnchor.Right, YAnchor.Top);
      this.add(TargetHud.INFO, 0, 24, XAnchor.Center, YAnchor.Top);
      this.add(ArmorHud.INFO, 0, -40, XAnchor.Center, YAnchor.Bottom);
      this.add(PotionTimersHud.INFO, 4, -4, XAnchor.Left, YAnchor.Bottom);
      this.add(ReviveTextHud.POSITION, -4, -4, XAnchor.Right, YAnchor.Bottom);
      this.add(ReviveTextHud.OPPOSITE_POSITION, -4, -4 - h, XAnchor.Right, YAnchor.Bottom);
      this.add(ReviveTextHud.ROTATION, -4, -4 - h * 2, XAnchor.Right, YAnchor.Bottom);
   }

   @EventHandler
   private void onTick(TickEvent.Post event) {
      if (!Utils.isLoading()) {
         if (this.resetToDefaultElements) {
            this.resetToDefaultElementsImpl();
            this.resetToDefaultElements = false;
         }

         if (this.isHudOn() || HudEditorScreen.isOpen()) {
            boolean editorOpen = HudEditorScreen.isOpen();

            for (HudElement element : this.elements) {
               if (element.isActive() || editorOpen) {
                  element.tick(HudRenderer.INSTANCE);
               }
            }
         }
      }
   }

   @EventHandler
   private void onRender(Render2DEvent event) {
      if (!Utils.isLoading()) {
         if (!HudEditorScreen.isOpen()) {
            if (this.isHudOn() && !this.shouldHideHud()) {
               if (!ReviveClient.mc.options.hudHidden) {
                  HudRenderer.INSTANCE.deferText = true;

                  try {
                     HudRenderer.INSTANCE.begin(event.drawContext);

                     for (HudElement element : this.elements) {
                        element.updatePos();
                        if (element.isActive()) {
                           element.render(HudRenderer.INSTANCE);
                        }
                     }

                     HudRenderer.INSTANCE.end();
                  } finally {
                     HudRenderer.INSTANCE.deferText = false;
                  }
               }
            }
         }
      }
   }

   public boolean isHudOn() {
      HudModule m = this.module();
      if (m != null) {
         this.active = m.isActive();
         return this.active;
      } else {
         return this.active;
      }
   }

   private boolean shouldHideHud() {
      return this.hideInMenus() && ReviveClient.mc.currentScreen != null && !(ReviveClient.mc.currentScreen instanceof WidgetScreen);
   }

   @EventHandler
   private void onCustomFontChanged(CustomFontChangedEvent event) {
      if (this.hasCustomFont()) {
         for (HudElement element : this.elements) {
            element.onFontChanged();
         }
      }
   }

   @NotNull
   @Override
   public Iterator<HudElement> iterator() {
      return this.elements.iterator();
   }

   @Override
   public NbtCompound toTag() {
      NbtCompound tag = new NbtCompound();
      tag.putInt("__version__", 1);
      tag.putBoolean("active", this.active);
      tag.put("settings", this.settings.toTag());
      tag.put("elements", NbtUtils.listToTag(this.elements));
      return tag;
   }

   public Hud fromTag(NbtCompound tag) {
      if (!tag.contains("__version__")) {
         this.resetToDefaultElements();
         return this;
      } else {
         this.settings.fromTag(tag.getCompound("settings").orElse(new NbtCompound()));
         this.clearElements();
         if (!tag.contains("elements")) {
            return this;
         } else {
            Optional<NbtList> optionalList = tag.getList("elements");
            if (!optionalList.isPresent()) {
               return this;
            } else {
               NbtList elementsList = optionalList.get();

               for (int i = 0; i < elementsList.size(); i++) {
                  if (i < elementsList.size() && elementsList.get(i) instanceof NbtCompound) {
                     NbtCompound c = (NbtCompound)elementsList.get(i);
                     if (c.contains("name")) {
                        Optional<String> optionalName = c.getString("name");
                        if (optionalName.isPresent()) {
                           HudElementInfo<?> info = this.infos.get(optionalName.get());
                           if (info != null) {
                              HudElement element = info.create();
                              element.fromTag(c);
                              element.settings.registerColorSettings(null);
                              this.elements.add(element);
                           }
                        }
                     }
                  }
               }

               return this;
            }
         }
      }
   }
}
