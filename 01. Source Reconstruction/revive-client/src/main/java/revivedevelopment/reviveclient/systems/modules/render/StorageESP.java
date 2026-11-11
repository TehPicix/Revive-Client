package revivedevelopment.reviveclient.systems.modules.render;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.entity.BrewingStandBlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.ChiseledBookshelfBlockEntity;
import net.minecraft.block.entity.CrafterBlockEntity;
import net.minecraft.block.entity.DecoratedPotBlockEntity;
import net.minecraft.block.entity.DispenserBlockEntity;
import net.minecraft.block.entity.EnderChestBlockEntity;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.block.entity.TrappedChestBlockEntity;
import net.minecraft.block.enums.ChestType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.TexturedRenderLayers;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import revivedevelopment.reviveclient.events.entity.player.InteractBlockEvent;
import revivedevelopment.reviveclient.events.render.Render3DEvent;
import revivedevelopment.reviveclient.renderer.MeshBuilder;
import revivedevelopment.reviveclient.renderer.MeshRenderer;
import revivedevelopment.reviveclient.renderer.ReviveRenderPipelines;
import revivedevelopment.reviveclient.renderer.ShapeMode;
import revivedevelopment.reviveclient.settings.BoolSetting;
import revivedevelopment.reviveclient.settings.ColorSetting;
import revivedevelopment.reviveclient.settings.EnumSetting;
import revivedevelopment.reviveclient.settings.IntSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.settings.StorageBlockListSetting;
import revivedevelopment.reviveclient.systems.modules.Categories;
import revivedevelopment.reviveclient.systems.modules.Module;
import revivedevelopment.reviveclient.utils.Utils;
import revivedevelopment.reviveclient.utils.player.PlayerUtils;
import revivedevelopment.reviveclient.utils.render.RenderUtils;
import revivedevelopment.reviveclient.utils.render.SimpleBlockRenderer;
import revivedevelopment.reviveclient.utils.render.TexturedMeshBuilderVertexConsumerProvider;
import revivedevelopment.reviveclient.utils.render.color.Color;
import revivedevelopment.reviveclient.utils.render.color.SettingColor;

public class StorageESP extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgTexture = this.settings.createGroup("Texture");
   private final SettingGroup sgColors = this.settings.createGroup("Colors");
   private final SettingGroup sgOpened = this.settings.createGroup("Opened");
   private final Setting<List<BlockEntityType<?>>> storageBlocks = this.sgGeneral
      .add(
         new StorageBlockListSetting.Builder()
            .name("storage-blocks")
            .description("Which storage blocks to render.")
            .defaultValue(StorageBlockListSetting.STORAGE_BLOCKS)
            .build()
      );
   private final Setting<ShapeMode> shapeMode = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("shape-mode"))
                  .description("How the boxes are rendered."))
               .defaultValue(ShapeMode.Both))
            .build()
      );
   private final Setting<Integer> fillOpacity = this.sgGeneral
      .add(
         new IntSetting.Builder().name("fill-opacity").description("The opacity of the box sides.").defaultValue(50).range(0, 255).sliderRange(0, 255).build()
      );
   private final Setting<Integer> minDistance = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("min-distance")
            .description("Hide containers closer than this many blocks to you (0 = disabled).")
            .defaultValue(0)
            .range(0, 64)
            .sliderRange(0, 64)
            .build()
      );
   private final Setting<Boolean> tracers = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("tracers")
            .description("Draws a tracer line to every container, matching its highlight color.")
            .defaultValue(false)
            .build()
      );
   private final Setting<Integer> tracerOpacity = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("tracer-opacity")
            .description("The opacity of the tracer lines.")
            .defaultValue(255)
            .range(0, 255)
            .sliderRange(0, 255)
            .visible(this.tracers::get)
            .build()
      );
   private final Setting<Boolean> showTexture = this.sgTexture
      .add(
         new BoolSetting.Builder()
            .name("show-texture")
            .description("Also render the container's real block model (textured, tinted) through walls.")
            .defaultValue(false)
            .build()
      );
   private final Setting<Integer> textureOpacity = this.sgTexture
      .add(
         new IntSetting.Builder()
            .name("texture-opacity")
            .description("Opacity of the textured model (255 = fully solid).")
            .defaultValue(255)
            .range(0, 255)
            .sliderRange(0, 255)
            .visible(this.showTexture::get)
            .build()
      );
   private final Setting<SettingColor> chest = this.sgColors.add(new ColorSetting.Builder().name("chest").defaultValue(new SettingColor(255, 160, 0)).build());
   private final Setting<SettingColor> trappedChest = this.sgColors
      .add(new ColorSetting.Builder().name("trapped-chest").defaultValue(new SettingColor(255, 0, 0)).build());
   private final Setting<SettingColor> barrel = this.sgColors
      .add(new ColorSetting.Builder().name("barrel").defaultValue(new SettingColor(255, 160, 0)).build());
   private final Setting<SettingColor> shulker = this.sgColors
      .add(new ColorSetting.Builder().name("shulker").defaultValue(new SettingColor(255, 0, 200)).build());
   private final Setting<SettingColor> enderChest = this.sgColors
      .add(new ColorSetting.Builder().name("ender-chest").defaultValue(new SettingColor(120, 0, 255)).build());
   private final Setting<SettingColor> other = this.sgColors
      .add(new ColorSetting.Builder().name("other").defaultValue(new SettingColor(140, 140, 140)).build());
   private final Setting<Boolean> hideOpened = this.sgOpened
      .add(new BoolSetting.Builder().name("hide-opened").description("Hide containers you've opened.").defaultValue(false).build());
   private final Setting<SettingColor> openedColor = this.sgOpened
      .add(
         new ColorSetting.Builder()
            .name("opened-color")
            .description("Colour for opened containers (transparent = use the normal colour).")
            .defaultValue(new SettingColor(203, 90, 203, 0))
            .visible(() -> !this.hideOpened.get())
            .build()
      );
   private final Set<BlockPos> interacted = new HashSet<>();
   private final Color line = new Color();
   private final Color side = new Color();
   private final Color tint = new Color();
   private final Color tracer = new Color();
   private final StorageESP.AtlasBatch blockBatch = new StorageESP.AtlasBatch(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE);
   private final StorageESP.AtlasBatch chestBatch = new StorageESP.AtlasBatch(TexturedRenderLayers.CHEST_ATLAS_TEXTURE);
   private final StorageESP.AtlasBatch shulkerBatch = new StorageESP.AtlasBatch(TexturedRenderLayers.SHULKER_BOXES_ATLAS_TEXTURE);
   private final StorageESP.AtlasBatch[] batches = new StorageESP.AtlasBatch[]{this.blockBatch, this.chestBatch, this.shulkerBatch};

   public StorageESP() {
      super(Categories.Render, "storage-esp", "Renders boxes on storage blocks.");
   }

   private StorageESP.AtlasBatch batchFor(BlockEntity blockEntity) {
      if (blockEntity instanceof ShulkerBoxBlockEntity) {
         return this.shulkerBatch;
      } else {
         return !(blockEntity instanceof ChestBlockEntity) && !(blockEntity instanceof EnderChestBlockEntity) ? this.blockBatch : this.chestBatch;
      }
   }

   @Override
   public void onDeactivate() {
      this.interacted.clear();
   }

   @EventHandler
   private void onInteractBlock(InteractBlockEvent event) {
      if (this.mc.world != null) {
         BlockPos pos = event.result.getBlockPos();
         BlockEntity blockEntity = this.mc.world.getBlockEntity(pos);
         if (blockEntity != null) {
            this.interacted.add(pos);
            if (blockEntity instanceof ChestBlockEntity) {
               BlockState state = blockEntity.getCachedState();
               if (state.getBlock() instanceof ChestBlock && state.contains(ChestBlock.CHEST_TYPE)) {
                  ChestType chestType = (ChestType)state.get(ChestBlock.CHEST_TYPE);
                  if (chestType == ChestType.LEFT || chestType == ChestType.RIGHT) {
                     Direction facing = (Direction)state.get(ChestBlock.FACING);
                     this.interacted.add(pos.offset(chestType == ChestType.LEFT ? facing.rotateYClockwise() : facing.rotateYCounterclockwise()));
                  }
               }
            }
         }
      }
   }

   @EventHandler
   private void onRender(Render3DEvent event) {
      if (this.mc.world != null) {
         boolean texture = this.showTexture.get();
         int min = this.minDistance.get();
         double minSq = (double)min * min;

         for (BlockEntity blockEntity : Utils.blockEntities()) {
            if (this.storageBlocks.get().contains(blockEntity.getType())) {
               SettingColor base = this.colorFor(blockEntity);
               if (base != null) {
                  BlockPos pos = blockEntity.getPos();
                  boolean opened = this.interacted.contains(pos);
                  if ((!opened || !this.hideOpened.get())
                     && (min <= 0 || !(PlayerUtils.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) < minSq))) {
                     if (opened && this.openedColor.get().a > 0) {
                        base = this.openedColor.get();
                     }

                     this.line.set((Color)base);
                     this.side.set((Color)base);
                     this.side.a = this.shapeMode.get().sides() ? this.fillOpacity.get() : 0;
                     event.renderer.box(pos, this.side, this.line, this.shapeMode.get(), 0);
                     if (this.tracers.get()) {
                        this.tracer.set((Color)base);
                        this.tracer.a = this.tracerOpacity.get();
                        event.renderer
                           .line(
                              RenderUtils.center.x,
                              RenderUtils.center.y,
                              RenderUtils.center.z,
                              pos.getX() + 0.5,
                              pos.getY() + 0.5,
                              pos.getZ() + 0.5,
                              this.tracer
                           );
                     }

                     if (texture) {
                        StorageESP.AtlasBatch batch = this.batchFor(blockEntity);
                        if (!batch.begun) {
                           batch.mesh.begin();
                           batch.begun = true;
                        }

                        this.tint.set((Color)base);
                        this.tint.a = this.textureOpacity.get();
                        batch.provider.setColor(this.tint);
                        SimpleBlockRenderer.renderWithBlockEntity(blockEntity, event.tickDelta, batch.provider, true);
                        batch.provider.flush();
                     }
                  }
               }
            }
         }

         if (texture) {
            for (StorageESP.AtlasBatch batch : this.batches) {
               if (batch.begun) {
                  batch.begun = false;
                  AbstractTexture atlas = this.mc.getTextureManager().getTexture(batch.atlas);
                  MeshRenderer.begin()
                     .attachments(MinecraftClient.getInstance().getFramebuffer())
                     .pipeline(ReviveRenderPipelines.WORLD_TEXTURED)
                     .sampler("u_Texture", atlas.getGlTextureView(), atlas.getSampler())
                     .mesh(batch.mesh, event.matrices)
                     .end();
               }
            }
         }
      }
   }

   private SettingColor colorFor(BlockEntity blockEntity) {
      if (blockEntity instanceof TrappedChestBlockEntity) {
         return this.trappedChest.get();
      } else if (blockEntity instanceof ChestBlockEntity) {
         return this.chest.get();
      } else if (blockEntity instanceof BarrelBlockEntity) {
         return this.barrel.get();
      } else if (blockEntity instanceof ShulkerBoxBlockEntity) {
         return this.shulker.get();
      } else if (blockEntity instanceof EnderChestBlockEntity) {
         return this.enderChest.get();
      } else {
         return !(blockEntity instanceof AbstractFurnaceBlockEntity)
               && !(blockEntity instanceof BrewingStandBlockEntity)
               && !(blockEntity instanceof DispenserBlockEntity)
               && !(blockEntity instanceof HopperBlockEntity)
               && !(blockEntity instanceof CrafterBlockEntity)
               && !(blockEntity instanceof DecoratedPotBlockEntity)
               && !(blockEntity instanceof ChiseledBookshelfBlockEntity)
            ? null
            : this.other.get();
      }
   }

   private static final class AtlasBatch {
      final Identifier atlas;
      final MeshBuilder mesh;
      final TexturedMeshBuilderVertexConsumerProvider provider;
      boolean begun;

      AtlasBatch(Identifier atlas) {
         this.atlas = atlas;
         this.mesh = new MeshBuilder(ReviveRenderPipelines.WORLD_TEXTURED);
         this.provider = new TexturedMeshBuilderVertexConsumerProvider(this.mesh);
      }
   }
}
