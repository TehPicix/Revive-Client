package revivedevelopment.reviveclient.systems.modules.hunting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.entity.vehicle.ChestBoatEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.chunk.WorldChunk;
import revivedevelopment.reviveclient.events.render.Render3DEvent;
import revivedevelopment.reviveclient.events.world.ChunkDataEvent;
import revivedevelopment.reviveclient.renderer.ShapeMode;
import revivedevelopment.reviveclient.settings.BoolSetting;
import revivedevelopment.reviveclient.settings.ColorSetting;
import revivedevelopment.reviveclient.settings.EnumSetting;
import revivedevelopment.reviveclient.settings.IntSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.systems.modules.Categories;
import revivedevelopment.reviveclient.systems.modules.Module;
import revivedevelopment.reviveclient.utils.render.RenderUtils;
import revivedevelopment.reviveclient.utils.render.color.Color;
import revivedevelopment.reviveclient.utils.render.color.SettingColor;

public class VanityESP extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgRender = this.settings.createGroup("Render");
   private final Setting<Boolean> stackedEntities = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("stacked-entities")
            .description("Detect chest/hopper minecarts and chest boats stacked at one position (an item-hiding technique).")
            .defaultValue(true)
            .build()
      );
   private final Setting<Integer> stackedMinCount = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("stacked-min-count")
            .description("Minimum number of inventory entities at one block to count as a stack.")
            .defaultValue(2)
            .min(2)
            .sliderRange(2, 10)
            .visible(this.stackedEntities::get)
            .build()
      );
   private final Setting<Boolean> shulkerFrames = this.sgGeneral
      .add(new BoolSetting.Builder().name("shulker-frames").description("Detect item frames displaying shulker boxes.").defaultValue(true).build());
   private final Setting<Boolean> buriedTreasure = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("buried-treasure")
            .description("Detect naturally-buried chests at the chunk treasure position.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> chatNotify = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("chat-notify")
            .description("Announce stacked entities and buried treasure in chat the first time they are seen.")
            .defaultValue(true)
            .build()
      );
   private final Setting<ShapeMode> shapeMode = this.sgRender
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("shape-mode"))
                  .description("How the ESP boxes are drawn."))
               .defaultValue(ShapeMode.Both))
            .build()
      );
   private final Setting<Boolean> tracers = this.sgRender
      .add(new BoolSetting.Builder().name("tracers").description("Draw tracer lines to detected targets.").defaultValue(false).build());
   private final Setting<Integer> fillAlpha = this.sgRender
      .add(new IntSetting.Builder().name("fill-alpha").description("Opacity of the box fill.").defaultValue(50).min(0).sliderRange(0, 255).build());
   private final Setting<SettingColor> stackedColor = this.sgRender
      .add(new ColorSetting.Builder().name("stacked-color").description("Color for stacked entities.").defaultValue(new SettingColor(255, 170, 0, 255)).build());
   private final Setting<SettingColor> shulkerFrameColor = this.sgRender
      .add(
         new ColorSetting.Builder()
            .name("shulker-frame-color")
            .description("Color for shulker item frames.")
            .defaultValue(new SettingColor(200, 90, 255, 255))
            .build()
      );
   private final Setting<SettingColor> treasureColor = this.sgRender
      .add(new ColorSetting.Builder().name("treasure-color").description("Color for buried treasure.").defaultValue(new SettingColor(0, 200, 200, 255)).build());
   private final List<List<Entity>> cachedStackedGroups = new ArrayList<>();
   private final Set<BlockPos> notifiedStacked = new HashSet<>();
   private final Set<BlockPos> treasurePositions = new HashSet<>();
   private long lastStackedScanMs;
   private final Color fill = new Color();
   private static final Block[] BURIAL_BLOCKS = new Block[]{
      Blocks.SAND, Blocks.DIRT, Blocks.GRAVEL, Blocks.STONE, Blocks.DIORITE, Blocks.GRANITE, Blocks.ANDESITE, Blocks.SANDSTONE, Blocks.COAL_ORE
   };

   public VanityESP() {
      super(
         Categories.Hunting,
         "vanity-esp",
         "Highlights stacked inventory entities, shulker item frames and buried treasure - base/stash signals a container ESP misses."
      );
   }

   @Override
   public void onActivate() {
      this.cachedStackedGroups.clear();
      this.notifiedStacked.clear();
      this.treasurePositions.clear();
      this.lastStackedScanMs = 0L;
   }

   @Override
   public void onDeactivate() {
      this.cachedStackedGroups.clear();
      this.notifiedStacked.clear();
      this.treasurePositions.clear();
   }

   @EventHandler
   private void onChunkData(ChunkDataEvent event) {
      if (this.buriedTreasure.get() && this.mc.world != null) {
         WorldChunk chunk = event.chunk();

         for (Entry<BlockPos, BlockEntity> entry : chunk.getBlockEntities().entrySet()) {
            BlockPos pos = entry.getKey();
            if (entry.getValue() instanceof ChestBlockEntity
               && (pos.getX() & 15) == 9
               && (pos.getZ() & 15) == 9
               && this.isBuriedNaturally(pos)
               && this.treasurePositions.add(pos)
               && this.chatNotify.get()) {
               this.info("Buried treasure at %d %d %d", new Object[]{pos.getX(), pos.getY(), pos.getZ()});
            }
         }
      }
   }

   @EventHandler
   private void onRender3D(Render3DEvent event) {
      if (this.mc.world != null && this.mc.player != null) {
         if (this.stackedEntities.get()) {
            long now = System.currentTimeMillis();
            if (now - this.lastStackedScanMs >= 200L) {
               this.lastStackedScanMs = now;
               this.recomputeStackedGroups();
            }

            SettingColor c = this.stackedColor.get();

            for (List<Entity> group : this.cachedStackedGroups) {
               for (Entity e : group) {
                  this.drawBox(event, e.getBoundingBox(), c);
               }
            }
         }

         if (this.shulkerFrames.get()) {
            SettingColor c = this.shulkerFrameColor.get();

            for (Entity entity : this.mc.world.getEntities()) {
               if (entity instanceof ItemFrameEntity frame && this.isShulkerBox(frame.getHeldItemStack())) {
                  this.drawBox(event, frame.getBoundingBox(), c);
               }
            }
         }

         if (this.buriedTreasure.get() && !this.treasurePositions.isEmpty()) {
            SettingColor c = this.treasureColor.get();
            this.fill.set(c.r, c.g, c.b, this.fillAlpha.get());

            for (BlockPos pos : this.treasurePositions) {
               event.renderer.box(pos, this.fill, c, this.shapeMode.get(), 0);
               if (this.tracers.get()) {
                  event.renderer
                     .line(RenderUtils.center.x, RenderUtils.center.y, RenderUtils.center.z, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, c);
               }
            }
         }
      }
   }

   private void drawBox(Render3DEvent event, Box box, SettingColor color) {
      this.fill.set(color.r, color.g, color.b, this.fillAlpha.get());
      event.renderer.box(box, this.fill, color, this.shapeMode.get(), 0);
      if (this.tracers.get()) {
         event.renderer.line(RenderUtils.center.x, RenderUtils.center.y, RenderUtils.center.z, box.getCenter().x, box.getCenter().y, box.getCenter().z, color);
      }
   }

   private void recomputeStackedGroups() {
      this.cachedStackedGroups.clear();
      Map<BlockPos, List<Entity>> byPos = new HashMap<>();

      for (Entity entity : this.mc.world.getEntities()) {
         if (this.isInventoryEntity(entity)) {
            byPos.computeIfAbsent(entity.getBlockPos(), k -> new ArrayList<>()).add(entity);
         }
      }

      int minCount = this.stackedMinCount.get();
      Set<BlockPos> current = new HashSet<>();

      for (Entry<BlockPos, List<Entity>> entry : byPos.entrySet()) {
         if (entry.getValue().size() >= minCount) {
            BlockPos pos = entry.getKey();
            current.add(pos);
            this.cachedStackedGroups.add(entry.getValue());
            if (this.notifiedStacked.add(pos) && this.chatNotify.get()) {
               this.info(
                  "(highlight)%d(default) stacked %s at %d %d %d",
                  new Object[]{entry.getValue().size(), this.stackName(entry.getValue().get(0)), pos.getX(), pos.getY(), pos.getZ()}
               );
            }
         }
      }

      this.notifiedStacked.retainAll(current);
   }

   private boolean isInventoryEntity(Entity entity) {
      return entity instanceof Inventory && (entity instanceof AbstractMinecartEntity || entity instanceof ChestBoatEntity);
   }

   private String stackName(Entity entity) {
      if (entity instanceof ChestBoatEntity) {
         return "chest boats";
      } else {
         return entity instanceof AbstractMinecartEntity ? "storage minecarts" : "inventory entities";
      }
   }

   private boolean isShulkerBox(ItemStack stack) {
      return stack != null && stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof ShulkerBoxBlock;
   }

   private boolean isBuriedNaturally(BlockPos pos) {
      if (this.mc.world == null) {
         return false;
      } else {
         Block above = this.mc.world.getBlockState(pos.up()).getBlock();

         for (Block b : BURIAL_BLOCKS) {
            if (above == b) {
               return true;
            }
         }

         return false;
      }
   }
}
