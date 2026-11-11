package revivedevelopment.reviveclient.systems.modules.render;

import java.util.ArrayList;
import java.util.List;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;
import revivedevelopment.reviveclient.events.render.Render3DEvent;
import revivedevelopment.reviveclient.events.world.TickEvent;
import revivedevelopment.reviveclient.renderer.ShapeMode;
import revivedevelopment.reviveclient.settings.BoolSetting;
import revivedevelopment.reviveclient.settings.ColorSetting;
import revivedevelopment.reviveclient.settings.EnumSetting;
import revivedevelopment.reviveclient.settings.IntSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.systems.modules.Categories;
import revivedevelopment.reviveclient.systems.modules.Module;
import revivedevelopment.reviveclient.utils.render.color.Color;
import revivedevelopment.reviveclient.utils.render.color.SettingColor;

public class PhaseESP extends Module {
   private final SettingGroup sgSafeBedrock = this.settings.createGroup("Safe Bedrock");
   private final SettingGroup sgUnsafeBedrock = this.settings.createGroup("Unsafe Bedrock");
   private final SettingGroup sgSafeOpenHeadBedrock = this.settings.createGroup("Safe Open Head Bedrock");
   private final SettingGroup sgSafeObsidian = this.settings.createGroup("Safe Obsidian");
   private final SettingGroup sgUnsafeObsidian = this.settings.createGroup("Unsafe Obsidian");
   private final SettingGroup sgOpenHead = this.settings.createGroup("Open Head");
   private final SettingGroup sgRender = this.settings.createGroup("Render");
   private final Setting<Boolean> safeBedrock = this.sgSafeBedrock
      .add(new BoolSetting.Builder().name("safe-bedrock").description("Bedrock with a safe block below it.").defaultValue(true).build());
   private final Setting<SettingColor> safeBedrockColor = this.sgSafeBedrock
      .add(
         new ColorSetting.Builder()
            .name("safe-bedrock-color")
            .description("Color of safe bedrock.")
            .defaultValue(new SettingColor(150, 0, 255, 50))
            .visible(this.safeBedrock::get)
            .build()
      );
   private final Setting<Boolean> unsafeBedrock = this.sgUnsafeBedrock
      .add(new BoolSetting.Builder().name("unsafe-bedrock").description("Bedrock without a safe block below it.").defaultValue(true).build());
   private final Setting<SettingColor> unsafeBedrockColor = this.sgUnsafeBedrock
      .add(
         new ColorSetting.Builder()
            .name("unsafe-bedrock-color")
            .description("Color of unsafe bedrock.")
            .defaultValue(new SettingColor(255, 0, 0, 70))
            .visible(this.unsafeBedrock::get)
            .build()
      );
   private final Setting<Boolean> safeOpenHeadBedrock = this.sgSafeOpenHeadBedrock
      .add(new BoolSetting.Builder().name("safe-open-head-bedrock").description("Safe bedrock with an open head.").defaultValue(true).build());
   private final Setting<SettingColor> safeOpenHeadBedrockColor = this.sgSafeOpenHeadBedrock
      .add(
         new ColorSetting.Builder()
            .name("safe-open-head-bedrock-color")
            .description("Color of safe open head bedrock.")
            .defaultValue(new SettingColor(135, 160, 20, 50))
            .visible(this.safeOpenHeadBedrock::get)
            .build()
      );
   private final Setting<Boolean> safeObsidian = this.sgSafeObsidian
      .add(new BoolSetting.Builder().name("safe-obsidian").description("Obsidian with a safe block below it.").defaultValue(true).build());
   private final Setting<SettingColor> safeObsidianColor = this.sgSafeObsidian
      .add(
         new ColorSetting.Builder()
            .name("safe-obsidian-color")
            .description("Color of safe obsidian.")
            .defaultValue(new SettingColor(140, 0, 255, 10))
            .visible(this.safeObsidian::get)
            .build()
      );
   private final Setting<Boolean> unsafeObsidian = this.sgUnsafeObsidian
      .add(new BoolSetting.Builder().name("unsafe-obsidian").description("Obsidian without a safe block below it.").defaultValue(true).build());
   private final Setting<SettingColor> unsafeObsidianColor = this.sgUnsafeObsidian
      .add(
         new ColorSetting.Builder()
            .name("unsafe-obsidian-color")
            .description("Color of unsafe obsidian.")
            .defaultValue(new SettingColor(255, 0, 0, 30))
            .visible(this.unsafeObsidian::get)
            .build()
      );
   private final Setting<Boolean> openHead = this.sgOpenHead
      .add(new BoolSetting.Builder().name("open-head").description("A block where the head is open.").defaultValue(true).build());
   private final Setting<SettingColor> openHeadColor = this.sgOpenHead
      .add(
         new ColorSetting.Builder()
            .name("open-head-color")
            .description("Color of open head blocks.")
            .defaultValue(new SettingColor(255, 0, 240, 30))
            .visible(this.openHead::get)
            .build()
      );
   private final Setting<ShapeMode> shapeMode = this.sgRender
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("shape-mode"))
                  .description("How the shapes are rendered."))
               .defaultValue(ShapeMode.Both))
            .build()
      );
   private final Setting<Integer> lineAlpha = this.sgRender
      .add(
         new IntSetting.Builder()
            .name("line-alpha")
            .description("Opacity of the outline. The outline uses each block type's color.")
            .defaultValue(255)
            .range(0, 255)
            .sliderRange(0, 255)
            .build()
      );
   private final Color lineColor = new Color();
   private final List<PhaseESP.PhaseBlock> phaseBlocks = new ArrayList<>();

   public PhaseESP() {
      super(Categories.Render, "phase-esp", "Shows you where it's safe to phase.");
   }

   @EventHandler
   private void onTick(TickEvent.Pre event) {
      this.phaseBlocks.clear();
      if (this.mc.player != null && this.mc.world != null) {
         BlockPos playerPos = this.mc.player.getBlockPos();
         Box boundingBox = this.mc.player.getBoundingBox().expand(0.999, 0.0, 0.999);
         double feetY = this.mc.player.getY();
         Box feetBox = new Box(boundingBox.minX, feetY, boundingBox.minZ, boundingBox.maxX, feetY + 0.1, boundingBox.maxZ);
         boolean acrossMultipleBlocks = (int)Math.floor(feetBox.maxX) - (int)Math.floor(feetBox.minX) >= 1
            || (int)Math.floor(feetBox.maxZ) - (int)Math.floor(feetBox.minZ) >= 1;

         for (int x = (int)Math.floor(feetBox.minX); x <= (int)Math.floor(feetBox.maxX); x++) {
            for (int z = (int)Math.floor(feetBox.minZ); z <= (int)Math.floor(feetBox.maxZ); z++) {
               BlockPos blockPos = new BlockPos(x, playerPos.getY(), z);
               if (acrossMultipleBlocks || playerPos.getX() != x || playerPos.getZ() != z) {
                  Vec3d start = this.mc.player.getEntityPos().add(0.0, 0.05, 0.0);
                  Vec3d end = Vec3d.ofBottomCenter(blockPos).add(0.0, 0.05, 0.0);
                  BlockHitResult result = this.mc.world.raycast(new RaycastContext(start, end, ShapeType.OUTLINE, FluidHandling.NONE, this.mc.player));
                  if (result == null || result.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK) {
                     this.checkBlock(blockPos);
                  }
               }
            }
         }
      }
   }

   private void checkBlock(BlockPos pos) {
      BlockState block = this.mc.world.getBlockState(pos);
      BlockState downBlock = this.mc.world.getBlockState(pos.down());
      BlockState upBlock = this.mc.world.getBlockState(pos.up());
      if (downBlock != null && block != null) {
         boolean obsidian = block.isOf(Blocks.OBSIDIAN) || block.isOf(Blocks.CRYING_OBSIDIAN);
         boolean bedrock = block.isOf(Blocks.BEDROCK);
         boolean bedrockDown = downBlock.isOf(Blocks.BEDROCK);
         boolean airUp = upBlock.isAir();
         boolean obsidianUp = upBlock.isOf(Blocks.OBSIDIAN) || upBlock.isOf(Blocks.CRYING_OBSIDIAN);
         boolean obsidianDown = downBlock.isOf(Blocks.OBSIDIAN) || downBlock.isOf(Blocks.CRYING_OBSIDIAN);
         boolean bedrockUp = upBlock.isOf(Blocks.BEDROCK);
         if (bedrock) {
            if (bedrockDown) {
               if (bedrockUp) {
                  this.phaseBlocks.add(new PhaseESP.PhaseBlock(pos, PhaseESP.Type.SAFE_BEDROCK));
               } else {
                  this.phaseBlocks.add(new PhaseESP.PhaseBlock(pos, PhaseESP.Type.SAFE_BEDROCK_OPEN_HEAD));
               }
            } else {
               this.phaseBlocks.add(new PhaseESP.PhaseBlock(pos, PhaseESP.Type.UNSAFE_BEDROCK));
            }
         } else if (obsidian) {
            if (!obsidianDown && !bedrockDown) {
               this.phaseBlocks.add(new PhaseESP.PhaseBlock(pos, PhaseESP.Type.UNSAFE_OBSIDIAN));
            } else if (airUp) {
               this.phaseBlocks.add(new PhaseESP.PhaseBlock(pos, PhaseESP.Type.OPEN_HEAD));
            } else {
               this.phaseBlocks.add(new PhaseESP.PhaseBlock(pos, PhaseESP.Type.SAFE_OBSIDIAN));
            }
         } else if (obsidianUp) {
            this.phaseBlocks.add(new PhaseESP.PhaseBlock(pos, PhaseESP.Type.UNSAFE_OBSIDIAN));
         } else if (airUp && (obsidianDown || bedrockDown)) {
            this.phaseBlocks.add(new PhaseESP.PhaseBlock(pos, PhaseESP.Type.OPEN_HEAD));
         }
      }
   }

   @EventHandler
   private void onRender3D(Render3DEvent event) {
      for (PhaseESP.PhaseBlock pb : this.phaseBlocks) {
         boolean enabled = switch (pb.type) {
            case SAFE_BEDROCK -> this.safeBedrock.get();
            case SAFE_OBSIDIAN -> this.safeObsidian.get();
            case UNSAFE_BEDROCK -> this.unsafeBedrock.get();
            case UNSAFE_OBSIDIAN -> this.unsafeObsidian.get();
            case SAFE_BEDROCK_OPEN_HEAD -> this.safeOpenHeadBedrock.get();
            case OPEN_HEAD -> this.openHead.get();
         };
         if (enabled) {
            SettingColor fillColor = switch (pb.type) {
               case SAFE_BEDROCK -> (SettingColor)this.safeBedrockColor.get();
               case SAFE_OBSIDIAN -> (SettingColor)this.safeObsidianColor.get();
               case UNSAFE_BEDROCK -> (SettingColor)this.unsafeBedrockColor.get();
               case UNSAFE_OBSIDIAN -> (SettingColor)this.unsafeObsidianColor.get();
               case SAFE_BEDROCK_OPEN_HEAD -> (SettingColor)this.safeOpenHeadBedrockColor.get();
               case OPEN_HEAD -> (SettingColor)this.openHeadColor.get();
            };
            int x1 = pb.pos.getX();
            int y1 = pb.pos.getY();
            int z1 = pb.pos.getZ();
            int x2 = x1 + 1;
            int z2 = z1 + 1;
            this.lineColor.set(fillColor.r, fillColor.g, fillColor.b, this.lineAlpha.get());
            event.renderer.sideHorizontal(x1, y1, z1, x2, z2, fillColor, this.lineColor, this.shapeMode.get());
         }
      }
   }

   private static class PhaseBlock {
      final BlockPos pos;
      final PhaseESP.Type type;

      PhaseBlock(BlockPos pos, PhaseESP.Type type) {
         this.pos = pos.toImmutable();
         this.type = type;
      }
   }

   private static enum Type {
      SAFE_BEDROCK,
      SAFE_OBSIDIAN,
      UNSAFE_BEDROCK,
      UNSAFE_OBSIDIAN,
      SAFE_BEDROCK_OPEN_HEAD,
      OPEN_HEAD;
   }
}
