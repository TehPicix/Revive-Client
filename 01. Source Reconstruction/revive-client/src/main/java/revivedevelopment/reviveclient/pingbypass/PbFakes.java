package revivedevelopment.reviveclient.pingbypass;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.authlib.GameProfile;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.utils.entity.fakeplayer.FakePlayerEntity;
import revivedevelopment.reviveclient.utils.entity.fakeplayer.FakePlayerManager;
import revivedevelopment.reviveclient.utils.misc.ItemNbt;

public final class PbFakes {
   private static final int MAX_FAKES = 64;
   private static final int MAX_ITEM_BYTES = 16384;
   private static final EquipmentSlot[] GEAR = new EquipmentSlot[]{
      EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND
   };
   private static final List<RegistryEntry<EntityAttribute>> ATTRIBUTES = List.of(
      EntityAttributes.ARMOR, EntityAttributes.ARMOR_TOUGHNESS, EntityAttributes.MAX_HEALTH, EntityAttributes.MAX_ABSORPTION, EntityAttributes.SCALE
   );
   private static volatile int worldEntries;
   private static volatile int logins;

   private PbFakes() {
   }

   public static void onWorldEntered() {
      worldEntries++;
   }

   public static int worldCount() {
      return worldEntries;
   }

   public static void onLoggedIn() {
      worldEntries = 0;
      logins++;
   }

   public static String dimension(World world) {
      return world.getRegistryKey().getValue().toString();
   }

   @Nullable
   public static FakePlayerEntity find(@Nullable String uuid) {
      if (uuid != null && !uuid.isEmpty()) {
         for (FakePlayerEntity fake : FakePlayerManager.getFakePlayers()) {
            if (fake.getUuidAsString().equals(uuid)) {
               return fake;
            }
         }

         return null;
      } else {
         return null;
      }
   }

   private static String encodeGear(FakePlayerEntity fake) {
      NbtCompound tag = new NbtCompound();

      for (EquipmentSlot slot : GEAR) {
         ItemStack stack = fake.getEquippedStack(slot);
         if (!stack.isEmpty() && ItemNbt.byteSize(stack) <= 16384) {
            NbtElement nbt = ItemNbt.encode(stack);
            if (nbt != null) {
               tag.put(slot.getName(), nbt);
            }
         }
      }

      if (tag.isEmpty()) {
         return "";
      } else {
         try {
            return PbModules.encode(tag);
         } catch (Exception var8) {
            ReviveClient.LOG.warn("[PingBypass] could not encode a fake player's gear", var8);
            return "";
         }
      }
   }

   public static final class Mirror {
      private static int nextId = -1000000;
      @Nullable
      private JsonObject wanted;
      private int wantedLogin;
      private boolean pending;
      private final Map<UUID, FakePlayerEntity> copies = new HashMap<>();
      private final Map<UUID, String> gearShown = new HashMap<>();
      @Nullable
      private Object session;
      private int hits;
      private WeakReference<World> world = new WeakReference<>(null);

      public void apply(JsonObject msg) {
         this.checkWorld();
         if (!(num(msg, "w", -1.0) < PbFakes.worldEntries)) {
            this.wanted = msg;
            this.wantedLogin = PbFakes.logins;
            this.pending = true;
            if (msg.get("dmg") instanceof JsonPrimitive dmg && dmg.isBoolean()) {
               FakePlayerManager.takeDamage = dmg.getAsBoolean();
            }

            this.syncSafely();
         }
      }

      public void tick() {
         MinecraftClient mc = MinecraftClient.getInstance();
         this.checkWorld();
         if (!this.pending) {
            for (FakePlayerEntity copy : this.copies.values()) {
               if (!alive(mc, copy)) {
                  this.pending = true;
               }
            }
         }

         if (this.pending) {
            this.syncSafely();
         }
      }

      private void checkWorld() {
         World current = MinecraftClient.getInstance().world;
         if (this.world.get() != current) {
            this.world = new WeakReference<>(current);
            this.removeAll();
            this.pending = this.wanted != null;
         }
      }

      public void reset(@Nullable Object session) {
         this.session = session;
         this.wanted = null;
         this.pending = false;
         this.removeAll();
         this.hits = 0;
         FakePlayerManager.takeDamage = true;
      }

      @Nullable
      public JsonObject hit(FakePlayerEntity fake, float amount, Object session) {
         if (session == this.session && this.copies.get(fake.getUuid()) == fake) {
            JsonObject msg = PbProtocol.message("fake_hit");
            msg.addProperty("u", fake.getUuidAsString());
            msg.addProperty("d", amount);
            msg.addProperty("s", ++this.hits);
            return msg;
         } else {
            return null;
         }
      }

      private void syncSafely() {
         try {
            this.sync();
         } catch (RuntimeException var2) {
            ReviveClient.LOG.warn("[PingBypass] dropped a fake players message it could not read", var2);
            this.wanted = null;
            this.pending = false;
            this.removeAll();
         }
      }

      private void sync() {
         MinecraftClient mc = MinecraftClient.getInstance();
         if (this.wanted != null && this.wantedLogin != PbFakes.logins) {
            this.wanted = null;
         }

         JsonObject msg = this.wanted;
         if (msg != null && mc.world != null && mc.player != null) {
            int entries = (int)num(msg, "w", -1.0);
            if (entries < PbFakes.worldEntries) {
               this.wanted = null;
               this.pending = false;
               this.removeAll();
            } else {
               String dim = msg.get("dim") instanceof JsonPrimitive p && p.isString() ? p.getAsString() : "";
               if (entries <= PbFakes.worldEntries && PbFakes.dimension(mc.world).equals(dim)) {
                  this.pending = false;
                  JsonArray list = msg.get("list") instanceof JsonArray a ? a : new JsonArray();
                  boolean healthCurrent = num(msg, "ack", -1.0) >= this.hits;
                  Set<UUID> listed = new HashSet<>();

                  for (JsonElement element : list) {
                     if (listed.size() >= 64) {
                        break;
                     }

                     if (element instanceof JsonObject e) {
                        try {
                           UUID uuid = uuid(e.get("u"));
                           if (uuid != null && listed.add(uuid)) {
                              FakePlayerEntity copy = this.copies.get(uuid);
                              if (copy != null && alive(mc, copy)) {
                                 this.update(copy, e, healthCurrent);
                              } else {
                                 if (copy != null) {
                                    drop(mc, copy);
                                 }

                                 this.copies.put(uuid, this.spawn(mc, uuid, e));
                              }
                           }
                        } catch (RuntimeException var13) {
                           ReviveClient.LOG.warn("[PingBypass] could not copy a fake player", var13);
                        }
                     }
                  }

                  Iterator<Entry<UUID, FakePlayerEntity>> it = this.copies.entrySet().iterator();

                  while (it.hasNext()) {
                     Entry<UUID, FakePlayerEntity> entry = it.next();
                     if (!listed.contains(entry.getKey()) || !alive(mc, entry.getValue())) {
                        drop(mc, entry.getValue());
                        it.remove();
                     }
                  }

                  this.gearShown.keySet().retainAll(this.copies.keySet());
               } else {
                  this.removeAll();
               }
            }
         } else {
            this.removeAll();
            this.pending = msg != null;
         }
      }

      private FakePlayerEntity spawn(MinecraftClient mc, UUID uuid, JsonObject e) {
         String name = e.get("n") instanceof JsonPrimitive p ? p.getAsString() : "FakePlayer";
         if (name.length() > 64) {
            name = name.substring(0, 64);
         }

         FakePlayerEntity copy = new FakePlayerEntity(mc.player, new GameProfile(uuid, name), 20.0F, false);
         copy.setId(nextId--);
         this.gearShown.remove(uuid);
         this.update(copy, e, true);
         copy.spawn();
         FakePlayerManager.getFakePlayers().add(copy);
         return copy;
      }

      private void update(FakePlayerEntity copy, JsonObject e, boolean health) {
         double x = num(e, "x", copy.getX());
         double y = num(e, "y", copy.getY());
         double z = num(e, "z", copy.getZ());
         float yaw = (float)num(e, "yaw", copy.getYaw());
         float pitch = (float)num(e, "pitch", copy.getPitch());
         if (Double.isFinite(x)
            && Double.isFinite(y)
            && Double.isFinite(z)
            && Float.isFinite(yaw)
            && Float.isFinite(pitch)
            && (x != copy.getX() || y != copy.getY() || z != copy.getZ() || yaw != copy.getYaw() || pitch != copy.getPitch())) {
            copy.refreshPositionAndAngles(x, y, z, yaw, pitch);
         }

         float headYaw = (float)num(e, "hy", copy.getYaw());
         if (Float.isFinite(headYaw)) {
            copy.headYaw = copy.lastHeadYaw = headYaw;
            copy.bodyYaw = copy.lastBodyYaw = headYaw;
         }

         if (e.get("pose") instanceof JsonPrimitive p && p.isString()) {
            try {
               EntityPose pose = EntityPose.valueOf(p.getAsString());
               if (copy.getPose() != pose) {
                  copy.setPose(pose);
               }
            } catch (IllegalArgumentException var20) {
            }
         }

         copy.weak = e.get("weak") instanceof JsonPrimitive weak && weak.isBoolean() && weak.getAsBoolean();
         String gear = e.get("gear") instanceof JsonPrimitive g && g.isString() ? g.getAsString() : "";
         if (!gear.equals(this.gearShown.get(copy.getUuid()))) {
            wear(copy, gear);
            this.gearShown.put(copy.getUuid(), gear);
         }

         JsonArray attr = e.get("attr") instanceof JsonArray a ? a : new JsonArray();

         for (int i = 0; i < PbFakes.ATTRIBUTES.size() && i < attr.size(); i++) {
            if (attr.get(i) instanceof JsonPrimitive p && p.isNumber()) {
               double value = p.getAsDouble();
               EntityAttributeInstance instance = copy.getAttributeInstance(PbFakes.ATTRIBUTES.get(i));
               if (instance != null && Double.isFinite(value) && instance.getValue() != value) {
                  instance.clearModifiers();
                  instance.setBaseValue(value);
               }
            }
         }

         if (health) {
            float hp = (float)num(e, "hp", copy.getHealth());
            float absorption = (float)num(e, "ab", copy.getAbsorptionAmount());
            if (Float.isFinite(hp)) {
               copy.setHealth(hp);
            }

            if (Float.isFinite(absorption)) {
               copy.setAbsorptionAmount(absorption);
            }

            copy.totemPops = (int)num(e, "pops", copy.totemPops);
         }
      }

      private static void wear(FakePlayerEntity copy, String gear) {
         MinecraftClient mc = MinecraftClient.getInstance();
         NbtCompound tag = new NbtCompound();
         if (!gear.isEmpty()) {
            try {
               tag = PbModules.decode(gear);
            } catch (Exception var10) {
               ReviveClient.LOG.warn("[PingBypass] could not read a fake player's gear", var10);
            }
         }

         for (EquipmentSlot slot : PbFakes.GEAR) {
            ItemStack stack = ItemStack.EMPTY;
            NbtElement nbt = tag.get(slot.getName());
            if (nbt != null && mc.player != null) {
               stack = ItemStack.CODEC.parse(mc.player.getRegistryManager().getOps(NbtOps.INSTANCE), nbt).result().orElse(ItemStack.EMPTY);
            }

            copy.equipStack(slot, stack);
         }
      }

      private static boolean alive(MinecraftClient mc, FakePlayerEntity copy) {
         return !copy.isRemoved() && mc.world != null && copy.getEntityWorld() == mc.world && FakePlayerManager.contains(copy);
      }

      private void removeAll() {
         if (!this.copies.isEmpty()) {
            MinecraftClient mc = MinecraftClient.getInstance();
            this.copies.values().forEach(copy -> drop(mc, copy));
            this.copies.clear();
            this.gearShown.clear();
         }
      }

      private static void drop(MinecraftClient mc, FakePlayerEntity copy) {
         FakePlayerManager.getFakePlayers().removeIf(fake -> fake == copy);
         if (!copy.isRemoved() && mc.world != null && copy.getEntityWorld() == mc.world) {
            copy.despawn();
         }
      }

      @Nullable
      private static UUID uuid(@Nullable JsonElement e) {
         if (e instanceof JsonPrimitive p && p.isString()) {
            try {
               return UUID.fromString(p.getAsString());
            } catch (IllegalArgumentException var3) {
               return null;
            }
         } else {
            return null;
         }
      }

      private static double num(JsonObject o, String key, double fallback) {
         return o.get(key) instanceof JsonPrimitive p && p.isNumber() ? p.getAsDouble() : fallback;
      }
   }

   public static final class Sender {
      private final Map<UUID, PbFakes.Sender.Gear> gear = new HashMap<>();

      public void reset() {
         this.gear.clear();
      }

      public JsonObject write(World world, int ack) {
         JsonObject msg = PbProtocol.message("fakes");
         msg.addProperty("w", PbFakes.worldEntries);
         msg.addProperty("dim", PbFakes.dimension(world));
         msg.addProperty("dmg", FakePlayerManager.takeDamage);
         msg.addProperty("ack", ack);
         JsonArray list = new JsonArray();
         Set<UUID> listed = new HashSet<>();

         for (FakePlayerEntity fake : FakePlayerManager.getFakePlayers()) {
            if (list.size() >= 64) {
               break;
            }

            if (!fake.isRemoved() && fake.getEntityWorld() == world && listed.add(fake.getUuid())) {
               JsonObject e = new JsonObject();
               e.addProperty("u", fake.getUuidAsString());
               e.addProperty("n", fake.getGameProfile().name());
               e.addProperty("x", fake.getX());
               e.addProperty("y", fake.getY());
               e.addProperty("z", fake.getZ());
               e.addProperty("yaw", fake.getYaw());
               e.addProperty("pitch", fake.getPitch());
               e.addProperty("hy", fake.headYaw);
               e.addProperty("pose", fake.getPose().name());
               e.addProperty("hp", fake.getHealth());
               e.addProperty("ab", fake.getAbsorptionAmount());
               e.addProperty("pops", fake.totemPops);
               e.addProperty("weak", fake.weak);
               JsonArray attr = new JsonArray();

               for (RegistryEntry<EntityAttribute> attribute : PbFakes.ATTRIBUTES) {
                  EntityAttributeInstance instance = fake.getAttributeInstance(attribute);
                  if (instance != null) {
                     attr.add(instance.getValue());
                  } else {
                     attr.add(JsonNull.INSTANCE);
                  }
               }

               e.add("attr", attr);
               String encoded = this.gear(fake);
               if (!encoded.isEmpty()) {
                  e.addProperty("gear", encoded);
               }

               list.add(e);
            }
         }

         this.gear.keySet().retainAll(listed);
         msg.add("list", list);
         return msg;
      }

      private String gear(FakePlayerEntity fake) {
         StringBuilder key = new StringBuilder();

         for (EquipmentSlot slot : PbFakes.GEAR) {
            ItemStack stack = fake.getEquippedStack(slot);
            key.append(System.identityHashCode(stack)).append(':').append(stack.getCount()).append(':').append(stack.getDamage()).append(';');
         }

         String k = key.toString();
         PbFakes.Sender.Gear cached = this.gear.get(fake.getUuid());
         if (cached != null && cached.key().equals(k)) {
            return cached.encoded();
         } else {
            String encoded = PbFakes.encodeGear(fake);
            this.gear.put(fake.getUuid(), new PbFakes.Sender.Gear(k, encoded));
            return encoded;
         }
      }

      private record Gear(String key, String encoded) {
      }
   }
}
