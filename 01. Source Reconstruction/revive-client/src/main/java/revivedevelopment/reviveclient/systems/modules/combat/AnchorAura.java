package revivedevelopment.reviveclient.systems.modules.combat;

import com.google.gson.JsonObject;
import java.util.Locale;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Vector3d;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.events.render.Render2DEvent;
import revivedevelopment.reviveclient.events.render.Render3DEvent;
import revivedevelopment.reviveclient.events.world.TickEvent;
import revivedevelopment.reviveclient.mixininterface.IClientWorld;
import revivedevelopment.reviveclient.pingbypass.PbFx;
import revivedevelopment.reviveclient.pingbypass.PbRenderable;
import revivedevelopment.reviveclient.renderer.ShapeMode;
import revivedevelopment.reviveclient.settings.BoolSetting;
import revivedevelopment.reviveclient.settings.ColorSetting;
import revivedevelopment.reviveclient.settings.DoubleSetting;
import revivedevelopment.reviveclient.settings.EnumSetting;
import revivedevelopment.reviveclient.settings.IntSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.systems.friends.Friends;
import revivedevelopment.reviveclient.systems.modules.Categories;
import revivedevelopment.reviveclient.systems.modules.Module;
import revivedevelopment.reviveclient.utils.entity.DamageUtil;
import revivedevelopment.reviveclient.utils.entity.SortPriority;
import revivedevelopment.reviveclient.utils.player.InvUtils;
import revivedevelopment.reviveclient.utils.render.NametagUtils;
import revivedevelopment.reviveclient.utils.render.color.Color;
import revivedevelopment.reviveclient.utils.render.color.SettingColor;

public class AnchorAura extends Module implements PbRenderable {
   private static final String ROTATION_ID = "anchor-aura";
   private static final float ANCHOR_BLAST_RADIUS = 5.0F;
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgRender = this.settings.createGroup("Render");
   private final Setting<Double> range = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("place-range")
            .description("How far from you an anchor may be placed or detonated.")
            .defaultValue(5.0)
            .min(0.0)
            .max(6.0)
            .sliderRange(0.0, 6.0)
            .build()
      );
   private final Setting<Double> targetRange = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("target-range")
            .description("Max distance to search for a target.")
            .defaultValue(10.0)
            .min(1.0)
            .max(16.0)
            .sliderRange(1.0, 16.0)
            .build()
      );
   private final Setting<Integer> delay = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("delay")
            .description("Ticks to wait between each action (place/charge/detonate). 0 is fastest.")
            .defaultValue(1)
            .min(0)
            .max(20)
            .sliderRange(0, 20)
            .build()
      );
   private final Setting<Double> minDamage = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("min-damage")
            .description("Minimum damage the anchor must deal to the target.")
            .defaultValue(6.0)
            .min(0.0)
            .max(20.0)
            .sliderRange(0.0, 20.0)
            .build()
      );
   private final Setting<Double> maxSelfDamage = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("max-self-damage")
            .description("Never detonate if it would deal more than this to you.")
            .defaultValue(8.0)
            .min(0.0)
            .max(20.0)
            .sliderRange(0.0, 20.0)
            .build()
      );
   private final Setting<Boolean> rotate = this.sgGeneral
      .add(new BoolSetting.Builder().name("rotate").description("Rotate server-side towards the anchor when interacting.").defaultValue(true).build());
   private final Setting<Boolean> swing = this.sgGeneral
      .add(new BoolSetting.Builder().name("swing").description("Swing your hand on place and detonate.").defaultValue(true).build());
   private final Setting<Boolean> render = this.sgRender
      .add(new BoolSetting.Builder().name("render").description("Draw a box on the current anchor target position.").defaultValue(true).build());
   private final Setting<ShapeMode> shapeMode = this.sgRender
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("shape-mode"))
                  .description("How the target box is drawn."))
               .defaultValue(ShapeMode.Both))
            .build()
      );
   private final Setting<SettingColor> sideColor = this.sgRender
      .add(new ColorSetting.Builder().name("side-color").description("Fill colour of the target box.").defaultValue(new SettingColor(255, 0, 0, 75)).build());
   private final Setting<SettingColor> lineColor = this.sgRender
      .add(
         new ColorSetting.Builder().name("line-color").description("Outline colour of the target box.").defaultValue(new SettingColor(255, 0, 0, 255)).build()
      );
   private final Setting<Boolean> damageText = this.sgRender
      .add(new BoolSetting.Builder().name("damage-text").description("Show the calculated damage above the target position.").defaultValue(false).build());
   private int delayLeft;
   private PlayerEntity target;
   private BlockPos targetPos;
   private double targetDamage;

   public AnchorAura() {
      super(Categories.Combat, "anchor-aura", "Automatically places and detonates respawn anchors to damage enemies.");
   }

   @Override
   public void onDeactivate() {
      this.delayLeft = 0;
      this.target = null;
      this.targetPos = null;
      this.targetDamage = 0.0;
   }

   @Override
   public String getInfoString() {
      return this.target != null ? String.format(Locale.US, "%.1f", this.targetDamage) : null;
   }

   @EventHandler
   private void onTick(TickEvent.Pre event) {
      if (this.mc.player != null && this.mc.world != null) {
         if (this.mc.world.getRegistryKey().equals(World.NETHER)) {
            this.info("Respawn anchors don't explode in the Nether. Disabling.", new Object[0]);
            this.toggle();
         } else {
            if (ReviveClient.TARGET.isBadTarget(this.target, this.targetRange.get()) || !Friends.get().shouldAttack(this.target)) {
               this.target = ReviveClient.TARGET.getPlayerTarget(this.targetRange.get(), SortPriority.LowestHealth);
            }

            if (this.target != null && this.target.isAlive()) {
               AnchorAura.AnchorPlan plan = this.findBestPlan(this.target);
               if (plan == null) {
                  this.targetPos = null;
               } else {
                  this.targetPos = plan.pos;
                  this.targetDamage = plan.targetDamage;
                  if (this.delayLeft > 0) {
                     this.delayLeft--;
                  } else {
                     this.delayLeft = this.delay.get();
                     this.execute(plan);
                  }
               }
            } else {
               this.target = null;
               this.targetPos = null;
            }
         }
      } else {
         this.targetPos = null;
      }
   }

   private AnchorAura.AnchorPlan findBestPlan(PlayerEntity target) {
      double rangeVal = this.range.get();
      double rangeSq = rangeVal * rangeVal;
      int r = MathHelper.ceil(rangeVal);
      BlockPos center = BlockPos.ofFloored(target.getEntityPos());
      Vec3d eye = this.mc.player.getEyePos();
      boolean hasAnchor = this.hasItem(Items.RESPAWN_ANCHOR);
      boolean hasGlowstone = this.hasItem(Items.GLOWSTONE);
      AnchorAura.AnchorPlan best = null;

      for (int dx = -r; dx <= r; dx++) {
         for (int dz = -r; dz <= r; dz++) {
            for (int dy = -2; dy <= 2; dy++) {
               BlockPos pos = center.add(dx, dy, dz);
               Vec3d posCenter = pos.toCenterPos();
               if (!(eye.squaredDistanceTo(posCenter) > rangeSq)) {
                  BlockState state = this.mc.world.getBlockState(pos);
                  boolean existingAnchor = state.isOf(Blocks.RESPAWN_ANCHOR);
                  boolean replaceable = state.isReplaceable();
                  if (existingAnchor || replaceable) {
                     boolean needsPlace = !existingAnchor;
                     int charges = existingAnchor && state.contains(Properties.CHARGES) ? (Integer)state.get(Properties.CHARGES) : 0;
                     boolean needsCharge = charges <= 0;
                     if ((!needsPlace || hasAnchor) && (!needsCharge || hasGlowstone)) {
                        double tDmg = this.calcDamage(target, posCenter);
                        if (!(tDmg < this.minDamage.get())) {
                           double sDmg = this.calcDamage(this.mc.player, posCenter);
                           if (!(sDmg > this.maxSelfDamage.get()) && (best == null || tDmg > best.targetDamage)) {
                              best = new AnchorAura.AnchorPlan(pos, tDmg, needsPlace, needsCharge);
                           }
                        }
                     }
                  }
               }
            }
         }
      }

      return best;
   }

   private double calcDamage(LivingEntity entity, Vec3d explosionPos) {
      return DamageUtil.explosionDamage(entity, entity.getEntityPos(), entity.getBoundingBox(), explosionPos, 5.0F, DamageUtil.BLOCK_CHECK, true);
   }

   private void execute(AnchorAura.AnchorPlan plan) {
      if (plan.needsPlace) {
         this.placeAnchor(plan.pos);
      } else if (plan.needsCharge) {
         this.chargeAnchor(plan.pos);
      } else {
         this.detonateAnchor(plan.pos);
      }
   }

   private void placeAnchor(BlockPos pos) {
      if (ReviveClient.BLOCK.checkPlacement(Items.RESPAWN_ANCHOR, pos)) {
         if (this.rotate.get()) {
            this.requestRotation(pos);
         }

         if (ReviveClient.BLOCK.beginPlacement(pos, this.mc.world.getBlockState(pos), Items.RESPAWN_ANCHOR)) {
            try {
               ReviveClient.BLOCK.placeBlock(Items.RESPAWN_ANCHOR, pos);
            } finally {
               ReviveClient.BLOCK.endPlacement();
            }
         }
      }
   }

   private void chargeAnchor(BlockPos pos) {
      if (this.isWithinRange(pos)) {
         boolean offhandGlowstone = this.mc.player.getOffHandStack().isOf(Items.GLOWSTONE);
         Hand hand = offhandGlowstone ? Hand.OFF_HAND : Hand.MAIN_HAND;
         boolean swapped = false;
         if (!offhandGlowstone) {
            if (!ReviveClient.SWAP.beginSwap(Items.GLOWSTONE, true)) {
               return;
            }

            swapped = true;
         }

         try {
            this.interact(pos, hand);
         } finally {
            if (swapped) {
               ReviveClient.SWAP.endSwap(true);
            }
         }
      }
   }

   private void detonateAnchor(BlockPos pos) {
      BlockState state = this.mc.world.getBlockState(pos);
      if (state.isOf(Blocks.RESPAWN_ANCHOR)) {
         int charges = state.contains(Properties.CHARGES) ? (Integer)state.get(Properties.CHARGES) : 0;
         if (charges > 0) {
            if (this.isWithinRange(pos)) {
               Hand hand;
               if (!this.mc.player.getMainHandStack().isOf(Items.GLOWSTONE)) {
                  hand = Hand.MAIN_HAND;
               } else {
                  if (this.mc.player.getOffHandStack().isOf(Items.GLOWSTONE)) {
                     return;
                  }

                  hand = Hand.OFF_HAND;
               }

               this.interact(pos, hand);
            }
         }
      }
   }

   private void interact(BlockPos pos, Hand hand) {
      if (this.mc.getNetworkHandler() != null) {
         if (this.rotate.get()) {
            this.requestRotation(pos);
         }

         Vec3d hitVec = pos.toCenterPos();
         BlockHitResult hit = new BlockHitResult(hitVec, Direction.UP, pos, false);
         int sequence = ((IClientWorld)this.mc.world).revive$getAndIncrementSequence();
         this.mc.getNetworkHandler().sendPacket(new PlayerInteractBlockC2SPacket(hand, hit, sequence));
         if (this.swing.get()) {
            this.mc.player.swingHand(hand);
         }
      }
   }

   private void requestRotation(BlockPos pos) {
      float[] rotation = ReviveClient.ROTATION.getRotation(pos.toCenterPos());
      ReviveClient.ROTATION.requestSilentRotation("anchor-aura", 9.0, rotation[0], rotation[1]);
   }

   private boolean isWithinRange(BlockPos pos) {
      return this.mc.player.getEyePos().squaredDistanceTo(pos.toCenterPos()) <= this.range.get() * this.range.get();
   }

   private boolean hasItem(Item item) {
      return InvUtils.testInHands(item) || InvUtils.find(item).found();
   }

   @EventHandler
   private void onRender3D(Render3DEvent event) {
      if (this.render.get() && this.targetPos != null) {
         SettingColor side = this.sideColor.get();
         SettingColor line = this.lineColor.get();
         event.renderer.box(this.targetPos, new Color(side.r, side.g, side.b, side.a), new Color(line.r, line.g, line.b, line.a), this.shapeMode.get(), 0);
      }
   }

   @EventHandler
   private void onRender2D(Render2DEvent event) {
      if (this.render.get() && this.damageText.get() && this.targetPos != null && this.mc.player != null) {
         Vector3d screen = new Vector3d(this.targetPos.getX() + 0.5, this.targetPos.getY() + 1.3, this.targetPos.getZ() + 0.5);
         if (NametagUtils.to2D(screen, 1.0)) {
            String text = String.format(Locale.US, "%.1f", this.targetDamage);
            int width = this.mc.textRenderer.getWidth(text);
            NametagUtils.begin(screen, event.drawContext);
            event.drawContext.drawText(this.mc.textRenderer, text, -width / 2, -9 / 2, -1, true);
            NametagUtils.end(event.drawContext);
         }
      }
   }

   @Override
   public JsonObject writeRemoteRender() {
      if (this.render.get() && this.targetPos != null) {
         JsonObject state = new JsonObject();
         state.add("pos", PbFx.pos(this.targetPos));
         state.addProperty("dmg", Math.round(this.targetDamage * 10.0) / 10.0);
         return state;
      } else {
         return null;
      }
   }

   @Override
   public void readRemoteRender(JsonObject state, PbFx.Clock clock) {
      this.targetPos = PbFx.pos(state.get("pos"));
      this.targetDamage = PbFx.num(state, "dmg", 0.0);
   }

   @Override
   public void drawRemote3D(Render3DEvent event) {
      this.onRender3D(event);
   }

   @Override
   public void drawRemote2D(Render2DEvent event) {
      this.onRender2D(event);
   }

   @Override
   public void clearRemote() {
      this.targetPos = null;
      this.targetDamage = 0.0;
   }

   private static final class AnchorPlan {
      final BlockPos pos;
      final double targetDamage;
      final boolean needsPlace;
      final boolean needsCharge;

      AnchorPlan(BlockPos pos, double targetDamage, boolean needsPlace, boolean needsCharge) {
         this.pos = pos;
         this.targetDamage = targetDamage;
         this.needsPlace = needsPlace;
         this.needsCharge = needsCharge;
      }
   }
}
