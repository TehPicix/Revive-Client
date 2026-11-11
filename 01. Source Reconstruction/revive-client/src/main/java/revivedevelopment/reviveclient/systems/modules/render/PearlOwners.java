package revivedevelopment.reviveclient.systems.modules.render;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LazyEntityReference;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import org.joml.Matrix3x2fStack;
import org.joml.Vector3d;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.events.entity.EntityAddedEvent;
import revivedevelopment.reviveclient.events.game.GameJoinedEvent;
import revivedevelopment.reviveclient.events.packets.PacketEvent;
import revivedevelopment.reviveclient.events.render.Render2DEvent;
import revivedevelopment.reviveclient.events.world.TickEvent;
import revivedevelopment.reviveclient.mixin.ProjectileOwnerAccessor;
import revivedevelopment.reviveclient.settings.BoolSetting;
import revivedevelopment.reviveclient.settings.ColorSetting;
import revivedevelopment.reviveclient.settings.DoubleSetting;
import revivedevelopment.reviveclient.settings.IntSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.systems.modules.Categories;
import revivedevelopment.reviveclient.systems.modules.Module;
import revivedevelopment.reviveclient.utils.Utils;
import revivedevelopment.reviveclient.utils.network.Http;
import revivedevelopment.reviveclient.utils.player.ChatUtils;
import revivedevelopment.reviveclient.utils.render.NametagUtils;
import revivedevelopment.reviveclient.utils.render.color.Color;
import revivedevelopment.reviveclient.utils.render.color.SettingColor;

public class PearlOwners extends Module {
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   private static final Type SAVE_TYPE = (new TypeToken<Map<String, String>>() {}).getType();
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgRender = this.settings.createGroup("Render");
   private final Setting<Boolean> chatOnSpawn = this.sgGeneral
      .add(new BoolSetting.Builder().name("chat-on-spawn").description("Say in chat whose pearl it is when a pearl appears.").defaultValue(false).build());
   private final Setting<Boolean> lookupMojang = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("mojang-lookup")
            .description("Ask Mojang's session server for the name behind a thrower UUID that isn't on the tab list (one request per unknown player).")
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> useTeam = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("team-sync")
            .description("Name pearls using what your Team Sync teammates know: their own pearls, and pearls of players they can see.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> remember = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("remember")
            .description("Save resolved owners to disk so a pearl stays labelled after its thrower goes out of range or you relog.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> showUnknown = this.sgRender
      .add(
         new BoolSetting.Builder()
            .name("show-unknown")
            .description("Label pearls whose thrower couldn't be worked out too (shows the thrower's entity id when the server sent one).")
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> showDistance = this.sgRender
      .add(new BoolSetting.Builder().name("distance").description("Append how far away the pearl is.").defaultValue(true).build());
   private final Setting<Integer> range = this.sgRender
      .add(new IntSetting.Builder().name("range").description("Only label pearls within this many blocks.").defaultValue(128).sliderRange(8, 512).build());
   private final Setting<Double> scale = this.sgRender
      .add(new DoubleSetting.Builder().name("scale").description("Label size.").defaultValue(1.0).min(0.1).sliderRange(0.1, 3.0).build());
   private final Setting<SettingColor> nameColor = this.sgRender
      .add(new ColorSetting.Builder().name("name-color").defaultValue(new SettingColor(190, 90, 255)).build());
   private final Setting<SettingColor> unknownColor = this.sgRender
      .add(new ColorSetting.Builder().name("unknown-color").defaultValue(new SettingColor(170, 170, 170)).visible(this.showUnknown::get).build());
   private final Setting<SettingColor> background = this.sgRender
      .add(new ColorSetting.Builder().name("background").defaultValue(new SettingColor(0, 0, 0, 110)).build());
   private final Map<Integer, Integer> pearlOwnerIds = new ConcurrentHashMap<>();
   private final Map<Integer, String> playerNames = new ConcurrentHashMap<>();
   private final Map<String, String> remembered = new ConcurrentHashMap<>();
   private final Map<Integer, Boolean> announced = new HashMap<>();
   private final Map<UUID, String> uuidNames = new ConcurrentHashMap<>();
   private final Set<UUID> pending = ConcurrentHashMap.newKeySet();
   private final Set<UUID> failed = ConcurrentHashMap.newKeySet();
   private static final ExecutorService LOOKUPS = Executors.newSingleThreadExecutor(r -> {
      Thread t = new Thread(r, "revive-pearl-owner-lookup");
      t.setDaemon(true);
      return t;
   });
   private boolean dirty;
   private int saveTimer;
   private String loadedWorld;
   private final Vector3d pos = new Vector3d();

   public PearlOwners() {
      super(Categories.Render, "pearl-owners", "Shows who threw each loaded ender pearl.");
   }

   @Override
   public void onActivate() {
      this.loadedWorld = null;
      if (this.mc.world != null) {
         for (Entity e : this.mc.world.getEntities()) {
            if (e instanceof PlayerEntity p) {
               this.playerNames.put(p.getId(), p.getGameProfile().name());
            }
         }
      }
   }

   @Override
   public void onDeactivate() {
      this.flush();
      this.pearlOwnerIds.clear();
      this.playerNames.clear();
      this.announced.clear();
   }

   @EventHandler
   private void onGameJoined(GameJoinedEvent event) {
      this.flush();
      this.pearlOwnerIds.clear();
      this.playerNames.clear();
      this.announced.clear();
      this.loadedWorld = null;
   }

   @EventHandler
   private void onPacketReceive(PacketEvent.Receive event) {
      if (event.packet instanceof EntitySpawnS2CPacket spawn) {
         if (spawn.getEntityType() == EntityType.ENDER_PEARL) {
            this.pearlOwnerIds.put(spawn.getEntityId(), spawn.getEntityData());
         } else if (spawn.getEntityType() == EntityType.PLAYER && this.mc.getNetworkHandler() != null) {
            PlayerListEntry entry = this.mc.getNetworkHandler().getPlayerListEntry(spawn.getUuid());
            if (entry != null) {
               this.playerNames.put(spawn.getEntityId(), entry.getProfile().name());
            }
         }
      }
   }

   @EventHandler
   private void onEntityAdded(EntityAddedEvent event) {
      if (event.entity instanceof PlayerEntity p) {
         this.playerNames.put(p.getId(), p.getGameProfile().name());
      }
   }

   @EventHandler
   private void onTick(TickEvent.Post event) {
      if (this.mc.world != null) {
         this.ensureLoaded();
         if (++this.saveTimer >= 100) {
            this.saveTimer = 0;
            this.flush();
         }

         if (this.chatOnSpawn.get()) {
            for (Entity e : this.mc.world.getEntities()) {
               if (e instanceof EnderPearlEntity pearl && !this.announced.containsKey(pearl.getId())) {
                  this.announced.put(pearl.getId(), true);
                  String owner = this.resolve(pearl);
                  if (owner == null) {
                     owner = "someone unknown";
                  }

                  ChatUtils.infoPrefix(
                     "Pearl Owners",
                     "(highlight)%s(default)'s pearl at (highlight)%d, %d, %d(default).",
                     owner,
                     pearl.getBlockPos().getX(),
                     pearl.getBlockPos().getY(),
                     pearl.getBlockPos().getZ()
                  );
               }
            }
         }
      }
   }

   @EventHandler
   private void onRender2D(Render2DEvent event) {
      if (this.mc.world != null && this.mc.player != null) {
         this.ensureLoaded();
         double rangeSq = (double)this.range.get().intValue() * this.range.get().intValue();
         DrawContext ctx = event.drawContext;

         for (Entity e : this.mc.world.getEntities()) {
            if (e instanceof EnderPearlEntity pearl && !(this.mc.player.squaredDistanceTo(pearl) > rangeSq)) {
               String owner = this.resolve(pearl);
               Color color = this.nameColor.get();
               String text;
               if (owner != null) {
                  text = owner;
               } else {
                  if (!this.showUnknown.get()) {
                     continue;
                  }

                  Integer id = this.pearlOwnerIds.get(pearl.getId());
                  text = id != null && id > 0 ? "Unknown #" + id : "Unknown";
                  color = this.unknownColor.get();
               }

               if (this.showDistance.get()) {
                  text = text + String.format(" %.0fm", this.mc.player.distanceTo(pearl));
               }

               Utils.set(this.pos, pearl, event.tickDelta);
               this.pos.add(0.0, 0.6, 0.0);
               if (NametagUtils.to2D(this.pos, this.scale.get())) {
                  NametagUtils.begin(this.pos, ctx);
                  double w = (this.mc.textRenderer.getWidth(text) + 1) * 2;
                  double h = (9 + 1) * 2;
                  double x = -w / 2.0;
                  double y = -h;
                  ctx.fill(
                     (int)Math.round(x - 1.0),
                     (int)Math.round(y - 1.0),
                     (int)Math.round(x + w + 1.0),
                     (int)Math.round(y + h + 1.0),
                     this.background.get().getPacked()
                  );
                  Matrix3x2fStack m = ctx.getMatrices();
                  m.pushMatrix();
                  m.scale(2.0F, 2.0F);
                  ctx.drawText(this.mc.textRenderer, text, (int)Math.round(x / 2.0), (int)Math.round(y / 2.0), color.getPacked(), true);
                  m.popMatrix();
                  NametagUtils.end(ctx);
               }
            }
         }
      }
   }

   public String resolve(EnderPearlEntity pearl) {
      String key = pearl.getUuid().toString();
      if (pearl.getOwner() instanceof PlayerEntity p) {
         return this.rememberName(key, p.getGameProfile().name());
      } else {
         LazyEntityReference<Entity> ref = ((ProjectileOwnerAccessor)pearl).getOwnerReference();
         if (ref != null) {
            UUID uuid = ref.getUuid();
            if (uuid != null) {
               String name = this.uuidNames.get(uuid);
               if (name == null && this.mc.getNetworkHandler() != null) {
                  PlayerListEntry entry = this.mc.getNetworkHandler().getPlayerListEntry(uuid);
                  if (entry != null) {
                     this.uuidNames.put(uuid, name = entry.getProfile().name());
                  }
               }

               if (name == null) {
                  name = this.remembered.get("uuid:" + uuid);
               }

               if (name != null) {
                  return this.rememberName(key, name);
               }

               if (this.lookupMojang.get()) {
                  this.lookupMojang(uuid);
               }
            }
         }

         Integer ownerId = this.pearlOwnerIds.get(pearl.getId());
         if (ownerId != null && ownerId > 0) {
            if (this.mc.world.getEntityById(ownerId) instanceof PlayerEntity p) {
               return this.rememberName(key, p.getGameProfile().name());
            }

            String seen = this.playerNames.get(ownerId);
            if (seen != null) {
               return this.rememberName(key, seen);
            }

            if (this.useTeam.get() && ReviveClient.TEAM != null) {
               String shared = ReviveClient.TEAM.nameForEntityId(ownerId);
               if (shared != null) {
                  return this.rememberName(key, shared);
               }
            }
         }

         return this.remembered.get(key);
      }
   }

   private void lookupMojang(UUID uuid) {
      if (!this.failed.contains(uuid) && this.pending.add(uuid)) {
         LOOKUPS.submit(
            () -> {
               try {
                  HttpResponse<PearlOwners.Profile> res = Http.get(
                        "https://sessionserver.mojang.com/session/minecraft/profile/" + uuid.toString().replace("-", "")
                     )
                     .exceptionHandler(ex -> ReviveClient.LOG.warn("Pearl Owners: session server lookup failed for {}", uuid))
                     .sendJsonResponse(PearlOwners.Profile.class);
                  if (res != null && res.statusCode() == 200 && res.body() != null && res.body().name != null) {
                     this.uuidNames.put(uuid, res.body().name);
                     this.remembered.put("uuid:" + uuid, res.body().name);
                     this.dirty = true;
                  } else {
                     this.failed.add(uuid);
                  }
               } catch (Exception var6) {
                  this.failed.add(uuid);
               } finally {
                  this.pending.remove(uuid);
               }
            }
         );
      }
   }

   private String rememberName(String key, String name) {
      if (this.remember.get() && !name.equals(this.remembered.put(key, name))) {
         this.dirty = true;
      }

      return name;
   }

   private File file() {
      return new File(new File(ReviveClient.FOLDER, "pearl-owners"), Utils.getFileWorldName() + ".json");
   }

   private void ensureLoaded() {
      String world = Utils.getFileWorldName();
      if (!world.equals(this.loadedWorld)) {
         this.flush();
         this.loadedWorld = world;
         this.remembered.clear();
         File f = this.file();
         if (f.exists()) {
            try (FileReader r = new FileReader(f)) {
               Map<String, String> m = (Map<String, String>)GSON.fromJson(r, SAVE_TYPE);
               if (m != null) {
                  this.remembered.putAll(m);
               }
            } catch (Exception var8) {
               ReviveClient.LOG.warn("Pearl Owners: couldn't read {}", f, var8);
            }
         }
      }
   }

   private void flush() {
      if (this.dirty && this.loadedWorld != null) {
         this.dirty = false;
         File f = this.file();
         f.getParentFile().mkdirs();

         try (FileWriter w = new FileWriter(f)) {
            GSON.toJson(this.remembered, SAVE_TYPE, w);
         } catch (IOException var7) {
            ReviveClient.LOG.warn("Pearl Owners: couldn't write {}", f, var7);
         }
      }
   }

   @Override
   public String getInfoString() {
      if (this.mc.world == null) {
         return null;
      } else {
         int n = 0;

         for (Entity e : this.mc.world.getEntities()) {
            if (e instanceof EnderPearlEntity) {
               n++;
            }
         }

         return n == 0 ? null : String.valueOf(n);
      }
   }

   private static class Profile {
      String id;
      String name;
   }
}
