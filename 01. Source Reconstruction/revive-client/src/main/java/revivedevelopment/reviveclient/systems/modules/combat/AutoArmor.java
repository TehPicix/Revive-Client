package revivedevelopment.reviveclient.systems.modules.combat;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Set;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.events.world.TickEvent;
import revivedevelopment.reviveclient.mixininterface.IPlayerInventory;
import revivedevelopment.reviveclient.settings.BoolSetting;
import revivedevelopment.reviveclient.settings.EnchantmentListSetting;
import revivedevelopment.reviveclient.settings.EnumSetting;
import revivedevelopment.reviveclient.settings.IntSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.systems.managers.PacketManager;
import revivedevelopment.reviveclient.systems.modules.Categories;
import revivedevelopment.reviveclient.systems.modules.Module;
import revivedevelopment.reviveclient.systems.modules.Modules;
import revivedevelopment.reviveclient.systems.modules.player.ChestSwap;
import revivedevelopment.reviveclient.utils.Utils;
import revivedevelopment.reviveclient.utils.player.InvUtils;

public class AutoArmor extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<AutoArmor.Protection> preferredProtection = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("preferred-protection"))
                  .description("Which type of protection to prefer."))
               .defaultValue(AutoArmor.Protection.Protection))
            .build()
      );
   private final Setting<Integer> delay = this.sgGeneral
      .add(new IntSetting.Builder().name("swap-delay").description("The delay between equipping armor pieces.").defaultValue(1).min(0).sliderMax(5).build());
   private final Setting<Set<RegistryKey<Enchantment>>> avoidedEnchantments = this.sgGeneral
      .add(
         new EnchantmentListSetting.Builder()
            .name("avoided-enchantments")
            .description("Enchantments that should be avoided.")
            .defaultValue(Enchantments.BINDING_CURSE, Enchantments.FROST_WALKER)
            .build()
      );
   private final Setting<Boolean> blastLeggings = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("blast-prot-leggings")
            .description("Uses blast protection for leggings regardless of preferred protection.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> antiBreak = this.sgGeneral
      .add(new BoolSetting.Builder().name("anti-break").description("Takes off armor if it is about to break.").defaultValue(false).build());
   private final Setting<Boolean> ignoreElytra = this.sgGeneral
      .add(new BoolSetting.Builder().name("ignore-elytra").description("Will not replace your elytra if you have it equipped.").defaultValue(true).build());
   private final Object2IntMap<RegistryEntry<Enchantment>> enchantments = new Object2IntOpenHashMap();
   private final AutoArmor.ArmorPiece[] armorPieces = new AutoArmor.ArmorPiece[4];
   private final AutoArmor.ArmorPiece helmet = new AutoArmor.ArmorPiece(3);
   private final AutoArmor.ArmorPiece chestplate = new AutoArmor.ArmorPiece(2);
   private final AutoArmor.ArmorPiece leggings = new AutoArmor.ArmorPiece(1);
   private final AutoArmor.ArmorPiece boots = new AutoArmor.ArmorPiece(0);
   private int timer;

   public AutoArmor() {
      super(Categories.Combat, "auto-armor", "Automatically equips armor.");
      this.armorPieces[0] = this.helmet;
      this.armorPieces[1] = this.chestplate;
      this.armorPieces[2] = this.leggings;
      this.armorPieces[3] = this.boots;
   }

   @Override
   public void onActivate() {
      this.timer = 0;
   }

   @EventHandler
   private void onPreTick(TickEvent.Pre event) {
      if (this.timer > 0) {
         this.timer--;
      } else {
         for (AutoArmor.ArmorPiece armorPiece : this.armorPieces) {
            armorPiece.reset();
         }

         for (int i = 0; i < ((IPlayerInventory)this.mc.player.getInventory()).revive$getMain().size(); i++) {
            ItemStack itemStack = this.mc.player.getInventory().getStack(i);
            if (!itemStack.isEmpty()
               && this.isArmor(itemStack)
               && (!this.antiBreak.get() || !itemStack.isDamageable() || itemStack.getMaxDamage() - itemStack.getDamage() > 10)) {
               Utils.getEnchantments(itemStack, this.enchantments);
               if (!this.hasAvoidedEnchantment()) {
                  switch (this.getItemSlotId(itemStack)) {
                     case 0:
                        this.boots.add(itemStack, i);
                        break;
                     case 1:
                        this.leggings.add(itemStack, i);
                        break;
                     case 2:
                        this.chestplate.add(itemStack, i);
                        break;
                     case 3:
                        this.helmet.add(itemStack, i);
                  }
               }
            }
         }

         for (AutoArmor.ArmorPiece armorPiece : this.armorPieces) {
            armorPiece.calculate();
         }

         Arrays.sort(this.armorPieces, Comparator.comparingInt(AutoArmor.ArmorPiece::getSortScore));

         for (AutoArmor.ArmorPiece armorPiece : this.armorPieces) {
            armorPiece.apply();
         }
      }
   }

   private boolean hasAvoidedEnchantment() {
      ObjectIterator var1 = this.enchantments.keySet().iterator();

      while (var1.hasNext()) {
         RegistryEntry<Enchantment> enchantment = (RegistryEntry<Enchantment>)var1.next();
         if (enchantment.matches(this.avoidedEnchantments.get()::contains)) {
            return true;
         }
      }

      return false;
   }

   private int getItemSlotId(ItemStack itemStack) {
      if (itemStack.getItem() == Items.ELYTRA) {
         return 2;
      } else {
         EquippableComponent equippable = (EquippableComponent)itemStack.get(DataComponentTypes.EQUIPPABLE);
         return equippable != null ? equippable.slot().getEntitySlotId() : -1;
      }
   }

   private boolean isArmor(ItemStack itemStack) {
      return itemStack.getItem() == Items.ELYTRA ? true : itemStack.get(DataComponentTypes.EQUIPPABLE) != null;
   }

   private int getProtection(ItemStack itemStack) {
      return 0;
   }

   private float getToughness(ItemStack itemStack) {
      return 0.0F;
   }

   private int getScore(ItemStack itemStack) {
      if (itemStack.isEmpty()) {
         return 0;
      } else {
         int score = 0;
         RegistryKey<Enchantment> protection = this.preferredProtection.get().enchantment;
         if (this.isArmor(itemStack) && this.blastLeggings.get() && this.getItemSlotId(itemStack) == 1) {
            protection = Enchantments.BLAST_PROTECTION;
         }

         score += 3 * Utils.getEnchantmentLevel(this.enchantments, protection);
         score += Utils.getEnchantmentLevel(this.enchantments, Enchantments.PROTECTION);
         score += Utils.getEnchantmentLevel(this.enchantments, Enchantments.BLAST_PROTECTION);
         score += Utils.getEnchantmentLevel(this.enchantments, Enchantments.FIRE_PROTECTION);
         score += Utils.getEnchantmentLevel(this.enchantments, Enchantments.PROJECTILE_PROTECTION);
         score += Utils.getEnchantmentLevel(this.enchantments, Enchantments.UNBREAKING);
         score += 2 * Utils.getEnchantmentLevel(this.enchantments, Enchantments.MENDING);
         score += this.getProtection(itemStack);
         return score + (int)this.getToughness(itemStack);
      }
   }

   private boolean cannotSwap() {
      return this.timer > 0 || !ReviveClient.PACKET.canSend(PacketManager.Category.CLICK_SLOT);
   }

   private void swap(int from, int armorSlotId) {
      InvUtils.move().from(from).toArmor(armorSlotId);
      this.timer = this.delay.get();
   }

   private void moveToEmpty(int armorSlotId) {
      for (int i = 0; i < ((IPlayerInventory)this.mc.player.getInventory()).revive$getMain().size(); i++) {
         if (this.mc.player.getInventory().getStack(i).isEmpty()) {
            InvUtils.move().fromArmor(armorSlotId).to(i);
            this.timer = this.delay.get();
            break;
         }
      }
   }

   private class ArmorPiece {
      private final int id;
      private int bestSlot;
      private int bestScore;
      private int score;
      private int durability;

      public ArmorPiece(int id) {
         this.id = id;
      }

      public void reset() {
         this.bestSlot = -1;
         this.bestScore = -1;
         this.score = -1;
         this.durability = Integer.MAX_VALUE;
      }

      public void add(ItemStack itemStack, int slot) {
         int score = AutoArmor.this.getScore(itemStack);
         if (score > this.bestScore) {
            this.bestScore = score;
            this.bestSlot = slot;
         }
      }

      public void calculate() {
         if (!AutoArmor.this.cannotSwap()) {
            ItemStack itemStack = (ItemStack)((IPlayerInventory)AutoArmor.this.mc.player.getInventory()).revive$getArmor().get(this.id);
            if ((AutoArmor.this.ignoreElytra.get() || Modules.get().isActive(ChestSwap.class)) && itemStack.getItem() == Items.ELYTRA) {
               this.score = Integer.MAX_VALUE;
            } else {
               Utils.getEnchantments(itemStack, AutoArmor.this.enchantments);
               if (AutoArmor.this.enchantments.containsKey(Enchantments.BINDING_CURSE)) {
                  this.score = Integer.MAX_VALUE;
               } else {
                  this.score = AutoArmor.this.getScore(itemStack);
                  this.score = this.decreaseScoreByAvoidedEnchantments(this.score);
                  this.score = this.applyAntiBreakScore(this.score, itemStack);
                  if (!itemStack.isEmpty()) {
                     this.durability = itemStack.getMaxDamage() - itemStack.getDamage();
                  }
               }
            }
         }
      }

      public int getSortScore() {
         return AutoArmor.this.antiBreak.get() && this.durability <= 10 ? -1 : this.bestScore;
      }

      public void apply() {
         if (!AutoArmor.this.cannotSwap() && this.score != Integer.MAX_VALUE) {
            if (this.bestScore > this.score) {
               AutoArmor.this.swap(this.bestSlot, this.id);
            } else if (AutoArmor.this.antiBreak.get() && this.durability <= 10) {
               AutoArmor.this.moveToEmpty(this.id);
            }
         }
      }

      private int decreaseScoreByAvoidedEnchantments(int score) {
         for (RegistryKey<Enchantment> enchantment : AutoArmor.this.avoidedEnchantments.get()) {
            score -= 2 * AutoArmor.this.enchantments.getInt(enchantment);
         }

         return score;
      }

      private int applyAntiBreakScore(int score, ItemStack itemStack) {
         return AutoArmor.this.antiBreak.get() && itemStack.isDamageable() && itemStack.getMaxDamage() - itemStack.getDamage() <= 10 ? -1 : score;
      }
   }

   public static enum Protection {
      Protection(Enchantments.PROTECTION),
      BlastProtection(Enchantments.BLAST_PROTECTION),
      FireProtection(Enchantments.FIRE_PROTECTION),
      ProjectileProtection(Enchantments.PROJECTILE_PROTECTION);

      private final RegistryKey<Enchantment> enchantment;

      private Protection(RegistryKey<Enchantment> enchantment) {
         this.enchantment = enchantment;
      }
   }
}
