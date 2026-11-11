package revivedevelopment.reviveclient.systems.modules.render;

import java.util.ArrayList;
import java.util.List;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.BlockPos.Mutable;
import revivedevelopment.reviveclient.events.render.Render3DEvent;
import revivedevelopment.reviveclient.events.world.TickEvent;
import revivedevelopment.reviveclient.renderer.ShapeMode;
import revivedevelopment.reviveclient.settings.BoolSetting;
import revivedevelopment.reviveclient.settings.ColorSetting;
import revivedevelopment.reviveclient.settings.DoubleSetting;
import revivedevelopment.reviveclient.settings.EnumSetting;
import revivedevelopment.reviveclient.settings.IntSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.systems.modules.Categories;
import revivedevelopment.reviveclient.systems.modules.Module;
import revivedevelopment.reviveclient.utils.render.color.Color;
import revivedevelopment.reviveclient.utils.render.color.SettingColor;

public class HoleESP extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgRender = this.settings.createGroup("Render");
   private final Setting<Integer> horizontalRadius = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("horizontal-radius")
            .description("Horizontal radius in which to search for holes.")
            .defaultValue(10)
            .min(1)
            .sliderRange(1, 32)
            .build()
      );
   private final Setting<Integer> verticalRadius = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("vertical-radius")
            .description("Vertical radius in which to search for holes.")
            .defaultValue(5)
            .min(1)
            .sliderRange(1, 16)
            .build()
      );
   private final Setting<Boolean> doubles = this.sgGeneral
      .add(new BoolSetting.Builder().name("doubles").description("Also highlight 2x1 holes that fit two players.").defaultValue(true).build());
   private final Setting<Boolean> ignoreOwn = this.sgGeneral
      .add(new BoolSetting.Builder().name("ignore-own").description("Doesn't highlight the hole you are currently standing in.").defaultValue(false).build());
   private final Setting<ShapeMode> shapeMode = this.sgRender
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("shape-mode"))
                  .description("How the shapes are rendered."))
               .defaultValue(ShapeMode.Both))
            .build()
      );
   private final Setting<Double> height = this.sgRender
      .add(new DoubleSetting.Builder().name("height").description("How tall the rendered box is.").defaultValue(0.5).min(0.05).sliderRange(0.05, 2.0).build());
   private final Setting<Integer> fillOpacity = this.sgRender
      .add(
         new IntSetting.Builder().name("fill-opacity").description("The opacity of the box sides.").defaultValue(50).range(0, 255).sliderRange(0, 255).build()
      );
   private final Setting<SettingColor> bedrockColor = this.sgRender
      .add(
         new ColorSetting.Builder()
            .name("bedrock")
            .description("Color of holes surrounded by bedrock only.")
            .defaultValue(new SettingColor(25, 225, 25))
            .build()
      );
   private final Setting<SettingColor> obsidianColor = this.sgRender
      .add(
         new ColorSetting.Builder()
            .name("obsidian")
            .description("Color of holes surrounded by obsidian only.")
            .defaultValue(new SettingColor(225, 25, 25))
            .build()
      );
   private final Setting<SettingColor> mixedColor = this.sgRender
      .add(
         new ColorSetting.Builder()
            .name("mixed")
            .description("Color of holes surrounded by a mix of bedrock and obsidian.")
            .defaultValue(new SettingColor(225, 145, 25))
            .build()
      );
   private static final Direction[] HORIZONTALS = new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
   private volatile List<HoleESP.Hole> holes = new ArrayList<>();
   private BlockPos lastScanPos;
   private int ticksSinceScan;
   private final Color side = new Color();
   private final Color line = new Color();

   public HoleESP() {
      super(Categories.Render, "hole-esp", "Highlights holes that prevent crystal damage.");
   }

   @Override
   public void onDeactivate() {
      this.holes = new ArrayList<>();
      this.lastScanPos = null;
   }

   @EventHandler
   private void onTick(TickEvent.Pre event) {
      if (this.mc.world != null && this.mc.player != null) {
         BlockPos playerPos = this.mc.player.getBlockPos();
         if (!playerPos.equals(this.lastScanPos) || ++this.ticksSinceScan >= 5) {
            this.lastScanPos = playerPos;
            this.ticksSinceScan = 0;
            List<HoleESP.Hole> found = new ArrayList<>();
            int hr = this.horizontalRadius.get();
            int vr = this.verticalRadius.get();
            Mutable pos = new Mutable();

            for (int x = playerPos.getX() - hr; x <= playerPos.getX() + hr; x++) {
               for (int z = playerPos.getZ() - hr; z <= playerPos.getZ() + hr; z++) {
                  for (int y = Math.max(this.mc.world.getBottomY(), playerPos.getY() - vr); y <= playerPos.getY() + vr; y++) {
                     pos.set(x, y, z);
                     this.checkHole(pos, found);
                  }
               }
            }

            this.holes = found;
         }
      }
   }

   private void checkHole(Mutable pos, List<HoleESP.Hole> found) {
      if (!this.ignoreOwn.get() || !pos.equals(this.mc.player.getBlockPos())) {
         if (this.isOpenColumn(pos)) {
            int bedrock = 0;
            int obsidian = 0;
            Direction open = null;
            BlockState floor = this.mc.world.getBlockState(pos.down());
            HoleESP.HoleBlock floorType = this.holeBlock(floor);
            if (floorType != null) {
               if (floorType == HoleESP.HoleBlock.Bedrock) {
                  bedrock++;
               } else {
                  obsidian++;
               }

               for (Direction dir : HORIZONTALS) {
                  HoleESP.HoleBlock type = this.holeBlock(this.mc.world.getBlockState(pos.offset(dir)));
                  if (type == HoleESP.HoleBlock.Bedrock) {
                     bedrock++;
                  } else if (type == HoleESP.HoleBlock.Obsidian) {
                     obsidian++;
                  } else {
                     if (open != null || !this.doubles.get()) {
                        return;
                     }

                     open = dir;
                  }
               }

               if (open == null) {
                  found.add(new HoleESP.Hole(pos.toImmutable(), pos.toImmutable(), this.typeOf(bedrock, obsidian)));
               } else {
                  BlockPos neighbour = pos.offset(open);
                  if (open != Direction.SOUTH && open != Direction.EAST) {
                     if (this.isOpenColumn(neighbour)) {
                        HoleESP.HoleBlock nFloor = this.holeBlock(this.mc.world.getBlockState(neighbour.down()));
                        if (nFloor != null) {
                           if (nFloor == HoleESP.HoleBlock.Bedrock) {
                              bedrock++;
                           } else {
                              obsidian++;
                           }

                           for (Direction dirx : HORIZONTALS) {
                              if (dirx != open.getOpposite()) {
                                 HoleESP.HoleBlock type = this.holeBlock(this.mc.world.getBlockState(neighbour.offset(dirx)));
                                 if (type == HoleESP.HoleBlock.Bedrock) {
                                    bedrock++;
                                 } else {
                                    if (type != HoleESP.HoleBlock.Obsidian) {
                                       return;
                                    }

                                    obsidian++;
                                 }
                              }
                           }

                           found.add(new HoleESP.Hole(pos.toImmutable(), neighbour, this.typeOf(bedrock, obsidian)));
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private boolean isOpenColumn(BlockPos pos) {
      return this.mc.world.getBlockState(pos).getCollisionShape(this.mc.world, pos).isEmpty()
         && this.mc.world.getBlockState(pos.up()).getCollisionShape(this.mc.world, pos.up()).isEmpty();
   }

   private HoleESP.HoleBlock holeBlock(BlockState state) {
      Block block = state.getBlock();
      if (block == Blocks.BEDROCK) {
         return HoleESP.HoleBlock.Bedrock;
      } else {
         return block != Blocks.OBSIDIAN
               && block != Blocks.CRYING_OBSIDIAN
               && block != Blocks.RESPAWN_ANCHOR
               && block != Blocks.ANCIENT_DEBRIS
               && block != Blocks.ENDER_CHEST
               && block != Blocks.ANVIL
            ? null
            : HoleESP.HoleBlock.Obsidian;
      }
   }

   private HoleESP.HoleType typeOf(int bedrock, int obsidian) {
      if (obsidian == 0) {
         return HoleESP.HoleType.Bedrock;
      } else {
         return bedrock == 0 ? HoleESP.HoleType.Obsidian : HoleESP.HoleType.Mixed;
      }
   }

   private SettingColor colorOf(HoleESP.HoleType type) {
      return switch (type) {
         case Bedrock -> (SettingColor)this.bedrockColor.get();
         case Obsidian -> (SettingColor)this.obsidianColor.get();
         case Mixed -> (SettingColor)this.mixedColor.get();
      };
   }

   @EventHandler
   private void onRender(Render3DEvent event) {
      List<HoleESP.Hole> render = this.holes;
      ShapeMode shape = this.shapeMode.get();
      double h = this.height.get();

      for (HoleESP.Hole hole : render) {
         SettingColor base = this.colorOf(hole.type());
         this.line.set((Color)base);
         this.side.set((Color)base);
         this.side.a = shape.sides() ? this.fillOpacity.get() : 0;
         double minX = Math.min(hole.a().getX(), hole.b().getX());
         double minZ = Math.min(hole.a().getZ(), hole.b().getZ());
         double maxX = Math.max(hole.a().getX(), hole.b().getX()) + 1;
         double maxZ = Math.max(hole.a().getZ(), hole.b().getZ()) + 1;
         double y = hole.a().getY();
         event.renderer.box(minX, y, minZ, maxX, y + h, maxZ, this.side, this.line, shape, 0);
      }
   }

   @Override
   public String getInfoString() {
      return Integer.toString(this.holes.size());
   }

   private record Hole(BlockPos a, BlockPos b, HoleESP.HoleType type) {
   }

   private static enum HoleBlock {
      Bedrock,
      Obsidian;
   }

   public static enum HoleType {
      Bedrock,
      Obsidian,
      Mixed;
   }
}
