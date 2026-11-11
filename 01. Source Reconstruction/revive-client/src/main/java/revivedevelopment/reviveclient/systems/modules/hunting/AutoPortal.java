package revivedevelopment.reviveclient.systems.modules.hunting;

import java.util.ArrayList;
import java.util.List;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.events.render.Render3DEvent;
import revivedevelopment.reviveclient.events.world.TickEvent;
import revivedevelopment.reviveclient.mixininterface.IClientWorld;
import revivedevelopment.reviveclient.renderer.ShapeMode;
import revivedevelopment.reviveclient.settings.BoolSetting;
import revivedevelopment.reviveclient.settings.ColorSetting;
import revivedevelopment.reviveclient.settings.IntSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.systems.config.AntiCheatConfig;
import revivedevelopment.reviveclient.systems.managers.PacketManager;
import revivedevelopment.reviveclient.systems.modules.Categories;
import revivedevelopment.reviveclient.systems.modules.Module;
import revivedevelopment.reviveclient.systems.modules.Modules;
import revivedevelopment.reviveclient.systems.modules.player.PingBypass;
import revivedevelopment.reviveclient.systems.modules.player.SilentMine;
import revivedevelopment.reviveclient.utils.player.FindItemResult;
import revivedevelopment.reviveclient.utils.player.InvUtils;
import revivedevelopment.reviveclient.utils.render.color.Color;
import revivedevelopment.reviveclient.utils.render.color.SettingColor;
import revivedevelopment.reviveclient.utils.world.BlockUtils;

public class AutoPortal extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private static final Color BLOCKED = new Color(255, 45, 45, 255);
   private static final Color TRANSPARENT = new Color(0, 0, 0, 0);
   private static final int OBSIDIAN_NEEDED = 10;
   private final Setting<Boolean> smartPlacement = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("smart-placement")
            .description("Scans in front of you for the best portal spot - prefers one needing no blocks broken.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> breakBlocks = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("break-blocks")
            .description("Mines obstructing blocks itself when no clear spot exists (uses SilentMine if it's active).")
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> autoDisable = this.sgGeneral
      .add(new BoolSetting.Builder().name("auto-disable").description("Automatically disables the module when a portal is built.").defaultValue(true).build());
   private final Setting<Boolean> pauseOnInput = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("pause-on-input")
            .description("Pauses the building process while you are moving, sneaking or jumping.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> renderPos = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("render-portal")
            .description("Renders the build as SilentMine-style boxes: 100% placed, 50% to place, 25% to break (red).")
            .defaultValue(true)
            .build()
      );
   private final Setting<SettingColor> renderColor = this.sgGeneral
      .add(
         new ColorSetting.Builder()
            .name("render-color")
            .description("Color for the placed / to-be-placed boxes.")
            .defaultValue(new SettingColor(0, 225, 255, 200))
            .visible(this.renderPos::get)
            .build()
      );
   private final Setting<Integer> fillOpacity = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("fill-opacity")
            .description("Opacity of the sides-only box fill.")
            .defaultValue(40)
            .min(0)
            .max(255)
            .sliderMax(255)
            .visible(this.renderPos::get)
            .build()
      );
   private boolean locked;
   private AutoPortal.Phase phase = AutoPortal.Phase.PLACE;
   private final List<BlockPos> frame = new ArrayList<>();
   private final List<BlockPos> interior = new ArrayList<>();
   private BlockPos lightPos;
   private boolean toolSwapped;

   public AutoPortal() {
      super(Categories.Hunting, "auto-portal", "Automatically builds and lights a nether portal in front of you.");
   }

   @Override
   public void onActivate() {
      this.locked = false;
      this.phase = AutoPortal.Phase.PLACE;
      this.frame.clear();
      this.interior.clear();
      this.lightPos = null;
      this.toolSwapped = false;
   }

   @Override
   public void onDeactivate() {
      this.frame.clear();
      this.interior.clear();
      this.lightPos = null;
      if (this.toolSwapped) {
         InvUtils.swapBack();
         this.toolSwapped = false;
      }
   }

   @EventHandler
   private void onTick(TickEvent.Pre event) {
      if (this.mc.player != null && this.mc.world != null && this.mc.interactionManager != null) {
         if (!this.locked) {
            this.computePositions();
         }

         if (!this.pauseOnInput.get() || !this.isInputting()) {
            if (!this.locked) {
               int obsidianNeeded = Math.max(this.frame.size(), 10);
               int obsidianCount = this.countItem(Items.OBSIDIAN);
               if (obsidianCount < obsidianNeeded) {
                  this.error("Not enough obsidian (need %d, have %d), disabling.", new Object[]{obsidianNeeded, obsidianCount});
                  this.toggle();
                  return;
               }

               if (!InvUtils.find(Items.FLINT_AND_STEEL).found()) {
                  this.error("No flint and steel found in inventory, disabling.", new Object[0]);
                  this.toggle();
                  return;
               }

               this.locked = true;
               this.phase = AutoPortal.Phase.PLACE;
               int breaks = this.countBreaks();
               if (breaks == 0) {
                  this.info("Position locked, building portal...", new Object[0]);
               } else if (this.breakBlocks.get()) {
                  this.info("Position locked - no clear spot found, mining %d obstructing block(s) first.", new Object[]{breaks});
               } else {
                  this.info("Position locked - no clear spot found, %d block(s) must be broken first (shown in red).", new Object[]{breaks});
               }
            }

            if (this.breakBlocks.get() && this.phase != AutoPortal.Phase.BREAK && this.countBreaks() > 0) {
               this.phase = AutoPortal.Phase.BREAK;
            }

            if (this.phase != AutoPortal.Phase.BREAK) {
               SilentMine sm = Modules.get().get(SilentMine.class);
               if (sm.isRunningHere()) {
                  for (BlockPos pos : this.frame) {
                     sm.abortRebreak(pos);
                  }

                  for (BlockPos pos : this.interior) {
                     sm.abortRebreak(pos);
                  }
               } else if (sm.isActive() && sm.isRemote()) {
                  PingBypass pingBypass = Modules.get().get(PingBypass.class);
                  if (pingBypass != null) {
                     List<BlockPos> all = new ArrayList<>(this.frame);
                     all.addAll(this.interior);
                     pingBypass.forwardAbortRebreak(all);
                  }
               }
            }

            switch (this.phase) {
               case BREAK:
                  this.tickBreak();
                  break;
               case PLACE:
                  this.tickPlace();
                  break;
               case LIGHT:
                  this.tickLight();
                  break;
               case CONFIRM:
                  this.tickConfirm();
            }
         }
      }
   }

   private void tickBreak() {
      if (this.countBreaks() == 0) {
         if (this.toolSwapped) {
            InvUtils.swapBack();
            this.toolSwapped = false;
         }

         this.phase = AutoPortal.Phase.PLACE;
      } else {
         List<BlockPos> obstructions = this.minableObstructions();
         if (!obstructions.isEmpty()) {
            SilentMine sm = Modules.get().get(SilentMine.class);
            if (sm.isRunningHere()) {
               for (BlockPos pos : obstructions) {
                  sm.silentBreakBlock(pos, BlockUtils.getDirection(pos), 4.0);
               }
            } else {
               if (sm.isActive() && sm.isRemote()) {
                  PingBypass pingBypass = Modules.get().get(PingBypass.class);
                  boolean sent = pingBypass != null;

                  for (BlockPos pos : obstructions) {
                     if (sent) {
                        sent = pingBypass.forwardMine(pos, BlockUtils.getDirection(pos), 4.0, true);
                     }
                  }

                  if (sent) {
                     return;
                  }
               }

               BlockPos target = null;
               double best = Double.MAX_VALUE;

               for (BlockPos posx : obstructions) {
                  double d = this.mc.player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(posx));
                  if (d < best) {
                     best = d;
                     target = posx;
                  }
               }

               FindItemResult tool = InvUtils.findFastestToolHotbar(this.mc.world.getBlockState(target));
               if (tool.found() && this.mc.player.getInventory().selectedSlot != tool.slot()) {
                  InvUtils.swap(tool.slot(), true);
                  this.toolSwapped = true;
               }

               if (AntiCheatConfig.get().blockRotatePlace.get()) {
                  ReviveClient.ROTATION.snapAt(Vec3d.ofCenter(target), true);
               }

               BlockUtils.breakBlock(target, true);
            }
         }
      }
   }

   private List<BlockPos> minableObstructions() {
      List<BlockPos> list = new ArrayList<>();

      for (BlockPos pos : this.frame) {
         if (this.needsBreaking(pos, false) && this.inReach(pos) && BlockUtils.canBreak(pos, this.mc.world.getBlockState(pos))) {
            list.add(pos);
         }
      }

      for (BlockPos posx : this.interior) {
         if (this.needsBreaking(posx, true) && this.inReach(posx) && BlockUtils.canBreak(posx, this.mc.world.getBlockState(posx))) {
            list.add(posx);
         }
      }

      return list;
   }

   private void tickPlace() {
      boolean anyPlaceable = false;

      for (BlockPos pos : this.frame) {
         if (this.needsPlacing(pos)) {
            anyPlaceable = true;
            break;
         }
      }

      if (!anyPlaceable) {
         if (this.isFrameComplete()) {
            this.phase = AutoPortal.Phase.LIGHT;
         }
      } else if (ReviveClient.BLOCK.beginPlacement(this.frame, Items.OBSIDIAN)) {
         try {
            for (BlockPos posx : this.frame) {
               if (this.needsPlacing(posx)) {
                  ReviveClient.BLOCK.placeBlock(Items.OBSIDIAN, posx);
               }
            }
         } finally {
            ReviveClient.BLOCK.endPlacement();
         }
      }
   }

   private boolean needsPlacing(BlockPos pos) {
      BlockState state = this.mc.world.getBlockState(pos);
      return state.getBlock() != Blocks.OBSIDIAN && state.isReplaceable() && this.inReach(pos);
   }

   private void tickLight() {
      if (this.lightPos != null && this.inReach(this.lightPos)) {
         if (ReviveClient.PACKET.canSend(PacketManager.Category.BLOCK_INTERACT)) {
            if (!ReviveClient.SWAP.beginSwap(Items.FLINT_AND_STEEL, true)) {
               if (!InvUtils.find(Items.FLINT_AND_STEEL).found()) {
                  this.error("Lost flint and steel during build, disabling.", new Object[0]);
                  this.toggle();
               }
            } else {
               try {
                  Vec3d hitPos = Vec3d.ofCenter(this.lightPos).add(0.0, 0.5, 0.0);
                  if (AntiCheatConfig.get().blockRotatePlace.get()) {
                     ReviveClient.ROTATION.snapAt(hitPos, true);
                  }

                  BlockHitResult hit = new BlockHitResult(hitPos, Direction.UP, this.lightPos, false);
                  this.mc.getNetworkHandler().sendPacket(new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND, hit, this.nextSequence()));
                  this.mc.player.swingHand(Hand.MAIN_HAND);
                  this.phase = AutoPortal.Phase.CONFIRM;
               } finally {
                  ReviveClient.SWAP.endSwap(true);
               }
            }
         }
      }
   }

   private void tickConfirm() {
      if (this.isLit()) {
         this.info("Portal built successfully!", new Object[0]);
         if (this.autoDisable.get()) {
            this.toggle();
         }
      } else {
         this.phase = AutoPortal.Phase.LIGHT;
      }
   }

   private void computePositions() {
      this.frame.clear();
      this.interior.clear();
      this.lightPos = null;
      BlockPos foot = this.mc.player.getBlockPos();
      Direction facing = this.facingFromYaw(this.mc.player.getYaw());
      int preferredY = foot.getY() + this.yOffsetFromPitch(this.mc.player.getPitch());
      int dist = 2;
      int baseY = preferredY;
      if (this.smartPlacement.get()) {
         int bestScore = Integer.MAX_VALUE;
         List<BlockPos> candidateFrame = new ArrayList<>();
         List<BlockPos> candidateInterior = new ArrayList<>();

         for (int d = 2; d <= 4; d++) {
            for (int dy = -2; dy <= 3; dy++) {
               int y = preferredY + dy;
               candidateFrame.clear();
               candidateInterior.clear();
               this.collectPlane(foot, facing, d, y, candidateFrame, candidateInterior);
               int score = this.scorePlane(candidateFrame, candidateInterior, y) + Math.abs(dy) * 2 + (d - 2);
               if (score < bestScore) {
                  bestScore = score;
                  dist = d;
                  baseY = y;
               }
            }
         }
      }

      this.collectPlane(foot, facing, dist, baseY, this.frame, this.interior);
      if (this.smartPlacement.get() && this.isBottomUnsupported(this.frame, baseY)) {
         int feetY = (int)Math.ceil(this.mc.player.getY());
         if (baseY < feetY) {
            List<BlockPos> raisedFrame = new ArrayList<>();
            List<BlockPos> raisedInterior = new ArrayList<>();
            this.collectPlane(foot, facing, dist, feetY, raisedFrame, raisedInterior);
            if (this.countBreaks(raisedFrame, raisedInterior) == 0) {
               baseY = feetY;
               this.frame.clear();
               this.interior.clear();
               this.frame.addAll(raisedFrame);
               this.interior.addAll(raisedInterior);
            }
         }
      }

      for (BlockPos pos : this.frame) {
         if (pos.getY() == baseY) {
            this.lightPos = pos;
            break;
         }
      }

      if (this.isBottomUnsupported(this.frame, baseY)) {
         for (BlockPos corner : this.bottomCorners(foot, facing, dist, baseY)) {
            BlockState state = this.mc.world.getBlockState(corner);
            if (state.getBlock() == Blocks.OBSIDIAN || state.isReplaceable()) {
               this.frame.add(corner);
            }
         }
      }
   }

   private void collectPlane(BlockPos foot, Direction facing, int dist, int baseY, List<BlockPos> frameOut, List<BlockPos> interiorOut) {
      int fx = facing.getOffsetX();
      int fz = facing.getOffsetZ();
      int rx = -fz;
      int rz = fx;
      int planeX = foot.getX() + fx * dist;
      int planeZ = foot.getZ() + fz * dist;

      for (int h = 0; h < 5; h++) {
         for (int w = -1; w <= 2; w++) {
            BlockPos pos = new BlockPos(planeX + rx * w, baseY + h, planeZ + rz * w);
            boolean isCorner = (h == 0 || h == 4) && (w == -1 || w == 2);
            boolean isInterior = h >= 1 && h <= 3 && w >= 0 && w <= 1;
            if (!isCorner) {
               if (isInterior) {
                  interiorOut.add(pos);
               } else {
                  frameOut.add(pos);
               }
            }
         }
      }
   }

   private int scorePlane(List<BlockPos> frameList, List<BlockPos> interiorList, int baseY) {
      int score = 0;

      for (BlockPos pos : frameList) {
         if (this.needsBreaking(pos, false)) {
            score += BlockUtils.canBreak(pos, this.mc.world.getBlockState(pos)) ? 100 : 1000;
         }

         if (!this.inReach(pos)) {
            score += 1000;
         }

         if (pos.getY() == baseY && this.mc.world.getBlockState(pos.down()).isReplaceable()) {
            score += 5;
         }
      }

      for (BlockPos pos : interiorList) {
         if (this.needsBreaking(pos, true)) {
            score += BlockUtils.canBreak(pos, this.mc.world.getBlockState(pos)) ? 100 : 1000;
         }

         if (!this.inReach(pos)) {
            score += 1000;
         }
      }

      return score;
   }

   private boolean needsBreaking(BlockPos pos, boolean isInterior) {
      BlockState state = this.mc.world.getBlockState(pos);
      return isInterior
         ? state.getBlock() != Blocks.NETHER_PORTAL && state.getBlock() != Blocks.FIRE && !state.isReplaceable()
         : state.getBlock() != Blocks.OBSIDIAN && !state.isReplaceable();
   }

   private int countBreaks() {
      return this.countBreaks(this.frame, this.interior);
   }

   private int countBreaks(List<BlockPos> frameList, List<BlockPos> interiorList) {
      int n = 0;

      for (BlockPos pos : frameList) {
         if (this.needsBreaking(pos, false)) {
            n++;
         }
      }

      for (BlockPos posx : interiorList) {
         if (this.needsBreaking(posx, true)) {
            n++;
         }
      }

      return n;
   }

   private boolean isBottomUnsupported(List<BlockPos> frameList, int baseY) {
      for (BlockPos pos : frameList) {
         if (pos.getY() == baseY && !this.mc.world.getBlockState(pos.down()).isReplaceable()) {
            return false;
         }
      }

      return true;
   }

   private List<BlockPos> bottomCorners(BlockPos foot, Direction facing, int dist, int baseY) {
      int fx = facing.getOffsetX();
      int fz = facing.getOffsetZ();
      int rx = -fz;
      int planeX = foot.getX() + fx * dist;
      int planeZ = foot.getZ() + fz * dist;
      return List.of(new BlockPos(planeX - rx, baseY, planeZ - fx), new BlockPos(planeX + rx * 2, baseY, planeZ + fx * 2));
   }

   private Direction facingFromYaw(float yaw) {
      float n = (yaw % 360.0F + 360.0F) % 360.0F;
      if (n >= 315.0F || n < 45.0F) {
         return Direction.SOUTH;
      } else if (n < 135.0F) {
         return Direction.WEST;
      } else {
         return n < 225.0F ? Direction.NORTH : Direction.EAST;
      }
   }

   private int yOffsetFromPitch(float pitch) {
      float clamped = Math.max(-45.0F, Math.min(45.0F, pitch));
      int step = Math.round(-clamped / 9.0F);
      return Math.max(-5, Math.min(5, step));
   }

   private boolean isFrameComplete() {
      for (BlockPos pos : this.frame) {
         if (this.mc.world.getBlockState(pos).getBlock() != Blocks.OBSIDIAN) {
            return false;
         }
      }

      return true;
   }

   private boolean isLit() {
      for (BlockPos pos : this.interior) {
         if (this.mc.world.getBlockState(pos).getBlock() == Blocks.NETHER_PORTAL) {
            return true;
         }
      }

      return false;
   }

   private boolean isInputting() {
      return this.mc.options.forwardKey.isPressed()
         || this.mc.options.backKey.isPressed()
         || this.mc.options.leftKey.isPressed()
         || this.mc.options.rightKey.isPressed()
         || this.mc.options.jumpKey.isPressed()
         || this.mc.options.sneakKey.isPressed();
   }

   private boolean inReach(BlockPos pos) {
      double range = Math.min(4.0, AntiCheatConfig.get().blockInteractRange.get());
      return new Box(pos).squaredMagnitude(this.mc.player.getEyePos()) <= range * range;
   }

   private int countItem(Item item) {
      int count = 0;

      for (int i = 0; i < this.mc.player.getInventory().size(); i++) {
         ItemStack stack = this.mc.player.getInventory().getStack(i);
         if (stack.isOf(item)) {
            count += stack.getCount();
         }
      }

      return count;
   }

   private int nextSequence() {
      return ((IClientWorld)this.mc.world).revive$getAndIncrementSequence();
   }

   @EventHandler
   private void onRender(Render3DEvent event) {
      if (this.mc.player != null && this.mc.world != null) {
         if (this.renderPos.get()) {
            if (!this.frame.isEmpty() || !this.interior.isEmpty()) {
               for (BlockPos pos : this.frame) {
                  if (this.needsBreaking(pos, false)) {
                     this.renderStateBox(event, pos, BLOCKED, 0.25);
                  } else if (this.mc.world.getBlockState(pos).getBlock() == Blocks.OBSIDIAN) {
                     this.renderStateBox(event, pos, this.renderColor.get(), 1.0);
                  } else {
                     this.renderStateBox(event, pos, this.renderColor.get(), 0.5);
                  }
               }

               for (BlockPos posx : this.interior) {
                  if (this.needsBreaking(posx, true)) {
                     this.renderStateBox(event, posx, BLOCKED, 0.25);
                  }
               }
            }
         }
      }
   }

   private void renderStateBox(Render3DEvent event, BlockPos pos, Color color, double scale) {
      if (this.fillOpacity.get() > 0) {
         event.renderer.box(scaleBox(this.getBlockBox(pos), scale), withA(color, this.fillOpacity.get()), TRANSPARENT, ShapeMode.Sides, 0);
      }
   }

   private Box getBlockBox(BlockPos pos) {
      VoxelShape shape = this.mc.world.getBlockState(pos).getOutlineShape(this.mc.world, pos);
      return shape != null && !shape.isEmpty() ? shape.getBoundingBox().offset(pos.getX(), pos.getY(), pos.getZ()) : new Box(pos);
   }

   private static Color withA(Color c, int a) {
      return new Color(c.r, c.g, c.b, a);
   }

   private static Box scaleBox(Box box, double scale) {
      if (scale >= 1.0) {
         return box;
      } else {
         if (scale <= 0.0) {
            scale = 0.001;
         }

         double cx = (box.minX + box.maxX) / 2.0;
         double cy = (box.minY + box.maxY) / 2.0;
         double cz = (box.minZ + box.maxZ) / 2.0;
         double hw = (box.maxX - box.minX) / 2.0 * scale;
         double hh = (box.maxY - box.minY) / 2.0 * scale;
         double hd = (box.maxZ - box.minZ) / 2.0 * scale;
         return new Box(cx - hw, cy - hh, cz - hd, cx + hw, cy + hh, cz + hd);
      }
   }

   private static enum Phase {
      BREAK,
      PLACE,
      LIGHT,
      CONFIRM;
   }
}
