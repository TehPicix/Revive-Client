package revivedevelopment.reviveclient.systems.modules.combat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.events.render.Render3DEvent;
import revivedevelopment.reviveclient.events.world.TickEvent;
import revivedevelopment.reviveclient.renderer.ShapeMode;
import revivedevelopment.reviveclient.settings.BoolSetting;
import revivedevelopment.reviveclient.settings.ColorSetting;
import revivedevelopment.reviveclient.settings.DoubleSetting;
import revivedevelopment.reviveclient.settings.IntSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.systems.friends.Friends;
import revivedevelopment.reviveclient.systems.modules.Categories;
import revivedevelopment.reviveclient.systems.modules.Module;
import revivedevelopment.reviveclient.utils.player.FindItemResult;
import revivedevelopment.reviveclient.utils.render.color.Color;
import revivedevelopment.reviveclient.utils.render.color.SettingColor;

public class AutoWeb extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgRender = this.settings.createGroup("Render");
   private final Setting<Double> range = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("target-range")
            .description("How close an enemy has to be.")
            .defaultValue(4.0)
            .min(1.0)
            .max(6.0)
            .sliderRange(1.0, 6.0)
            .build()
      );
   private final Setting<Integer> delay = this.sgGeneral
      .add(new IntSetting.Builder().name("delay").description("Ticks between webs.").defaultValue(2).range(0, 20).sliderRange(0, 20).build());
   private final Setting<Boolean> render = this.sgRender
      .add(new BoolSetting.Builder().name("render").description("Highlight the last web placed.").defaultValue(true).build());
   private final Setting<SettingColor> sideColor = this.sgRender
      .add(new ColorSetting.Builder().name("side-color").defaultValue(new SettingColor(150, 150, 150, 45)).visible(this.render::get).build());
   private final Setting<SettingColor> lineColor = this.sgRender
      .add(new ColorSetting.Builder().name("line-color").defaultValue(new SettingColor(220, 220, 220, 220)).visible(this.render::get).build());
   private int cooldown;
   private BlockPos renderPos;
   private long renderUntil;

   public AutoWeb() {
      super(Categories.Combat, "auto-web", "Places cobwebs in the blocks enemies are standing in.");
   }

   @Override
   public void onActivate() {
      this.cooldown = 0;
      this.renderPos = null;
   }

   @EventHandler
   private void onTick(TickEvent.Pre event) {
      if (this.cooldown > 0) {
         this.cooldown--;
      }

      if (this.mc.player != null && this.mc.world != null && !this.mc.player.isDead()) {
         if (this.mc.player.currentScreenHandler instanceof PlayerScreenHandler) {
            if (this.cooldown <= 0) {
               FindItemResult web = ReviveClient.SWAP.getSlot(Items.COBWEB);
               if (web.found()) {
                  double rangeSq = this.range.get() * this.range.get();
                  List<PlayerEntity> enemies = new ArrayList<>();

                  for (PlayerEntity p : this.mc.world.getPlayers()) {
                     if (p != this.mc.player
                        && !p.isDead()
                        && !p.isCreative()
                        && !p.isSpectator()
                        && !Friends.get().isFriend(p)
                        && this.mc.player.squaredDistanceTo(p) <= rangeSq) {
                        enemies.add(p);
                     }
                  }

                  if (!enemies.isEmpty()) {
                     enemies.sort(Comparator.comparingDouble(this.mc.player::squaredDistanceTo));
                     Vec3d eye = this.mc.player.getEyePos();

                     for (PlayerEntity enemy : enemies) {
                        for (BlockPos pos : this.cells(enemy.getBoundingBox(), eye)) {
                           if (ReviveClient.BLOCK.beginPlacement(pos, this.mc.world.getBlockState(pos), Items.COBWEB)) {
                              boolean placed;
                              try {
                                 placed = ReviveClient.BLOCK.placeBlock(Items.COBWEB, pos);
                              } finally {
                                 ReviveClient.BLOCK.endPlacement();
                              }

                              if (placed) {
                                 this.cooldown = this.delay.get();
                                 this.renderPos = pos;
                                 this.renderUntil = System.currentTimeMillis() + 850L;
                                 return;
                              }
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private List<BlockPos> cells(Box box, Vec3d eye) {
      record Cell(BlockPos pos, double overlap, double dist) {
      }

      List<Cell> cells = new ArrayList<>();

      for (int x = MathHelper.floor(box.minX); x <= MathHelper.floor(box.maxX - 1.0E-7); x++) {
         for (int y = MathHelper.floor(box.minY); y <= MathHelper.floor(box.maxY - 1.0E-7); y++) {
            for (int z = MathHelper.floor(box.minZ); z <= MathHelper.floor(box.maxZ - 1.0E-7); z++) {
               BlockPos pos = new BlockPos(x, y, z);
               if (this.mc.world.getBlockState(pos).isReplaceable()) {
                  double ox = Math.max(0.0, Math.min(box.maxX, x + 1.0) - Math.max(box.minX, (double)x));
                  double oy = Math.max(0.0, Math.min(box.maxY, y + 1.0) - Math.max(box.minY, (double)y));
                  double oz = Math.max(0.0, Math.min(box.maxZ, z + 1.0) - Math.max(box.minZ, (double)z));
                  double overlap = ox * oy * oz;
                  if (overlap > 0.0) {
                     cells.add(new Cell(pos, overlap, eye.squaredDistanceTo(Vec3d.ofCenter(pos))));
                  }
               }
            }
         }
      }

      cells.sort(Comparator.comparingDouble(Cell::overlap).reversed().thenComparingDouble(Cell::dist));
      List<BlockPos> out = new ArrayList<>(cells.size());

      for (Cell c : cells) {
         out.add(c.pos());
      }

      return out;
   }

   @EventHandler
   private void onRender3D(Render3DEvent event) {
      if (this.render.get() && this.renderPos != null) {
         if (System.currentTimeMillis() > this.renderUntil) {
            this.renderPos = null;
         } else {
            SettingColor s = this.sideColor.get();
            SettingColor l = this.lineColor.get();
            event.renderer.box(this.renderPos, new Color(s.r, s.g, s.b, s.a), new Color(l.r, l.g, l.b, l.a), ShapeMode.Both, 0);
         }
      }
   }
}
