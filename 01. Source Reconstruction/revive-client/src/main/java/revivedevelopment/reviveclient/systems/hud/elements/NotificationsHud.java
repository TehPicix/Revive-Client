package revivedevelopment.reviveclient.systems.hud.elements;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.settings.IntSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.systems.hud.Hud;
import revivedevelopment.reviveclient.systems.hud.HudElement;
import revivedevelopment.reviveclient.systems.hud.HudElementInfo;
import revivedevelopment.reviveclient.systems.hud.HudRenderer;
import revivedevelopment.reviveclient.utils.render.color.Color;

public class NotificationsHud extends HudElement {
   public static final HudElementInfo<NotificationsHud> INFO = new HudElementInfo<>(
      Hud.GROUP, "notifications", "Toast popups for low supplies, damaged armor and module events.", NotificationsHud::new
   );
   private static final int TOAST_WIDTH = 150;
   private static final int TOAST_HEIGHT = 26;
   private static final int TOAST_GAP = 4;
   private static final int TOAST_PAD = 6;
   private static final int ICON_SIZE = 16;
   private static final int ICON_GAP = 7;
   private static final int ACCENT_W = 3;
   private static final int BG_RGB = 1184795;
   private static final float SLIDE_SPEED = 0.18F;
   private static final float FADE_SPEED = 0.1F;
   private static final long CHECK_INTERVAL_MS = 1000L;
   private static final long TYPE_COOLDOWN_MS = 8000L;
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgThresholds = this.settings.createGroup("Thresholds");
   private final Setting<Integer> holdTime = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("hold-time")
            .description("How long a toast stays before fading, in milliseconds.")
            .defaultValue(3000)
            .min(500)
            .sliderRange(500, 10000)
            .build()
      );
   private final Setting<Integer> maxToasts = this.sgGeneral
      .add(new IntSetting.Builder().name("max-toasts").description("Maximum number of toasts shown at once.").defaultValue(5).min(1).sliderRange(1, 10).build());
   private final Setting<Integer> armorThreshold = this.sgThresholds
      .add(
         new IntSetting.Builder()
            .name("armor-percent")
            .description("Warn when a worn armor piece (or elytra) drops to this durability percent. 0 disables.")
            .defaultValue(30)
            .min(0)
            .max(100)
            .sliderRange(0, 100)
            .build()
      );
   private final Setting<Integer> obsidianThreshold = this.sgThresholds
      .add(
         new IntSetting.Builder()
            .name("obsidian")
            .description("Warn at this many obsidian or fewer. 0 disables.")
            .defaultValue(16)
            .min(0)
            .sliderRange(0, 128)
            .build()
      );
   private final Setting<Integer> pearlThreshold = this.sgThresholds
      .add(
         new IntSetting.Builder()
            .name("pearls")
            .description("Warn at this many ender pearls or fewer. 0 disables.")
            .defaultValue(4)
            .min(0)
            .sliderRange(0, 64)
            .build()
      );
   private final Setting<Integer> xpThreshold = this.sgThresholds
      .add(
         new IntSetting.Builder()
            .name("xp-bottles")
            .description("Warn at this many XP bottles or fewer. 0 disables.")
            .defaultValue(8)
            .min(0)
            .sliderRange(0, 128)
            .build()
      );
   private final Setting<Integer> crystalThreshold = this.sgThresholds
      .add(
         new IntSetting.Builder()
            .name("crystals")
            .description("Warn at this many end crystals or fewer. 0 disables.")
            .defaultValue(8)
            .min(0)
            .sliderRange(0, 128)
            .build()
      );
   private final Setting<Integer> totemThreshold = this.sgThresholds
      .add(
         new IntSetting.Builder()
            .name("totems")
            .description("Warn at this many totems or fewer. 0 disables.")
            .defaultValue(1)
            .min(0)
            .sliderRange(0, 27)
            .build()
      );
   private final Setting<Integer> gappleThreshold = this.sgThresholds
      .add(
         new IntSetting.Builder()
            .name("gapples")
            .description("Warn at this many enchanted golden apples or fewer. 0 disables.")
            .defaultValue(4)
            .min(0)
            .sliderRange(0, 64)
            .build()
      );
   private final List<NotificationsHud.Toast> toasts = new ArrayList<>();
   private final long[] kindCooldowns = new long[NotificationsHud.Kind.values().length];
   private long lastCheckMs;

   public NotificationsHud() {
      super(INFO);
   }

   public static void notify(String title, String subtitle, ItemStack stack, int color) {
      for (HudElement e : Hud.get()) {
         if (e instanceof NotificationsHud nh && e.isActive()) {
            nh.toasts.add(new NotificationsHud.Toast(title, subtitle, stack == null ? ItemStack.EMPTY : stack, NotificationsHud.Kind.CUSTOM, color));

            while (nh.toasts.size() > nh.maxToasts.get() * 2) {
               nh.toasts.remove(0);
            }

            return;
         }
      }
   }

   @Override
   public void render(HudRenderer renderer) {
      this.setSize(150.0, Math.max(1, Math.min(this.isInEditor() ? 3 : this.toasts.size(), this.maxToasts.get())) * 30 - 4);
      if (this.isInEditor()) {
         this.renderToast(
            renderer,
            new NotificationsHud.Toast("Elytra", "34% durability", new ItemStack(Items.ELYTRA), NotificationsHud.Kind.ARMOR_CHEST, 16733525),
            this.x,
            this.y,
            0.95F
         );
         this.renderToast(
            renderer,
            new NotificationsHud.Toast("Obsidian", "8 left", new ItemStack(Items.OBSIDIAN), NotificationsHud.Kind.OBSIDIAN, 11766015),
            this.x,
            this.y + 26 + 4,
            0.95F
         );
         this.renderToast(
            renderer,
            new NotificationsHud.Toast("Ender Pearl", "2 left", new ItemStack(Items.ENDER_PEARL), NotificationsHud.Kind.PEARL, 5623039),
            this.x,
            this.y + 60,
            0.95F
         );
      } else if (ReviveClient.mc.player != null && ReviveClient.mc.world != null) {
         long now = System.currentTimeMillis();
         if (now - this.lastCheckMs >= 1000L) {
            this.lastCheckMs = now;
            this.checkInventory(now);
         }

         this.animate(now);
         if (!this.toasts.isEmpty()) {
            int drawY = this.y + this.getHeight();
            int drawn = 0;

            for (int i = this.toasts.size() - 1; i >= 0 && drawn < this.maxToasts.get(); i--) {
               NotificationsHud.Toast t = this.toasts.get(i);
               float alpha = t.fadeProgress * t.slideProgress;
               if (!(alpha <= 0.01F)) {
                  drawn++;
                  drawY -= 26;
                  float eased = easeOutCubic(t.slideProgress);
                  int toastX = this.x + Math.round((1.0F - eased) * 156.0F);
                  this.renderToast(renderer, t, toastX, drawY, alpha);
                  drawY -= 4;
               }
            }
         }
      }
   }

   private void renderToast(HudRenderer renderer, NotificationsHud.Toast t, double tx, double ty, float alpha) {
      int a = (int)(alpha * 255.0F) & 0xFF;
      if (a >= 4) {
         int accent = t.color & 16777215;
         renderer.quad(tx + 2.0, ty + 2.0, 150.0, 26.0, rgba(0, a * 51 / 255));
         renderer.quad(tx, ty, 150.0, 26.0, rgba(1184795, a * 230 / 255));
         renderer.quad(tx, ty, 150.0, 1.0, rgba(16777215, a * 24 / 255));
         renderer.quad(tx, ty + 26.0 - 1.0, 150.0, 1.0, rgba(0, a * 64 / 255));
         renderer.quad(tx, ty, 3.0, 26.0, rgba(accent, a * 230 / 255));
         double iconX = tx + 3.0 + 6.0;
         double iconY = ty + 5.0;
         renderer.quad(iconX - 1.0, iconY - 1.0, 18.0, 18.0, rgba(0, a * 48 / 255));
         if (t.stack != null && !t.stack.isEmpty()) {
            renderer.item(t.stack, (int)iconX, (int)iconY, 1.0F, false, null);
         }

         double textX = iconX + 16.0 + 7.0;
         Color titleColor = rgba(16777215, a);
         Color subColor = rgba(accent == 0 ? 11184810 : accent, a);
         double lineH = renderer.textHeight(true, 0.8);
         if (t.subtitle != null && !t.subtitle.isEmpty()) {
            renderer.text(t.title, textX, ty + 4.0, titleColor, true, 0.8);
            renderer.text(t.subtitle, textX, ty + 4.0 + lineH + 1.0, subColor, false, 0.8);
         } else {
            renderer.text(t.title, textX, ty + (26.0 - lineH) / 2.0, titleColor, true, 0.8);
         }

         long elapsed = System.currentTimeMillis() - t.spawnTimeMs;
         float progress = Math.max(0.0F, Math.min(1.0F, 1.0F - (float)elapsed / Math.max(1, this.holdTime.get())));
         if (progress > 0.0F && !t.fading) {
            renderer.quad(tx + 3.0, ty + 26.0 - 1.0, 147.0F * progress, 1.0, rgba(accent, a * 128 / 255));
         }
      }
   }

   private static Color rgba(int rgb, int a) {
      return new Color(rgb >> 16 & 0xFF, rgb >> 8 & 0xFF, rgb & 0xFF, Math.max(0, Math.min(255, a)));
   }

   private static float easeOutCubic(float t) {
      float inv = 1.0F - t;
      return 1.0F - inv * inv * inv;
   }

   private void animate(long now) {
      Iterator<NotificationsHud.Toast> it = this.toasts.iterator();

      while (it.hasNext()) {
         NotificationsHud.Toast t = it.next();
         if (t.slideProgress < 1.0F) {
            t.slideProgress = t.slideProgress + (1.0F - t.slideProgress) * 0.18F;
            if (t.slideProgress > 0.999F) {
               t.slideProgress = 1.0F;
            }
         }

         if (now - t.spawnTimeMs > this.holdTime.get().intValue() && !t.fading) {
            t.fading = true;
         }

         if (t.fading) {
            t.fadeProgress = t.fadeProgress - (t.fadeProgress * 0.1F + 0.01F);
            if (t.fadeProgress <= 0.0F) {
               it.remove();
            }
         }
      }
   }

   private void checkInventory(long now) {
      EquipmentSlot[] slots = new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
      NotificationsHud.Kind[] slotKinds = new NotificationsHud.Kind[]{
         NotificationsHud.Kind.ARMOR_HEAD, NotificationsHud.Kind.ARMOR_CHEST, NotificationsHud.Kind.ARMOR_LEGS, NotificationsHud.Kind.ARMOR_FEET
      };
      if (this.armorThreshold.get() > 0) {
         for (int s = 0; s < slots.length; s++) {
            ItemStack worn = ReviveClient.mc.player.getEquippedStack(slots[s]);
            if (!worn.isEmpty() && worn.getMaxDamage() > 0) {
               int durPercent = (int)(100.0 * (worn.getMaxDamage() - worn.getDamage()) / worn.getMaxDamage());
               if (durPercent <= this.armorThreshold.get()) {
                  this.push(worn.getName().getString(), durPercent + "% durability", worn.copy(), slotKinds[s], 16733525, now);
               }
            }
         }
      }

      this.checkSupply(Items.OBSIDIAN, this.obsidianThreshold.get(), NotificationsHud.Kind.OBSIDIAN, 11766015, now);
      this.checkSupply(Items.ENDER_PEARL, this.pearlThreshold.get(), NotificationsHud.Kind.PEARL, 5623039, now);
      this.checkSupply(Items.EXPERIENCE_BOTTLE, this.xpThreshold.get(), NotificationsHud.Kind.XP, 8191851, now);
      this.checkSupply(Items.END_CRYSTAL, this.crystalThreshold.get(), NotificationsHud.Kind.CRYSTAL, 16739286, now);
      this.checkSupply(Items.TOTEM_OF_UNDYING, this.totemThreshold.get(), NotificationsHud.Kind.TOTEM, 16767291, now);
      this.checkSupply(Items.ENCHANTED_GOLDEN_APPLE, this.gappleThreshold.get(), NotificationsHud.Kind.GAPPLE, 16757575, now);
   }

   private void checkSupply(Item item, int threshold, NotificationsHud.Kind kind, int color, long now) {
      if (threshold > 0) {
         int count = 0;

         for (int i = 0; i < ReviveClient.mc.player.getInventory().size(); i++) {
            ItemStack stack = ReviveClient.mc.player.getInventory().getStack(i);
            if (stack.isOf(item)) {
               count += stack.getCount();
            }
         }

         if (count <= threshold) {
            ItemStack stack = new ItemStack(item);
            this.push(stack.getName().getString(), count == 0 ? "Out!" : "x" + count + " left", stack, kind, color, now);
         }
      }
   }

   private void push(String title, String subtitle, ItemStack stack, NotificationsHud.Kind kind, int color, long now) {
      if (now - this.kindCooldowns[kind.ordinal()] >= 8000L) {
         for (NotificationsHud.Toast t : this.toasts) {
            if (t.kind == kind && !t.fading) {
               return;
            }
         }

         this.kindCooldowns[kind.ordinal()] = now;
         this.toasts.add(new NotificationsHud.Toast(title, subtitle, stack, kind, color));

         while (this.toasts.size() > this.maxToasts.get() * 2) {
            this.toasts.remove(0);
         }
      }
   }

   private static enum Kind {
      ARMOR_HEAD,
      ARMOR_CHEST,
      ARMOR_LEGS,
      ARMOR_FEET,
      OBSIDIAN,
      PEARL,
      XP,
      CRYSTAL,
      TOTEM,
      GAPPLE,
      CUSTOM;
   }

   private static final class Toast {
      final String title;
      final String subtitle;
      final ItemStack stack;
      final NotificationsHud.Kind kind;
      final int color;
      final long spawnTimeMs;
      float slideProgress;
      float fadeProgress = 1.0F;
      boolean fading;

      Toast(String title, String subtitle, ItemStack stack, NotificationsHud.Kind kind, int color) {
         this.title = title;
         this.subtitle = subtitle;
         this.stack = stack;
         this.kind = kind;
         this.color = color;
         this.spawnTimeMs = System.currentTimeMillis();
      }
   }
}
