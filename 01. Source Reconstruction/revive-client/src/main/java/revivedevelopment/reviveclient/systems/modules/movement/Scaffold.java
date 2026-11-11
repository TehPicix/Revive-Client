package revivedevelopment.reviveclient.systems.modules.movement;

import java.util.List;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.FallingBlock;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.PlayerInputC2SPacket;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.events.entity.player.ClipAtLedgeEvent;
import revivedevelopment.reviveclient.events.entity.player.SendMovementPacketsEvent;
import revivedevelopment.reviveclient.events.packets.PacketEvent;
import revivedevelopment.reviveclient.events.world.TickEvent;
import revivedevelopment.reviveclient.settings.BlockListSetting;
import revivedevelopment.reviveclient.settings.BoolSetting;
import revivedevelopment.reviveclient.settings.DoubleSetting;
import revivedevelopment.reviveclient.settings.IntSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.systems.managers.ElytraDurabilitySaver;
import revivedevelopment.reviveclient.systems.modules.Categories;
import revivedevelopment.reviveclient.systems.modules.Module;
import revivedevelopment.reviveclient.utils.player.ChatUtils;
import revivedevelopment.reviveclient.utils.player.FindItemResult;
import revivedevelopment.reviveclient.utils.player.InvUtils;
import revivedevelopment.reviveclient.utils.world.BlockUtils;

public class Scaffold extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgEagle = this.settings.createGroup("Eagle");
   private final Setting<List<Block>> whitelist = this.sgGeneral
      .add(
         new BlockListSetting.Builder()
            .name("blocks")
            .description("Blocks allowed for bridging. Leave empty to use any solid full block in your hotbar.")
            .filter(Scaffold::isScaffoldCandidate)
            .build()
      );
   private final Setting<Boolean> rotate = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("rotate")
            .description(
               "Aim the server-side look at the support block for each placement (your camera does not move). Needed on Grim servers such as 2b2t, which reject a placement the look does not reach. Forward bridging turns the look behind you for the placement tick with the movement keys redirected to match (MoveFix), so you keep moving; sprint drops for that one tick."
            )
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> autoSwitch = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("auto-switch")
            .description("Switch to a usable block in your hotbar automatically. When off, only places while one is held.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> keepY = this.sgGeneral
      .add(new BoolSetting.Builder().name("keep-y").description("Keep the bridge at the height you started on while jumping.").defaultValue(false).build());
   private final Setting<Boolean> tower = this.sgGeneral
      .add(new BoolSetting.Builder().name("tower").description("Jump continuously while holding the jump key to tower upward.").defaultValue(true).build());
   private final Setting<Boolean> safeWalk = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("safe-walk")
            .description("Keeps you from walking off edges, like sneaking without the slowdown.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> swing = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("swing")
            .description("Swing the hand client-side on each placement; otherwise only the swing packet is sent.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Integer> searchRadius = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("search-radius")
            .description("How far around the target to look for a supported cell when the bridge has to extend.")
            .defaultValue(3)
            .range(1, 4)
            .sliderRange(1, 4)
            .build()
      );
   private final Setting<Boolean> debug = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("debug")
            .description("Log placement decisions to chat (block found, chosen target, or why nothing could be placed).")
            .defaultValue(false)
            .build()
      );
   private final Setting<Boolean> eagle = this.sgEagle
      .add(new BoolSetting.Builder().name("eagle").description("Briefly sneak at block edges while bridging.").defaultValue(false).build());
   private final Setting<Double> edgeDistance = this.sgEagle
      .add(
         new DoubleSetting.Builder()
            .name("edge-distance")
            .description("How close to a block edge you must be before eagle sneaks.")
            .defaultValue(0.13)
            .range(0.0, 0.5)
            .sliderRange(0.0, 0.5)
            .visible(this.eagle::get)
            .build()
      );
   private final Setting<Integer> sneakDelay = this.sgEagle
      .add(
         new IntSetting.Builder()
            .name("sneak-delay")
            .description("Minimum milliseconds between eagle sneaks.")
            .defaultValue(80)
            .range(0, 500)
            .sliderRange(0, 500)
            .visible(this.eagle::get)
            .build()
      );
   private int startY;
   private int returnSlot = -1;
   private boolean eagleSneakForced = false;
   private int eagleSneakTicks = 0;
   private long eagleLastSneakTime = 0L;
   private String lastDebug;
   private long lastDebugMs;
   private static final double LOOK_PRIORITY = 1500.0;
   private static final double FACE_MARGIN = 0.03;
   private static final double EXACT_MARGIN = 0.005;
   private static final double AIM_INSET = 0.05;
   private static final double REACH_SLACK = 0.15;
   private boolean hiddenFaceSeen;
   private Scaffold.Placement pending;
   private FindItemResult pendingBlocks;
   private boolean stillTick;
   private boolean moveFixTick;
   private float fixedYaw;
   private boolean armed;
   private float aimYaw;
   private float aimPitch;
   private PlayerInput lastSentInput;
   private boolean bypassInputIntercept;

   public Scaffold() {
      super(Categories.Movement, "scaffold", "Automatically places blocks beneath you while bridging.");
   }

   @Override
   public void onActivate() {
      this.startY = this.mc.player != null ? this.mc.player.getBlockY() : 0;
      this.returnSlot = -1;
      this.pending = null;
      this.pendingBlocks = null;
      this.stillTick = false;
      this.armed = false;
      this.lastSentInput = null;
      this.eagleSneakTicks = 0;
      this.eagleLastSneakTime = 0L;
      this.releaseEagleSneak();
   }

   @Override
   public void onDeactivate() {
      this.releaseEagleSneak();
      this.restoreSlot();
      this.pending = null;
      this.armed = false;
      this.stillTick = false;
      if (this.mc.player != null
         && this.mc.getNetworkHandler() != null
         && this.lastSentInput != null
         && !this.sprintManagesInput()
         && !this.lastSentInput.equals(this.mc.player.input.playerInput)) {
         this.sendInput(this.mc.player.input.playerInput);
      }

      this.lastSentInput = null;
   }

   @EventHandler(
      priority = 100
   )
   private void onTick(TickEvent.Pre event) {
      this.pending = null;
      this.pendingBlocks = null;
      this.stillTick = false;
      this.moveFixTick = false;
      this.armed = false;
      if (this.mc.player != null && this.mc.world != null && this.mc.interactionManager != null) {
         FindItemResult blocks = this.findBlocks();
         if (!blocks.found()) {
            this.dbg(this.autoSwitch.get() ? "no bridging block in hotbar" : "no bridging block held (auto-switch off)");
            this.releaseEagleSneak();
            this.restoreSlot();
         } else {
            boolean jumping = this.mc.options.jumpKey.isPressed();
            if (this.mc.player.isOnGround()) {
               this.startY = this.mc.player.getBlockY();
            }

            this.updateEagle();
            if (this.tower.get() && jumping && !this.mc.player.isSneaking() && this.mc.player.isOnGround()) {
               this.mc.player.setVelocity(this.mc.player.getVelocity().x, 0.42, this.mc.player.getVelocity().z);
            }

            int feetY = this.mc.player.getBlockY();
            int bridgeY = this.keepY.get() && !jumping ? Math.min(feetY, this.startY) : feetY;
            Scaffold.Placement placement = this.findTarget(bridgeY);
            if (placement == null) {
               this.dbg(
                  this.hiddenFaceSeen
                     ? "waiting: the only support face is hidden from your eye (the anticheat rejects that click) - it opens once your head is past the edge"
                     : "no placeable cell in reach under/around you (need a solid face to place against)"
               );
            } else {
               this.pending = placement;
               this.pendingBlocks = blocks;
               if (this.rotate.get()) {
                  Box support = new Box(placement.pos().offset(placement.side()));
                  Vec3d eye = this.projectedEye();
                  this.moveFixTick = this.aimWithYaw(eye, this.mc.player.getYaw(), support) == null;
                  Sprint sprint = Sprint.get();
                  if (this.moveFixTick) {
                     float[] aim = this.aimFree(eye, support);
                     if (aim == null) {
                        this.dbg("no look from here reaches the support of " + fmt(placement.pos()) + " - next tick");
                        this.pending = null;
                        this.pendingBlocks = null;
                        this.moveFixTick = false;
                        return;
                     }

                     this.fixedYaw = aim[0];
                     ReviveClient.ROTATION.requestMotionRotation(aim[0], aim[1], 1500.0, true);
                  } else if (sprint != null && sprint.isActive() && sprint.isMotionSpoofActive()) {
                     sprint.stopSprinting(2);
                  }
               }
            }
         }
      }
   }

   @EventHandler(
      priority = 1000
   )
   private void onSendMovementPre(SendMovementPacketsEvent.Pre event) {
      this.armed = false;
      if (this.pending != null && this.mc.player != null && this.mc.world != null) {
         Vec3d eye = this.mc.player.getEyePos();
         Direction side = this.legalPlaceSide(this.pending.pos(), eye, 0.005);
         if (side == null) {
            this.dbg("held a tick: from where this move packet puts your eye no support face of " + fmt(this.pending.pos()) + " is open yet");
            this.pending = null;
            this.syncServerInput();
         } else {
            this.pending = new Scaffold.Placement(this.pending.pos(), side);
            if (!this.rotate.get()) {
               this.armed = true;
               this.syncServerInput();
            } else {
               Box support = new Box(this.pending.pos().offset(side));
               float[] aim = this.aimWithYaw(eye, this.moveFixTick ? this.fixedYaw : this.mc.player.getYaw(), support);
               if (aim == null) {
                  this.dbg(
                     "held a tick: no pitch at "
                        + (this.moveFixTick ? "the placement yaw" : "your yaw")
                        + " from here reaches the support of "
                        + fmt(this.pending.pos())
                  );
                  this.pending = null;
                  this.syncServerInput();
               } else {
                  this.aimYaw = aim[0];
                  this.aimPitch = aim[1];
                  ReviveClient.ROTATION.requestMotionRotation(this.aimYaw, this.aimPitch, 1500.0, this.moveFixTick);
                  this.armed = true;
                  this.syncServerInput();
               }
            }
         }
      } else {
         this.syncServerInput();
      }
   }

   @EventHandler
   private void onSendMovementPost(SendMovementPacketsEvent.Post event) {
      if (this.armed) {
         this.armed = false;
         Scaffold.Placement p = this.pending;
         FindItemResult blocks = this.pendingBlocks;
         this.pending = null;
         if (p != null && blocks != null && this.mc.player != null && this.mc.world != null) {
            float serverYaw = ReviveClient.ROTATION.getServerYaw();
            float serverPitch = ReviveClient.ROTATION.getServerPitch();
            if (!this.rotate.get() || !(Math.abs(MathHelper.wrapDegrees(serverYaw - this.aimYaw)) > 0.5F) && !(Math.abs(serverPitch - this.aimPitch) > 0.5F)) {
               if (blocks.isHotbar() && this.returnSlot == -1) {
                  this.returnSlot = this.mc.player.getInventory().selectedSlot;
               }

               boolean placed = BlockUtils.place(p.pos(), blocks, p.side(), false, 50, this.swing.get(), true, false);
               this.dbg(
                  placed
                     ? String.format(
                        "placing at %s against the %s face of its %s neighbour - %s, look %.1f/%.1f",
                        fmt(p.pos()),
                        p.side().getOpposite().asString(),
                        p.side().asString(),
                        !this.rotate.get()
                           ? "no look (rotate off)"
                           : (this.moveFixTick ? "MoveFix tick (look turned to the support, keys redirected to match)" : "pitch only at your yaw"),
                        this.aimYaw,
                        this.aimPitch
                     )
                     : "place() refused for " + fmt(p.pos()) + " (not replaceable / entity in the way)"
               );
            } else {
               this.dbg(
                  String.format(
                     "deferred: another module's aim took this tick's rotation slot (server look %.1f/%.1f, ours %.1f/%.1f)",
                     serverYaw,
                     serverPitch,
                     this.aimYaw,
                     this.aimPitch
                  )
               );
            }
         }
      }
   }

   private static String fmt(BlockPos pos) {
      return pos.getX() + " " + pos.getY() + " " + pos.getZ();
   }

   public boolean suppressesMovementInput() {
      return this.isActive() && this.stillTick;
   }

   private Vec3d projectedEye() {
      return this.mc.player.getEyePos().add(this.mc.player.getVelocity());
   }

   private Direction legalPlaceSide(BlockPos target, Vec3d eye, double margin) {
      Direction best = null;
      double bestDist = Double.MAX_VALUE;

      for (Direction dir : Direction.values()) {
         BlockPos n = target.offset(dir);
         BlockState state = this.mc.world.getBlockState(n);
         if (!state.isReplaceable() && !BlockUtils.isClickable(state.getBlock()) && state.getFluidState().isEmpty()) {
            Box nb = new Box(n);

            boolean visible = switch (dir.getOpposite()) {
               case NORTH -> eye.z <= nb.minZ - margin;
               case SOUTH -> eye.z >= nb.maxZ + margin;
               case WEST -> eye.x <= nb.minX - margin;
               case EAST -> eye.x >= nb.maxX + margin;
               case UP -> eye.y >= nb.maxY + margin;
               case DOWN -> eye.y <= nb.minY - margin;
               default -> throw new MatchException(null, null);
            };
            if (!visible) {
               this.hiddenFaceSeen = true;
            } else {
               double dist = dir == Direction.DOWN ? -1.0 : eye.squaredDistanceTo(Vec3d.ofCenter(n));
               if (dist < bestDist) {
                  bestDist = dist;
                  best = dir;
               }
            }
         }
      }

      return best;
   }

   private float[] aimWithYaw(Vec3d eye, float yaw, Box box) {
      double rad = Math.toRadians(yaw);
      double hx = -Math.sin(rad);
      double hz = Math.cos(rad);
      double t0 = 0.01;
      double t1 = Double.MAX_VALUE;
      double minX = box.minX + 0.05;
      double maxX = box.maxX - 0.05;
      double minZ = box.minZ + 0.05;
      double maxZ = box.maxZ - 0.05;
      if (Math.abs(hx) < 1.0E-6) {
         if (eye.x < minX || eye.x > maxX) {
            return null;
         }
      } else {
         double a = (minX - eye.x) / hx;
         double b = (maxX - eye.x) / hx;
         t0 = Math.max(t0, Math.min(a, b));
         t1 = Math.min(t1, Math.max(a, b));
      }

      if (Math.abs(hz) < 1.0E-6) {
         if (eye.z < minZ || eye.z > maxZ) {
            return null;
         }
      } else {
         double a = (minZ - eye.z) / hz;
         double b = (maxZ - eye.z) / hz;
         t0 = Math.max(t0, Math.min(a, b));
         t1 = Math.min(t1, Math.max(a, b));
      }

      if (t1 - t0 < 0.02) {
         return null;
      } else {
         double t = t0 + Math.min(0.25, (t1 - t0) / 2.0);
         double y = MathHelper.clamp(eye.y, box.minY + 0.05, box.maxY - 0.05);
         return this.finishAim(eye, new Vec3d(eye.x + hx * t, y, eye.z + hz * t), box, yaw);
      }
   }

   private float[] aimFree(Vec3d eye, Box box) {
      Vec3d p = new Vec3d(
         MathHelper.clamp(eye.x, box.minX + 0.05, box.maxX - 0.05),
         MathHelper.clamp(eye.y, box.minY + 0.05, box.maxY - 0.05),
         MathHelper.clamp(eye.z, box.minZ + 0.05, box.maxZ - 0.05)
      );
      return this.finishAim(eye, p, box, null);
   }

   private float[] finishAim(Vec3d eye, Vec3d point, Box box, Float fixedYaw) {
      double dx = point.x - eye.x;
      double dy = point.y - eye.y;
      double dz = point.z - eye.z;
      double horiz = Math.sqrt(dx * dx + dz * dz);
      if (Math.sqrt(horiz * horiz + dy * dy) > this.mc.player.getBlockInteractionRange() - 0.15) {
         return null;
      } else {
         float realYaw = this.mc.player.getYaw();
         float yaw = fixedYaw != null ? fixedYaw : realYaw + MathHelper.wrapDegrees((float)Math.toDegrees(Math.atan2(-dx, dz)) - realYaw);
         float pitch = MathHelper.clamp((float)Math.toDegrees(-Math.atan2(dy, horiz)), -90.0F, 90.0F);
         return !ReviveClient.ROTATION.raytraceCheck(eye, yaw, pitch, box) ? null : new float[]{yaw, pitch};
      }
   }

   private boolean sprintManagesInput() {
      Sprint sprint = Sprint.get();
      return sprint != null && sprint.isActive();
   }

   private static PlayerInput stillInput(PlayerInput real) {
      return new PlayerInput(false, false, false, false, real.jump(), real.sneak(), false);
   }

   private void syncServerInput() {
      if (this.mc.player != null && this.mc.getNetworkHandler() != null) {
         if (!this.sprintManagesInput() && !ElytraDurabilitySaver.get().ownsServerInput()) {
            PlayerInput real = this.mc.player.input.playerInput;
            if (this.stillTick) {
               PlayerInput still = stillInput(real);
               if (!still.equals(this.lastSentInput)) {
                  this.sendInput(still);
               }
            } else if (this.lastSentInput != null && !this.lastSentInput.equals(real)) {
               this.sendInput(real);
            }
         }
      }
   }

   private void sendInput(PlayerInput input) {
      this.bypassInputIntercept = true;
      this.mc.getNetworkHandler().sendPacket(new PlayerInputC2SPacket(input));
      this.bypassInputIntercept = false;
      this.lastSentInput = input;
   }

   @EventHandler(
      priority = 100
   )
   private void onPacketSend(PacketEvent.Send event) {
      if (!this.bypassInputIntercept && !event.isCancelled() && this.mc.player != null) {
         if (event.packet instanceof PlayerInputC2SPacket pkt) {
            if (!this.sprintManagesInput() && !ElytraDurabilitySaver.get().ownsServerInput()) {
               PlayerInput desired = this.stillTick ? stillInput(pkt.input()) : pkt.input();
               if (desired.equals(pkt.input())) {
                  this.lastSentInput = desired;
               } else {
                  event.setCancelled(true);
                  if (!desired.equals(this.lastSentInput)) {
                     this.sendInput(desired);
                  }
               }
            }
         }
      }
   }

   private void dbg(String msg) {
      if (this.debug.get()) {
         long now = System.currentTimeMillis();
         if (!msg.equals(this.lastDebug) || now - this.lastDebugMs >= 1000L) {
            this.lastDebug = msg;
            this.lastDebugMs = now;
            ChatUtils.infoPrefix("Scaffold", "%s", msg);
         }
      }
   }

   @EventHandler
   private void onClipAtLedge(ClipAtLedgeEvent event) {
      if (this.safeWalk.get() && this.mc.player != null && this.mc.world != null && !this.mc.player.isSneaking()) {
         if (this.findBlocks().found()) {
            event.setClip(true);
         }
      }
   }

   private Scaffold.Placement findTarget(int bridgeY) {
      this.hiddenFaceSeen = false;
      Vec3d eye = this.projectedEye();
      BlockPos under = new BlockPos(this.mc.player.getBlockX(), bridgeY - 1, this.mc.player.getBlockZ());
      Scaffold.Placement urgent = this.findPlacePos(under, eye);
      if (urgent != null) {
         return urgent;
      } else {
         Box box = this.mc.player.getBoundingBox();
         Vec3d velocity = this.mc.player.getVelocity();
         int minX = MathHelper.floor(Math.min(box.minX, box.minX + velocity.x));
         int maxX = MathHelper.floor(Math.max(box.maxX, box.maxX + velocity.x));
         int minZ = MathHelper.floor(Math.min(box.minZ, box.minZ + velocity.z));
         int maxZ = MathHelper.floor(Math.max(box.maxZ, box.maxZ + velocity.z));
         Scaffold.Placement best = null;
         double bestDist = Double.MAX_VALUE;

         for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
               Scaffold.Placement candidate = this.findPlacePos(new BlockPos(x, bridgeY - 1, z), eye);
               if (candidate != null) {
                  double dist = candidate.pos().getSquaredDistance(under);
                  if (dist < bestDist) {
                     bestDist = dist;
                     best = candidate;
                  }
               }
            }
         }

         return best;
      }
   }

   private Scaffold.Placement findPlacePos(BlockPos target, Vec3d eye) {
      if (!this.mc.world.getBlockState(target).isReplaceable()) {
         return null;
      } else if (!this.inReach(target)) {
         return null;
      } else {
         Direction direct = this.legalPlaceSide(target, eye, 0.03);
         if (direct != null) {
            return new Scaffold.Placement(target, direct);
         } else {
            int radius = this.searchRadius.get();
            Scaffold.Placement best = null;
            double bestDist = Double.MAX_VALUE;

            for (int x = -radius; x <= radius; x++) {
               for (int y = -radius; y <= 0; y++) {
                  for (int z = -radius; z <= radius; z++) {
                     BlockPos pos = target.add(x, y, z);
                     if (this.mc.world.getBlockState(pos).isReplaceable() && this.inReach(pos)) {
                        Direction side = this.legalPlaceSide(pos, eye, 0.03);
                        if (side != null) {
                           double dist = pos.getSquaredDistance(target);
                           if (dist < bestDist) {
                              bestDist = dist;
                              best = new Scaffold.Placement(pos, side);
                           }
                        }
                     }
                  }
               }
            }

            return best;
         }
      }
   }

   private boolean inReach(BlockPos pos) {
      double reach = this.mc.player.getBlockInteractionRange();
      return new Box(pos).squaredMagnitude(this.mc.player.getEyePos()) <= reach * reach;
   }

   private FindItemResult findBlocks() {
      if (this.autoSwitch.get()) {
         return InvUtils.findInHotbar(this::isUsableBlock);
      } else {
         ItemStack held = this.mc.player.getMainHandStack();
         if (this.isUsableBlock(held)) {
            return new FindItemResult(this.mc.player.getInventory().selectedSlot, held.getCount());
         } else {
            ItemStack offhand = this.mc.player.getOffHandStack();
            return this.isUsableBlock(offhand) ? new FindItemResult(45, offhand.getCount()) : new FindItemResult(-1, 0);
         }
      }
   }

   private boolean isUsableBlock(ItemStack stack) {
      if (stack.getItem() instanceof BlockItem blockItem) {
         Block var5 = blockItem.getBlock();
         List<Block> allowed = this.whitelist.get();
         return allowed != null && !allowed.isEmpty() ? allowed.contains(var5) : this.isSaneScaffoldBlock(var5);
      } else {
         return false;
      }
   }

   private static boolean isScaffoldCandidate(Block block) {
      return !BlockUtils.isClickable(block) && !(block instanceof FallingBlock);
   }

   private boolean isSaneScaffoldBlock(Block block) {
      if (!isScaffoldCandidate(block)) {
         return false;
      } else {
         return this.mc.world != null && this.mc.player != null ? block.getDefaultState().isSolidBlock(this.mc.world, this.mc.player.getBlockPos()) : true;
      }
   }

   private void restoreSlot() {
      if (this.returnSlot != -1) {
         if (this.mc.player != null) {
            InvUtils.swap(this.returnSlot, false);
         }

         this.returnSlot = -1;
      }
   }

   private void updateEagle() {
      if (!this.eagle.get() || !this.mc.player.isOnGround()) {
         this.releaseEagleSneak();
         this.eagleSneakTicks = 0;
      } else if (this.eagleSneakTicks > 0) {
         this.eagleSneakTicks--;
         if (this.eagleSneakTicks == 0) {
            this.releaseEagleSneak();
         }
      } else if (System.currentTimeMillis() - this.eagleLastSneakTime >= this.sneakDelay.get().intValue()) {
         if (!this.mc.player.isSneaking()) {
            if (this.isNearEdge()) {
               this.eagleSneakTicks = 2;
               this.eagleLastSneakTime = System.currentTimeMillis();
               this.eagleSneakForced = true;
               this.mc.options.sneakKey.setPressed(true);
            }
         }
      }
   }

   private boolean isNearEdge() {
      double fracX = this.mc.player.getX() - Math.floor(this.mc.player.getX());
      double fracZ = this.mc.player.getZ() - Math.floor(this.mc.player.getZ());
      double minDist = Math.min(Math.min(fracX, 1.0 - fracX), Math.min(fracZ, 1.0 - fracZ));
      return minDist <= this.edgeDistance.get();
   }

   private void releaseEagleSneak() {
      if (this.eagleSneakForced) {
         this.eagleSneakForced = false;
         if (this.mc.options != null) {
            this.mc.options.sneakKey.setPressed(false);
         }
      }
   }

   @Override
   public String getInfoString() {
      if (this.mc.player == null) {
         return null;
      } else {
         int count = 0;

         for (int i = 0; i < 9; i++) {
            ItemStack stack = this.mc.player.getInventory().getStack(i);
            if (this.isUsableBlock(stack)) {
               count += stack.getCount();
            }
         }

         return String.valueOf(count);
      }
   }

   private record Placement(BlockPos pos, Direction side) {
   }
}
