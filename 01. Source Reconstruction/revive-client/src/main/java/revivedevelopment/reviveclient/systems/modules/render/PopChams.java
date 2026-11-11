package revivedevelopment.reviveclient.systems.modules.render;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import revivedevelopment.reviveclient.events.entity.PlayerDeathEvent;
import revivedevelopment.reviveclient.events.game.GameLeftEvent;
import revivedevelopment.reviveclient.events.render.Render3DEvent;
import revivedevelopment.reviveclient.events.render.SubmitEntitiesEvent;
import revivedevelopment.reviveclient.renderer.ShapeMode;
import revivedevelopment.reviveclient.settings.BoolSetting;
import revivedevelopment.reviveclient.settings.ColorSetting;
import revivedevelopment.reviveclient.settings.DoubleSetting;
import revivedevelopment.reviveclient.settings.EnumSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.systems.friends.Friends;
import revivedevelopment.reviveclient.systems.modules.Categories;
import revivedevelopment.reviveclient.systems.modules.Module;
import revivedevelopment.reviveclient.utils.Utils;
import revivedevelopment.reviveclient.utils.entity.fakeplayer.FakePlayerEntity;
import revivedevelopment.reviveclient.utils.render.SkinEntityRenderer;
import revivedevelopment.reviveclient.utils.render.WireframeEntityRenderer;
import revivedevelopment.reviveclient.utils.render.color.Color;
import revivedevelopment.reviveclient.utils.render.color.SettingColor;

public class PopChams extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgRender = this.settings.createGroup("Render");
   private final Setting<Boolean> onlyOne = this.sgGeneral
      .add(new BoolSetting.Builder().name("only-one").description("Only allow one ghost per player.").defaultValue(false).build());
   private final Setting<Boolean> self = this.sgGeneral
      .add(new BoolSetting.Builder().name("self").description("Also render a ghost when you pop.").defaultValue(false).build());
   private final Setting<Boolean> ignoreFriends = this.sgGeneral
      .add(new BoolSetting.Builder().name("ignore-friends").description("Don't render ghosts for friends.").defaultValue(false).build());
   private final Setting<Double> renderTime = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("render-time")
            .description("How long the ghost is rendered in seconds.")
            .defaultValue(1.0)
            .min(0.1)
            .sliderMax(6.0)
            .build()
      );
   private final Setting<Double> yModifier = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("y-modifier")
            .description("How much should the Y position of the ghost change per second.")
            .defaultValue(0.75)
            .sliderRange(-4.0, 4.0)
            .build()
      );
   private final Setting<Double> scaleModifier = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("scale-modifier")
            .description("How much should the scale of the ghost change per second.")
            .defaultValue(-0.25)
            .sliderRange(-4.0, 4.0)
            .build()
      );
   private final Setting<Boolean> fadeOut = this.sgGeneral
      .add(new BoolSetting.Builder().name("fade-out").description("Fades out the color.").defaultValue(true).build());
   private final Setting<PopChams.Mode> mode = this.sgRender
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("mode")).description("How the ghost is rendered."))
               .defaultValue(PopChams.Mode.Wireframe))
            .build()
      );
   private final Setting<ShapeMode> shapeMode = this.sgRender
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("shape-mode"))
                     .description("How the shapes are rendered."))
                  .defaultValue(ShapeMode.Both))
               .visible(() -> this.mode.get() != PopChams.Mode.Skin))
            .build()
      );
   private final Setting<SettingColor> sideColor = this.sgRender
      .add(
         new ColorSetting.Builder()
            .name("side-color")
            .description("The side color.")
            .defaultValue(new SettingColor(255, 255, 255, 25))
            .visible(() -> this.mode.get() != PopChams.Mode.Skin)
            .build()
      );
   private final Setting<SettingColor> lineColor = this.sgRender
      .add(
         new ColorSetting.Builder()
            .name("line-color")
            .description("The line color.")
            .defaultValue(new SettingColor(255, 255, 255, 60))
            .visible(() -> this.mode.get() != PopChams.Mode.Skin)
            .build()
      );
   private final List<PopChams.GhostPlayer> ghosts = new ArrayList<>();
   private final Color side = new Color();
   private final Color line = new Color();

   public PopChams() {
      super(Categories.Render, "pop-chams", "Renders a ghost where players pop totem.");
   }

   @Override
   public void onDeactivate() {
      synchronized (this.ghosts) {
         this.ghosts.clear();
      }
   }

   @EventHandler
   private void onTotemPop(PlayerDeathEvent.TotemPop event) {
      PlayerEntity player = event.getPlayer();
      if (player != null) {
         this.mc.execute(() -> {
            if (this.isActive() && this.mc.world != null && this.mc.player != null) {
               if (player != this.mc.player || this.self.get()) {
                  if (player == this.mc.player || !this.ignoreFriends.get() || !Friends.get().isFriend(player)) {
                     PopChams.GhostPlayer ghost = new PopChams.GhostPlayer(player);
                     synchronized (this.ghosts) {
                        if (this.onlyOne.get()) {
                           this.ghosts.removeIf(g -> g.uuid.equals(ghost.uuid));
                        }

                        this.ghosts.add(ghost);
                     }
                  }
               }
            }
         });
      }
   }

   @EventHandler
   private void onGameLeft(GameLeftEvent event) {
      synchronized (this.ghosts) {
         this.ghosts.clear();
      }
   }

   @EventHandler
   private void onRender3D(Render3DEvent event) {
      if (this.mc.world != null && this.mode.get() != PopChams.Mode.Skin) {
         synchronized (this.ghosts) {
            if (!this.ghosts.isEmpty()) {
               this.ghosts.removeIf(ghost -> ghost.isStale() || ghost.advance(event.frameTime) || ghost.render(event));
            }
         }
      }
   }

   @EventHandler
   private void onSubmitEntities(SubmitEntitiesEvent event) {
      if (this.mc.world != null && this.mode.get() == PopChams.Mode.Skin) {
         synchronized (this.ghosts) {
            if (!this.ghosts.isEmpty()) {
               this.ghosts.removeIf(ghost -> {
                  if (!ghost.isStale() && !ghost.advance(Utils.frameTime)) {
                     SkinEntityRenderer.render(event, ghost.ghost, ghost.scale);
                     return false;
                  } else {
                     return true;
                  }
               });
            }
         }
      }
   }

   private class GhostPlayer {
      private final UUID uuid;
      private final FakePlayerEntity ghost;
      private final double x;
      private final double y;
      private final double z;
      private double timer;
      private double yOffset;
      private double scale = 1.0;

      GhostPlayer(PlayerEntity player) {
         this.uuid = player.getUuid();
         this.x = player.getX();
         this.y = player.getY();
         this.z = player.getZ();
         this.ghost = new FakePlayerEntity(player, player.getGameProfile(), 20.0F, true);
         this.ghost.setSkinSource(PopChams.this.mc.getNetworkHandler() != null ? PopChams.this.mc.getNetworkHandler().getPlayerListEntry(this.uuid) : null);
         this.place();
      }

      private void place() {
         this.ghost.setPosition(this.x, this.y + this.yOffset, this.z);
         this.ghost.lastRenderX = this.x;
         this.ghost.lastRenderY = this.y + this.yOffset;
         this.ghost.lastRenderZ = this.z;
      }

      boolean isStale() {
         return this.ghost.getEntityWorld() != PopChams.this.mc.world;
      }

      boolean advance(double frameTime) {
         this.timer += frameTime;
         if (this.timer > PopChams.this.renderTime.get()) {
            return true;
         } else {
            this.yOffset = this.yOffset + PopChams.this.yModifier.get() * frameTime;
            this.scale = this.scale + PopChams.this.scaleModifier.get() * frameTime;
            if (this.scale <= 0.0) {
               return true;
            } else {
               this.place();
               return false;
            }
         }
      }

      boolean render(Render3DEvent event) {
         PopChams.this.side.set(PopChams.this.sideColor.get());
         PopChams.this.line.set(PopChams.this.lineColor.get());
         if (PopChams.this.fadeOut.get()) {
            double fade = Math.max(0.0, 1.0 - this.timer / PopChams.this.renderTime.get());
            PopChams.this.side.a((int)(PopChams.this.side.a * fade));
            PopChams.this.line.a((int)(PopChams.this.line.a * fade));
         }

         if (PopChams.this.mode.get() == PopChams.Mode.Wireframe) {
            WireframeEntityRenderer.render(event, this.ghost, this.scale, PopChams.this.side, PopChams.this.line, PopChams.this.shapeMode.get());
         } else {
            Box box = this.ghost.getBoundingBox();
            double halfX = (box.maxX - box.minX) / 2.0 * this.scale;
            double halfZ = (box.maxZ - box.minZ) / 2.0 * this.scale;
            double height = (box.maxY - box.minY) * this.scale;
            double cx = (box.minX + box.maxX) / 2.0;
            double cz = (box.minZ + box.maxZ) / 2.0;
            event.renderer
               .box(
                  cx - halfX,
                  box.minY,
                  cz - halfZ,
                  cx + halfX,
                  box.minY + height,
                  cz + halfZ,
                  PopChams.this.side,
                  PopChams.this.line,
                  PopChams.this.shapeMode.get(),
                  0
               );
         }

         return false;
      }
   }

   public static enum Mode {
      Wireframe,
      Box,
      Skin;
   }
}
