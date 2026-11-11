package revivedevelopment.reviveclient.systems.modules.combat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.events.render.Render3DEvent;
import revivedevelopment.reviveclient.events.world.TickEvent;
import revivedevelopment.reviveclient.renderer.ShapeMode;
import revivedevelopment.reviveclient.settings.BoolSetting;
import revivedevelopment.reviveclient.settings.ColorSetting;
import revivedevelopment.reviveclient.settings.DoubleSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.systems.friends.Friends;
import revivedevelopment.reviveclient.systems.modules.Categories;
import revivedevelopment.reviveclient.systems.modules.Module;
import revivedevelopment.reviveclient.utils.render.color.Color;
import revivedevelopment.reviveclient.utils.render.color.SettingColor;

public class PearlBlocker extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgRender = this.settings.createGroup("Render");
   private final Setting<Boolean> render = this.sgRender
      .add(new BoolSetting.Builder().name("render").description("Flash the block placed to catch a pearl.").defaultValue(true).build());
   private final Setting<Double> fadeTime = this.sgRender
      .add(
         new DoubleSetting.Builder()
            .name("fade-time")
            .description("Seconds the highlight stays.")
            .defaultValue(1.0)
            .min(0.05)
            .max(2.0)
            .sliderRange(0.05, 2.0)
            .visible(this.render::get)
            .build()
      );
   private final Setting<SettingColor> sideColor = this.sgRender
      .add(new ColorSetting.Builder().name("side-color").defaultValue(new SettingColor(255, 0, 0, 44)).visible(this.render::get).build());
   private final Setting<SettingColor> lineColor = this.sgRender
      .add(new ColorSetting.Builder().name("line-color").defaultValue(new SettingColor(255, 0, 0, 120)).visible(this.render::get).build());
   private static final int MAX_FORWARD_TICKS = 60;
   private static final int MIN_LEAD_TICKS = 3;
   private static final int MIN_REMAINING_TICKS = 2;
   private final Set<Integer> handled = new HashSet<>();
   private final Map<BlockPos, Long> placed = new HashMap<>();

   public PearlBlocker() {
      super(Categories.Combat, "pearl-blocker", "Catches enemy pearls by placing obsidian in their flight path.");
   }

   @Override
   public void onActivate() {
      this.handled.clear();
      this.placed.clear();
   }

   @EventHandler
   private void onTick(TickEvent.Pre event) {
      if (this.mc.player != null && this.mc.world != null) {
         long now = System.currentTimeMillis();
         this.placed.entrySet().removeIf(e -> now - e.getValue() > (long)(this.fadeTime.get() * 1000.0));
         this.handled.removeIf(id -> {
            Entity e = this.mc.world.getEntityById(id);
            return e == null || e.isRemoved();
         });
         if (ReviveClient.SWAP.getSlot(Items.OBSIDIAN).found()) {
            List<PearlBlocker.Interception> catches = new ArrayList<>();

            for (Entity e : this.mc.world.getEntities()) {
               if (e instanceof EnderPearlEntity pearl
                  && !pearl.isRemoved()
                  && !this.handled.contains(pearl.getId())
                  && pearl.getOwner() instanceof PlayerEntity owner
                  && owner != this.mc.player
                  && !Friends.get().isFriend(owner)
                  && (!pearl.isTouchingWater() || !(pearl.getVelocity().length() < 0.015))) {
                  PearlBlocker.Interception i = this.findInterception(pearl);
                  if (i != null) {
                     catches.add(i);
                  }
               }
            }

            catches.sort((a, b) -> a.remaining != b.remaining ? Integer.compare(b.remaining, a.remaining) : Integer.compare(a.arrival, b.arrival));

            for (PearlBlocker.Interception i : catches) {
               if (ReviveClient.BLOCK.beginPlacement(i.pos, this.mc.world.getBlockState(i.pos), Items.OBSIDIAN)) {
                  boolean ok;
                  try {
                     ok = ReviveClient.BLOCK.placeBlock(Items.OBSIDIAN, i.pos);
                  } finally {
                     ReviveClient.BLOCK.endPlacement();
                  }

                  if (ok) {
                     this.handled.add(i.pearl.getId());
                     this.placed.put(i.pos, now);
                     return;
                  }
               }
            }
         }
      }
   }

   private PearlBlocker.Interception findInterception(EnderPearlEntity pearl) {
      Vec3d pos = pearl.getEntityPos();
      Vec3d vel = pearl.getVelocity();
      boolean inWater = pearl.isTouchingWater();
      BlockPos candidate = null;
      int candidateTick = -1;

      for (int t = 0; t < 60; t++) {
         double drag = inWater ? 0.8 : 0.99;
         vel = new Vec3d(vel.x * drag, (vel.y - 0.03) * drag, vel.z * drag);
         Vec3d next = pos.add(vel);
         BlockHitResult hit = this.mc.world.raycast(new RaycastContext(pos, next, ShapeType.COLLIDER, FluidHandling.NONE, pearl));
         Vec3d end = hit.getType() == Type.MISS ? next : hit.getPos();
         int arrival = t + 1;
         if (candidate == null && arrival >= 3) {
            candidate = this.placementCell(pos, end);
            if (candidate != null) {
               candidateTick = arrival;
            }
         }

         if (hit.getType() != Type.MISS) {
            if (candidate == null) {
               return null;
            }

            int remaining = arrival - candidateTick;
            return remaining < 2 ? null : new PearlBlocker.Interception(pearl, candidate, candidateTick, remaining);
         }

         pos = next;
         inWater = this.mc.world.getFluidState(BlockPos.ofFloored(next)).isIn(FluidTags.WATER);
         if (next.y < this.mc.world.getBottomY() - 5) {
            return null;
         }
      }

      return null;
   }

   private BlockPos placementCell(Vec3d from, Vec3d to) {
      Box self = this.mc.player.getBoundingBox();
      return (BlockPos)BlockView.raycast(from, to, this, (ctx, pos) -> {
         if (!this.mc.world.getBlockState(pos).isReplaceable()) {
            return null;
         } else if (self.intersects(new Box(pos))) {
            return null;
         } else if (!this.mc.world.getBlockState(pos.down()).getCollisionShape(this.mc.world, pos.down()).isEmpty()) {
            return null;
         } else {
            return !ReviveClient.BLOCK.checkPlacement(Items.OBSIDIAN, pos) ? null : pos.toImmutable();
         }
      }, ctx -> null);
   }

   @EventHandler
   private void onRender3D(Render3DEvent event) {
      if (this.render.get() && !this.placed.isEmpty()) {
         long now = System.currentTimeMillis();
         double fade = this.fadeTime.get() * 1000.0;
         SettingColor s = this.sideColor.get();
         SettingColor l = this.lineColor.get();

         for (Entry<BlockPos, Long> e : this.placed.entrySet()) {
            double k = 1.0 - Math.min(1.0, (now - e.getValue()) / fade);
            if (!(k <= 0.0)) {
               event.renderer.box(e.getKey(), new Color(s.r, s.g, s.b, (int)(s.a * k)), new Color(l.r, l.g, l.b, (int)(l.a * k)), ShapeMode.Both, 0);
            }
         }
      }
   }

   private record Interception(EnderPearlEntity pearl, BlockPos pos, int arrival, int remaining) {
   }
}
