package revivedevelopment.reviveclient.systems.modules.combat;

import java.util.function.Predicate;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.tag.ItemTags;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.events.entity.player.AttackEntityEvent;
import revivedevelopment.reviveclient.events.entity.player.PostAttackEntityEvent;
import revivedevelopment.reviveclient.events.world.TickEvent;
import revivedevelopment.reviveclient.settings.BoolSetting;
import revivedevelopment.reviveclient.settings.DoubleSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.systems.managers.SwapManager;
import revivedevelopment.reviveclient.systems.modules.Categories;
import revivedevelopment.reviveclient.systems.modules.Module;
import revivedevelopment.reviveclient.utils.Utils;
import revivedevelopment.reviveclient.utils.player.FindItemResult;
import revivedevelopment.reviveclient.utils.player.InvUtils;

public class BreachSwap extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Boolean> axes = this.sgGeneral
      .add(new BoolSetting.Builder().name("axes").description("Also swap when hitting with an axe.").defaultValue(true).build());
   private final Setting<Boolean> keepShieldBreak = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("keep-shield-break")
            .description("Don't swap an axe hit on someone holding up a shield, so the axe still disables it.")
            .defaultValue(true)
            .visible(this.axes::get)
            .build()
      );
   private final Setting<Boolean> requireEnchant = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("require-breach-or-density")
            .description("Only swap to a mace with Breach or Density. Off = any mace.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Double> minCharge = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("min-charge")
            .description("Only swap when your attack is at least this charged (1 = full).")
            .defaultValue(0.9)
            .min(0.0)
            .max(1.0)
            .sliderRange(0.0, 1.0)
            .build()
      );
   private final Setting<Boolean> playersOnly = this.sgGeneral
      .add(new BoolSetting.Builder().name("players-only").description("Only swap when hitting players.").defaultValue(true).build());
   private boolean swapping;

   public BreachSwap() {
      super(Categories.Combat, "breach-swap", "Swaps to your mace for sword/axe hits so they get Breach, Density and smash damage.");
   }

   @Override
   public void onDeactivate() {
      this.release();
   }

   @EventHandler(
      priority = -100
   )
   private void onAttack(AttackEntityEvent event) {
      if (!event.isCancelled() && !this.swapping && this.mc.player != null) {
         if (event.entity instanceof LivingEntity target) {
            if (!this.playersOnly.get() || target instanceof PlayerEntity) {
               ItemStack held = this.mc.player.getMainHandStack();
               boolean sword = held.isIn(ItemTags.SWORDS);
               boolean axe = held.getItem() instanceof AxeItem;
               if (sword || axe && this.axes.get()) {
                  if (!axe || !this.keepShieldBreak.get() || !target.isBlocking()) {
                     if (!(this.mc.player.getAttackCooldownProgress(0.5F) < this.minCharge.get())) {
                        FindItemResult mace = this.findMace();
                        if (mace.found() && !mace.isMainHand() && !mace.isOffhand()) {
                           this.swapping = ReviveClient.SWAP.beginSwap(mace, true);
                        }
                     }
                  }
               }
            }
         }
      }
   }

   @EventHandler
   private void onPostAttack(PostAttackEntityEvent event) {
      this.release();
   }

   @EventHandler
   private void onTick(TickEvent.Pre event) {
      this.release();
   }

   private void release() {
      if (this.swapping) {
         this.swapping = false;
         ReviveClient.SWAP.endSwap(true);
      }
   }

   private FindItemResult findMace() {
      FindItemResult r = this.findMaceWith(Enchantments.BREACH);
      if (!r.found()) {
         r = this.findMaceWith(Enchantments.DENSITY);
      }

      if (!r.found() && !this.requireEnchant.get()) {
         r = this.pick(s -> s.isOf(Items.MACE));
      }

      return r;
   }

   private FindItemResult findMaceWith(RegistryKey<Enchantment> key) {
      return this.pick(s -> s.isOf(Items.MACE) && Utils.getEnchantmentLevel(s, key) > 0);
   }

   private FindItemResult pick(Predicate<ItemStack> good) {
      FindItemResult r = InvUtils.findInHotbar(good);
      if (r.found()) {
         return r;
      } else {
         return ReviveClient.SWAP.getItemSwapMode() == SwapManager.SwapMode.SilentHotbar ? r : InvUtils.find(good);
      }
   }
}
