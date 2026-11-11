package revivedevelopment.reviveclient.systems.modules.combat;

import java.util.List;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.util.math.BlockPos;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.events.render.Render3DEvent;
import revivedevelopment.reviveclient.events.world.TickEvent;
import revivedevelopment.reviveclient.renderer.ShapeMode;
import revivedevelopment.reviveclient.settings.BlockListSetting;
import revivedevelopment.reviveclient.settings.BoolSetting;
import revivedevelopment.reviveclient.settings.ColorSetting;
import revivedevelopment.reviveclient.settings.DoubleSetting;
import revivedevelopment.reviveclient.settings.EnumSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.systems.friends.Friends;
import revivedevelopment.reviveclient.systems.modules.Categories;
import revivedevelopment.reviveclient.systems.modules.Module;
import revivedevelopment.reviveclient.utils.entity.EntityUtils;
import revivedevelopment.reviveclient.utils.entity.SortPriority;
import revivedevelopment.reviveclient.utils.render.color.SettingColor;

public class ForceSwim extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgRender = this.settings.createGroup("Render");
   private final Setting<List<Block>> blocks = this.sgGeneral
      .add(
         new BlockListSetting.Builder()
            .name("whitelist")
            .description("Which blocks to use, in order of preference.")
            .defaultValue(Blocks.OBSIDIAN, Blocks.NETHERITE_BLOCK)
            .build()
      );
   private final Setting<Double> range = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("target-range")
            .description("The range players can be targeted.")
            .defaultValue(4.0)
            .min(0.0)
            .max(6.0)
            .sliderRange(0.0, 6.0)
            .build()
      );
   private final Setting<SortPriority> priority = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("target-priority"))
                  .description("How to select the player to target."))
               .defaultValue(SortPriority.LowestHealth))
            .build()
      );
   private final Setting<Boolean> includeCrawling = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("include-crawling")
            .description("Also target players crawling on land (swim pose out of water), not just swimming ones.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> pauseEat = this.sgGeneral
      .add(new BoolSetting.Builder().name("pause-eat").description("Pauses while eating.").defaultValue(true).build());
   private final Setting<Boolean> render = this.sgRender
      .add(new BoolSetting.Builder().name("render").description("Renders an overlay where the block will be placed.").defaultValue(true).build());
   private final Setting<ShapeMode> shapeMode = this.sgRender
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("shape-mode"))
                     .description("How the shapes are rendered."))
                  .defaultValue(ShapeMode.Both))
               .visible(this.render::get))
            .build()
      );
   private final Setting<SettingColor> sideColor = this.sgRender
      .add(
         new ColorSetting.Builder()
            .name("side-color")
            .description("The side color of the target block rendering.")
            .defaultValue(new SettingColor(197, 137, 232, 10))
            .visible(() -> this.render.get() && this.shapeMode.get().sides())
            .build()
      );
   private final Setting<SettingColor> lineColor = this.sgRender
      .add(
         new ColorSetting.Builder()
            .name("line-color")
            .description("The line color of the target block rendering.")
            .defaultValue(new SettingColor(197, 137, 232, 255))
            .visible(() -> this.render.get() && this.shapeMode.get().lines())
            .build()
      );
   private PlayerEntity target;

   public ForceSwim() {
      super(Categories.Combat, "force-swim", "Places a block above swimming / crawling enemies so they can't stand up.");
   }

   @Override
   public void onActivate() {
      this.target = null;
   }

   @Override
   public void onDeactivate() {
      this.target = null;
   }

   @EventHandler
   private void onTick(TickEvent.Pre event) {
      if (this.mc.player != null && this.mc.world != null) {
         this.target = this.findTarget();
         if (this.target != null) {
            if (!this.pauseEat.get() || !this.mc.player.isUsingItem()) {
               BlockPos above = this.target.getBlockPos().up();
               if (this.mc.world.getBlockState(above).isReplaceable()) {
                  Item item = this.findUseItem();
                  if (item != null) {
                     if (ReviveClient.BLOCK.beginPlacement(above, this.mc.world.getBlockState(above), item)) {
                        try {
                           ReviveClient.BLOCK.placeBlock(item, above);
                        } finally {
                           ReviveClient.BLOCK.endPlacement();
                        }
                     }
                  }
               }
            }
         }
      } else {
         this.target = null;
      }
   }

   private PlayerEntity findTarget() {
      double r = this.range.get();
      return (PlayerEntity)ReviveClient.TARGET.get(entity -> {
         if (!(entity instanceof PlayerEntity player && player != this.mc.player)) {
            return false;
         } else if (ReviveClient.TARGET.isBadTarget(player, r)) {
            return false;
         } else {
            return !Friends.get().shouldAttack(player) ? false : this.isForced(player);
         }
      }, this.priority.get());
   }

   private boolean isForced(PlayerEntity player) {
      return player.isSwimming() || this.includeCrawling.get() && player.isCrawling();
   }

   private Item findUseItem() {
      for (Block block : this.blocks.get()) {
         Item item = block.asItem();
         if (ReviveClient.SWAP.canSwap(item)) {
            return item;
         }
      }

      return null;
   }

   @EventHandler
   private void onRender(Render3DEvent event) {
      if (this.render.get() && this.mc.player != null && this.mc.world != null) {
         if (this.target != null && this.isForced(this.target)) {
            BlockPos above = this.target.getBlockPos().up();
            if (this.mc.world.getBlockState(above).isReplaceable()) {
               event.renderer.box(above, this.sideColor.get(), this.lineColor.get(), this.shapeMode.get(), 0);
            }
         }
      }
   }

   @Override
   public String getInfoString() {
      return this.target == null ? null : EntityUtils.getName(this.target);
   }
}
