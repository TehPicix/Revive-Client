package revivedevelopment.reviveclient.systems.modules.combat;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.function.Predicate;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.consume.UseAction;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.sound.SoundEvent;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.events.packets.PacketEvent;
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
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.systems.config.AntiCheatConfig;
import revivedevelopment.reviveclient.systems.friends.Friends;
import revivedevelopment.reviveclient.systems.modules.Categories;
import revivedevelopment.reviveclient.systems.modules.Module;
import revivedevelopment.reviveclient.systems.modules.Modules;
import revivedevelopment.reviveclient.systems.modules.player.SilentMine;
import revivedevelopment.reviveclient.systems.modules.render.BreakIndicators;
import revivedevelopment.reviveclient.utils.entity.DamageUtil;
import revivedevelopment.reviveclient.utils.player.PlayerUtils;
import revivedevelopment.reviveclient.utils.render.color.Color;
import revivedevelopment.reviveclient.utils.render.color.SettingColor;

public class Surround extends Module implements PbRenderable {
   private static final long PROTECT_BREAK_COOLDOWN = 50L;
   private static final long REACTION_WINDOW = 1000L;
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgProtect = this.settings.createGroup("Protect");
   private final SettingGroup sgSelfTrap = this.settings.createGroup("Self Trap");
   private final SettingGroup sgExtend = this.settings.createGroup("Extend");
   private final SettingGroup sgEnemy = this.settings.createGroup("Enemy Reaction");
   private final SettingGroup sgRender = this.settings.createGroup("Render");
   private final Setting<Boolean> pauseWhileEating = this.sgGeneral
      .add(new BoolSetting.Builder().name("pause-while-eating").description("Stop placing while you are eating or drinking.").defaultValue(true).build());
   private final Setting<Boolean> avoidHelpingOpponents = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("avoid-helping-opponents")
            .description("Do not place blocks that would help surround/trap a nearby opponent.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> ignoreRebreak = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("ignore-rebreak")
            .description("Skip the block Silent Mine is currently mining so the two don't fight.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> protect = this.sgProtect
      .add(
         new BoolSetting.Builder()
            .name("protect")
            .description("Break crystals sitting on your surround positions when it is safe to do so.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> protectInstantPlacement = this.sgProtect
      .add(
         new BoolSetting.Builder()
            .name("instant-placement")
            .description("Instantly re-place a surround block the moment its crystal is broken.")
            .defaultValue(true)
            .visible(this.protect::get)
            .build()
      );
   private final Setting<Boolean> protectOverrideBlockCooldown = this.sgProtect
      .add(
         new BoolSetting.Builder()
            .name("override-block-cooldown")
            .description("Reset the per-block placement cooldown when re-placing, for a faster reaction.")
            .defaultValue(false)
            .visible(this.protect::get)
            .build()
      );
   private final Setting<Surround.BreakSwing> breakSwing = this.sgProtect
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("break-swing"))
                     .description("Swing your hand when breaking a protecting crystal."))
                  .defaultValue(Surround.BreakSwing.None))
               .visible(this.protect::get))
            .build()
      );
   private final Setting<Boolean> selfTrap = this.sgSelfTrap
      .add(
         new BoolSetting.Builder()
            .name("self-trap")
            .description("Seal yourself in (place blocks above/below) to survive incoming crystals.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Surround.TrapMode> selfTrapMode = this.sgSelfTrap
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("self-trap-mode"))
                     .description("Smart only traps when a crystal is detected; Always traps constantly."))
                  .defaultValue(Surround.TrapMode.Smart))
               .visible(this.selfTrap::get))
            .build()
      );
   private final Setting<Boolean> selfTrapHead = this.sgSelfTrap
      .add(
         new BoolSetting.Builder()
            .name("self-trap-head")
            .description("Also place a block two above your feet (on your head).")
            .defaultValue(true)
            .visible(this.selfTrap::get)
            .build()
      );
   private final Setting<Boolean> extend = this.sgExtend
      .add(
         new BoolSetting.Builder()
            .name("extend")
            .description("Extend the surround toward an incoming crystal to block its placement spot.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Surround.TrapMode> extendMode = this.sgExtend
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("extend-mode"))
                     .description("Smart only extends toward a detected crystal; Always extends constantly."))
                  .defaultValue(Surround.TrapMode.Smart))
               .visible(this.extend::get))
            .build()
      );
   private final Setting<Surround.CrawlMode> crawlExtendMode = this.sgExtend
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("crawl-extend-mode"))
                  .description("Extend while crawling / gliding (air placement) toward a detected crystal."))
               .defaultValue(Surround.CrawlMode.Smart))
            .build()
      );
   private final Setting<Surround.TrapMode> anchorExtendMode = this.sgExtend
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("anchor-extend-mode"))
                  .description("Place a block above surround positions when a respawn anchor is detected nearby."))
               .defaultValue(Surround.TrapMode.None))
            .build()
      );
   private final Setting<Boolean> enemyDoubleMineReact = this.sgEnemy
      .add(
         new BoolSetting.Builder()
            .name("enemy-double-mine-react")
            .description("Reinforce your surround when an opponent is mining one of your blocks. Requires the Break Indicators module.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> rocketProtect = this.sgEnemy
      .add(
         new BoolSetting.Builder()
            .name("rocket-protect")
            .description("Use a firework on a block an opponent is rebreaking to disrupt their mine.")
            .defaultValue(false)
            .visible(this.enemyDoubleMineReact::get)
            .build()
      );
   private final Setting<Boolean> render = this.sgRender
      .add(new BoolSetting.Builder().name("render").description("Render the blocks Surround places (and skips).").defaultValue(true).build());
   private final Setting<Double> fadeTime = this.sgRender
      .add(
         new DoubleSetting.Builder()
            .name("fade-time")
            .description("Seconds a placed/skipped block stays rendered before fading out.")
            .defaultValue(0.2)
            .min(0.0)
            .max(1.0)
            .sliderMax(1.0)
            .visible(this.render::get)
            .build()
      );
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
            .description("Side color of placed blocks.")
            .defaultValue(new SettingColor(85, 0, 255, 40))
            .visible(() -> this.render.get() && this.shapeMode.get().sides())
            .build()
      );
   private final Setting<SettingColor> lineColor = this.sgRender
      .add(
         new ColorSetting.Builder()
            .name("line-color")
            .description("Line color of placed blocks.")
            .defaultValue(new SettingColor(255, 255, 255, 60))
            .visible(() -> this.render.get() && this.shapeMode.get().lines())
            .build()
      );
   private final Setting<SettingColor> skippedSideColor = this.sgRender
      .add(
         new ColorSetting.Builder()
            .name("skipped-side-color")
            .description("Side color of blocks that were skipped (e.g. to avoid helping opponents).")
            .defaultValue(new SettingColor(255, 0, 0, 40))
            .visible(() -> this.render.get() && this.shapeMode.get().sides())
            .build()
      );
   private final Setting<SettingColor> skippedLineColor = this.sgRender
      .add(
         new ColorSetting.Builder()
            .name("skipped-line-color")
            .description("Line color of blocks that were skipped.")
            .defaultValue(new SettingColor(255, 0, 0, 120))
            .visible(() -> this.render.get() && this.shapeMode.get().lines())
            .build()
      );
   private final Setting<Boolean> debugProtectionShape = this.sgRender
      .add(
         new BoolSetting.Builder()
            .name("debug-protection-shape")
            .description("Render the full protection shape that protect scans.")
            .defaultValue(false)
            .build()
      );
   private final List<BlockPos> placeList = new ArrayList<>();
   private final Map<BlockPos, Long> placedBlocks = new HashMap<>();
   private final Map<BlockPos, Long> skippedBlocks = new HashMap<>();
   private long crystalAboveTs;
   private long crystalBelowTs;
   private long extendCrystalTs;
   private long crawlCrystalTs;
   private long anchorTs;
   private long lastProtectBreak;
   private BlockPos extendCrystalOffset;
   private BlockPos crawlCrystalOffset;
   private boolean enemyMineNonFeet;
   private BlockPos lastRocketPos;
   private long lastRocketTs;
   private static final int[][] GROUND_SHAPE = new int[][]{
      {0, 0, 0},
      {1, 0, 0},
      {2, 0, 0},
      {3, 0, 0},
      {-1, 0, 0},
      {-2, 0, 0},
      {-3, 0, 0},
      {0, 0, 1},
      {0, 0, 2},
      {0, 0, 3},
      {0, 0, -1},
      {0, 0, -2},
      {0, 0, -3},
      {1, 0, 1},
      {2, 0, 1},
      {1, 0, 2},
      {1, 0, -1},
      {2, 0, -1},
      {1, 0, -2},
      {-1, 0, 1},
      {-2, 0, 1},
      {-1, 0, 2},
      {-1, 0, -1},
      {-2, 0, -1},
      {-1, 0, -2},
      {0, 1, 0},
      {1, 1, 0},
      {-1, 1, 0},
      {2, 1, 0},
      {-2, 1, 0},
      {0, 1, 1},
      {0, 1, -1},
      {0, 1, 2},
      {0, 1, -2},
      {1, 1, 1},
      {1, 1, -1},
      {-1, 1, 1},
      {-1, 1, -1},
      {0, 2, 0},
      {0, 3, 0},
      {1, 2, 0},
      {-1, 2, 0},
      {0, 2, 1},
      {0, 2, -1},
      {0, -1, 0},
      {1, -1, 0},
      {-1, -1, 0},
      {2, -1, 0},
      {2, -1, 1},
      {2, -1, -1},
      {-2, -1, 0},
      {-2, -1, 1},
      {-2, -1, -1},
      {0, -1, 1},
      {0, -1, -1},
      {0, -1, 2},
      {1, -1, 2},
      {-1, -1, 2},
      {0, -1, -2},
      {1, -1, -2},
      {-1, -1, -2},
      {1, -1, 1},
      {1, -1, -1},
      {-1, -1, 1},
      {-1, -1, -1},
      {0, -2, 0}
   };
   private static final int[][] AIR_SHAPE = new int[][]{
      {0, 0, 0},
      {1, 0, 0},
      {2, 0, 0},
      {3, 0, 0},
      {-1, 0, 0},
      {-2, 0, 0},
      {-3, 0, 0},
      {0, 0, 1},
      {0, 0, 2},
      {0, 0, 3},
      {0, 0, -1},
      {0, 0, -2},
      {0, 0, -3},
      {1, 0, 1},
      {2, 0, 1},
      {1, 0, 2},
      {1, 0, -1},
      {2, 0, -1},
      {1, 0, -2},
      {-1, 0, 1},
      {-2, 0, 1},
      {-1, 0, 2},
      {-1, 0, -1},
      {-2, 0, -1},
      {-1, 0, -2},
      {0, -1, 0},
      {1, -1, 0},
      {-1, -1, 0},
      {0, -1, 1},
      {0, -1, -1},
      {0, -2, 0},
      {1, -1, 1},
      {1, -1, -1},
      {-1, -1, 1},
      {-1, -1, -1},
      {2, -1, 0},
      {2, -1, 1},
      {2, -1, -1},
      {-2, -1, 0},
      {-2, -1, 1},
      {-2, -1, -1},
      {0, 2, 0},
      {0, 1, 0},
      {1, 1, 0},
      {0, 1, 1},
      {-1, 1, 0},
      {0, 1, -1},
      {0, -1, 2},
      {1, -1, 2},
      {-1, -1, 2},
      {0, -1, -2},
      {1, -1, -2},
      {-1, -1, -2}
   };

   public Surround() {
      super(Categories.Combat, "surround", "Surrounds you in obsidian to prevent crystal damage.");
   }

   @Override
   public void onActivate() {
      this.reset();
   }

   @Override
   public void onDeactivate() {
      this.reset();
   }

   private void reset() {
      this.placeList.clear();
      this.placedBlocks.clear();
      this.skippedBlocks.clear();
      this.crystalAboveTs = 0L;
      this.crystalBelowTs = 0L;
      this.extendCrystalTs = 0L;
      this.crawlCrystalTs = 0L;
      this.anchorTs = 0L;
      this.lastProtectBreak = 0L;
      this.extendCrystalOffset = null;
      this.crawlCrystalOffset = null;
      this.enemyMineNonFeet = false;
      this.lastRocketPos = null;
      this.lastRocketTs = 0L;
   }

   private SilentMine silentMine() {
      return Modules.get().get(SilentMine.class);
   }

   @EventHandler(
      priority = 150
   )
   private void onTick(TickEvent.Pre event) {
      if (this.mc.player != null && this.mc.world != null) {
         SilentMine sm = this.silentMine();
         this.placeList.clear();
         this.enemyMineNonFeet = false;
         long now = System.currentTimeMillis();
         if (this.anchorExtendMode.get() == Surround.TrapMode.Smart) {
            this.detectAnchor(now);
         }

         List<BlockPos> feet = new ArrayList<>();
         List<BlockPos> extras = new ArrayList<>();
         List<BlockPos> extendFills = new ArrayList<>();
         if (!this.mc.player.isSwimming() && !this.mc.player.isGliding()) {
            this.collectGround(feet, extras, extendFills, sm, now);
         } else {
            this.collectAir(feet, extendFills, now);
         }

         this.reactToEnemyMining(extras, now);
         this.placeList.addAll(feet);
         this.placeList.addAll(extras);
         this.placeList.addAll(extendFills);
         if (!this.isEating()) {
            Set<BlockPos> protection = this.protectionShape();
            if (this.protect.get()) {
               this.doProtect(protection, sm, now);
            }

            List<BlockPos> toPlace = new ArrayList<>();
            Set<BlockPos> skipped = new HashSet<>();
            if (!this.avoidHelpingOpponents.get()) {
               toPlace.addAll(this.placeList);
            } else {
               Set<BlockPos> myCells = this.feetCells();
               boolean trapped = this.partiallyTrapped();

               for (BlockPos pos : this.placeList) {
                  if (!this.helpsOpponent(pos)) {
                     toPlace.add(pos);
                  } else if (trapped && !this.enemyMineNonFeet) {
                     skipped.add(pos);
                  } else if (this.isWithinMyProtection(pos, myCells)) {
                     toPlace.add(pos);
                  } else {
                     skipped.add(pos);
                  }
               }
            }

            for (BlockPos posx : skipped) {
               this.skippedBlocks.put(posx.toImmutable(), now);
            }

            this.placeAll(toPlace, sm, now);
            if (this.debugProtectionShape.get()) {
               for (BlockPos posx : protection) {
                  this.skippedBlocks.put(posx.toImmutable(), now);
               }
            }
         }
      }
   }

   private void collectGround(List<BlockPos> feet, List<BlockPos> extras, List<BlockPos> extendFills, SilentMine sm, long now) {
      Box box = this.mc.player.getBoundingBox();
      int y = this.mc.player.getBlockPos().getY();
      int minX = MathHelper.floor(box.minX + 0.01);
      int maxX = MathHelper.floor(box.maxX - 0.01);
      int minZ = MathHelper.floor(box.minZ + 0.01);
      int maxZ = MathHelper.floor(box.maxZ - 0.01);
      boolean single = minX == maxX && minZ == maxZ;
      boolean crystalAbove = false;
      boolean crystalBelow = false;
      boolean extendCrystal = false;
      if (this.selfTrap.get() || this.extend.get() && this.extendMode.get() == Surround.TrapMode.Smart) {
         if (this.hasCrystalIn(box.expand(3.5, 0.5, 3.5).offset(0.0, 1.0, 0.0))) {
            crystalAbove = true;
            this.crystalAboveTs = now;
         }

         if (this.hasCrystalIn(box.expand(1.5, 0.0, 1.5).offset(0.0, -1.0, 0.0))) {
            crystalBelow = true;
            this.crystalBelowTs = now;
         }
      }

      if (this.extend.get() && this.extendMode.get() == Surround.TrapMode.Smart && single) {
         EndCrystalEntity crystal = this.nearestCrystal(box.expand(2.0, 1.0, 2.0), c -> c.getBlockPos().getY() <= this.mc.player.getBlockPos().getY());
         if (crystal != null) {
            this.extendCrystalOffset = relative(crystal.getBlockPos(), this.mc.player.getBlockPos());
            this.extendCrystalTs = now;
            extendCrystal = true;
         }
      }

      for (int cx = minX; cx <= maxX; cx++) {
         for (int cz = minZ; cz <= maxZ; cz++) {
            BlockPos cell = new BlockPos(cx, y, cz);

            for (int dx = -1; dx <= 1; dx++) {
               for (int dz = -1; dz <= 1; dz++) {
                  if (Math.abs(dx) + Math.abs(dz) == 1) {
                     BlockPos neighbour = cell.add(dx, 0, dz).toImmutable();
                     if (this.isReplaceable(neighbour)) {
                        feet.add(neighbour);
                     }

                     if (this.selfTrap.get() && this.selfTrapMode.get() != Surround.TrapMode.None) {
                        this.addSelfTrap(extras, neighbour, crystalAbove, crystalBelow, now);
                     }

                     if (this.anchorExtendMode.get() != Surround.TrapMode.None) {
                        this.addAnchorExtend(extras, neighbour, now);
                     }
                  }
               }
            }

            BlockPos below = new BlockPos(cx, y - 1, cz).toImmutable();
            BlockState belowState = this.mc.world.getBlockState(below);
            boolean rebreak = this.ignoreRebreak.get() && this.isRebreak(sm, below);
            if (!rebreak && (belowState.isAir() || belowState.isReplaceable())) {
               feet.add(below);
            }
         }
      }

      if (this.extend.get() && single) {
         boolean doExtend = this.extendMode.get() == Surround.TrapMode.Always
            || this.extendMode.get() == Surround.TrapMode.Smart && extendCrystal && now - this.extendCrystalTs < 1000L;
         if (doExtend) {
            this.addExtendDirectional(extendFills, this.mc.player.getBlockPos(), this.extendCrystalOffset);
         }
      }

      if (this.selfTrap.get() && this.selfTrapHead.get() && !this.mc.player.isSwimming() && !this.mc.player.isCrawling()) {
         extras.add(this.mc.player.getBlockPos().up(2).toImmutable());
      }
   }

   private void collectAir(List<BlockPos> feet, List<BlockPos> extendFills, long now) {
      Box box = this.mc.player.getBoundingBox();
      int y = this.mc.player.getBlockPos().getY();
      int minX = MathHelper.floor(box.minX + 0.01);
      int maxX = MathHelper.floor(box.maxX - 0.01);
      int minZ = MathHelper.floor(box.minZ + 0.01);
      int maxZ = MathHelper.floor(box.maxZ - 0.01);
      boolean single = minX == maxX && minZ == maxZ;
      boolean crawlCrystal = false;
      if (this.crawlExtendMode.get() == Surround.CrawlMode.Smart && single) {
         EndCrystalEntity crystal = this.nearestCrystal(box.expand(2.0, 1.0, 2.0), c -> true);
         if (crystal != null) {
            this.crawlCrystalOffset = relative(crystal.getBlockPos(), this.mc.player.getBlockPos());
            this.crawlCrystalTs = now;
            crawlCrystal = true;
         }
      }

      int[][] offsets = new int[][]{{0, 1, 0}, {0, -1, 0}, {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}};

      for (int cx = minX; cx <= maxX; cx++) {
         for (int cz = minZ; cz <= maxZ; cz++) {
            BlockPos cell = new BlockPos(cx, y, cz);

            for (int[] o : offsets) {
               BlockPos pos = cell.add(o[0], o[1], o[2]).toImmutable();
               if (this.isReplaceable(pos)) {
                  feet.add(pos);
               }
            }
         }
      }

      if (single && this.crawlExtendMode.get() == Surround.CrawlMode.Smart && crawlCrystal && now - this.crawlCrystalTs < 1000L) {
         this.addExtendDirectional(extendFills, new BlockPos(minX, y, minZ), this.crawlCrystalOffset);
      }
   }

   private void addSelfTrap(List<BlockPos> list, BlockPos neighbour, boolean crystalAbove, boolean crystalBelow, long now) {
      boolean trapUp = this.selfTrapMode.get() == Surround.TrapMode.Always
         || this.selfTrapMode.get() == Surround.TrapMode.Smart && crystalAbove && now - this.crystalAboveTs < 1000L;
      if (trapUp) {
         BlockPos up = neighbour.up().toImmutable();
         if (this.isReplaceable(up)) {
            list.add(up);
         }
      }

      boolean trapDown = this.selfTrapMode.get() == Surround.TrapMode.Always
         || this.selfTrapMode.get() == Surround.TrapMode.Smart && crystalBelow && now - this.crystalBelowTs < 1000L;
      if (trapDown) {
         BlockPos down = neighbour.down().toImmutable();
         if (this.isReplaceable(down)) {
            list.add(down);
         }

         BlockPos belowFeet = this.mc.player.getBlockPos().down(2).toImmutable();
         if (this.isReplaceable(belowFeet)) {
            list.add(belowFeet);
         }
      }
   }

   private void addAnchorExtend(List<BlockPos> list, BlockPos neighbour, long now) {
      boolean active = this.anchorExtendMode.get() == Surround.TrapMode.Always
         || this.anchorExtendMode.get() == Surround.TrapMode.Smart && now - this.anchorTs < 1000L;
      if (active) {
         BlockPos up = neighbour.up().toImmutable();
         if (this.isReplaceable(up)) {
            list.add(up);
         }
      }
   }

   private void detectAnchor(long now) {
      Box box = this.mc.player.getBoundingBox().expand(4.0, 2.0, 4.0);
      BlockPos min = BlockPos.ofFloored(box.minX, box.minY, box.minZ);
      BlockPos max = BlockPos.ofFloored(box.maxX, box.maxY, box.maxZ);

      for (BlockPos pos : BlockPos.iterate(min, max)) {
         BlockState state = this.mc.world.getBlockState(pos);
         if (state.isOf(Blocks.RESPAWN_ANCHOR) && state.contains(Properties.CHARGES) && (Integer)state.get(Properties.CHARGES) > 0) {
            this.anchorTs = now;
            return;
         }
      }
   }

   private void addExtendDirectional(List<BlockPos> list, BlockPos base, BlockPos crystalOffset) {
      if (crystalOffset != null) {
         int sx = Integer.signum(crystalOffset.getX());
         int sz = Integer.signum(crystalOffset.getZ());
         boolean diagonal = sx != 0 && sz != 0;
         boolean straight = sx != 0 ^ sz != 0;
         if (diagonal) {
            this.addIfReplaceable(list, base.add(sx, 0, sz));
            this.addIfReplaceable(list, base.add(sx * 2, 0, 0));
            this.addIfReplaceable(list, base.add(0, 0, sz * 2));
         } else if (straight) {
            if (sx != 0) {
               this.addIfReplaceable(list, base.add(sx, 0, 1));
               this.addIfReplaceable(list, base.add(sx, 0, -1));
               this.addIfReplaceable(list, base.add(sx * 2, 0, 0));
            } else {
               this.addIfReplaceable(list, base.add(1, 0, sz));
               this.addIfReplaceable(list, base.add(-1, 0, sz));
               this.addIfReplaceable(list, base.add(0, 0, sz * 2));
            }
         }
      }
   }

   private static BlockPos relative(BlockPos pos, BlockPos origin) {
      return new BlockPos(pos.getX() - origin.getX(), pos.getY() - origin.getY(), pos.getZ() - origin.getZ());
   }

   private void addIfReplaceable(List<BlockPos> list, BlockPos pos) {
      BlockPos p = pos.toImmutable();
      if (this.isReplaceable(p)) {
         list.add(p);
      }
   }

   private void doProtect(Set<BlockPos> protection, SilentMine sm, long now) {
      for (BlockPos pos : protection) {
         Box cell = new Box(pos);
         Entity found = (Entity)this.mc.world.getOtherEntities(null, cell, e -> e instanceof EndCrystalEntity).stream().findFirst().orElse(null);
         if (found instanceof EndCrystalEntity crystal && now - this.lastProtectBreak >= 50L) {
            BlockPos crystalPos = crystal.getBlockPos();
            if (crystalPos != null && protection.contains(crystalPos)) {
               float selfDamage = DamageUtil.crystalDamage(this.mc.player, crystal.getEntityPos());
               if (!(selfDamage >= PlayerUtils.getTotalHealth())) {
                  ReviveClient.ROTATION.requestRotation(crystal.getEntityPos(), AntiCheatConfig.get().surroundPriority.get());
                  this.mc.player.networkHandler.sendPacket(PlayerInteractEntityC2SPacket.attack(crystal, this.mc.player.isSneaking()));
                  this.doBreakSwing();
                  crystal.discard();
                  this.lastProtectBreak = now;
                  if (this.protectOverrideBlockCooldown.get()) {
                     ReviveClient.BLOCK.forceResetPlaceCooldown(pos);
                  }

                  if (this.protectInstantPlacement.get()) {
                     boolean rebreak = this.ignoreRebreak.get() && this.isRebreak(sm, crystalPos);
                     if (!rebreak && !this.nearEnemy(crystalPos) && this.isReplaceable(crystalPos)) {
                        List<BlockPos> one = new ArrayList<>();
                        one.add(crystalPos.toImmutable());
                        this.placeAll(one, sm, now);
                     }
                     break;
                  }
               }
            }
         }
      }
   }

   private void placeAll(List<BlockPos> list, SilentMine sm, long now) {
      if (!list.isEmpty()) {
         List<BlockPos> candidates = new ArrayList<>();

         for (BlockPos pos : list) {
            if ((!this.ignoreRebreak.get() || !this.isRebreak(sm, pos)) && ReviveClient.BLOCK.checkPlacement(Items.OBSIDIAN, pos)) {
               candidates.add(pos);
            }
         }

         if (!candidates.isEmpty()) {
            if (ReviveClient.BLOCK.beginPlacement(candidates, Items.OBSIDIAN)) {
               try {
                  for (BlockPos posx : candidates) {
                     if (ReviveClient.BLOCK.placeBlock(Items.OBSIDIAN, posx)) {
                        this.placedBlocks.put(posx.toImmutable(), now);
                     }
                  }
               } finally {
                  ReviveClient.BLOCK.endPlacement();
               }
            }
         }
      }
   }

   private boolean isRebreak(SilentMine sm, BlockPos pos) {
      if (sm == null) {
         return false;
      } else {
         BlockPos rb = sm.getRebreakBlockPos();
         BlockPos db = sm.getDelayedDestroyBlockPos();
         return rb != null && pos.equals(rb) || db != null && pos.equals(db);
      }
   }

   private void doBreakSwing() {
      switch ((Surround.BreakSwing)this.breakSwing.get()) {
         case Client:
            this.mc.player.swingHand(Hand.MAIN_HAND);
            break;
         case Packet:
            this.mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
      }
   }

   private void reactToEnemyMining(List<BlockPos> list, long now) {
      if (this.enemyDoubleMineReact.get()) {
         BreakIndicators indicators = Modules.get().get(BreakIndicators.class);
         if (indicators != null && indicators.isActive()) {
            BlockPos myPos = this.mc.player.getBlockPos();
            Set<BlockPos> myCells = this.feetCells();
            boolean single = this.isSingleCell();

            for (BreakIndicators.TrackedBlock tracked : indicators.getTracked().values()) {
               if (tracked.player != null && this.isFeetNeighbour(tracked.pos, myCells)) {
                  if (tracked.isRebreak) {
                     this.tryRocketProtect(tracked.pos, now);
                  }

                  if (!single) {
                     this.enemyMineNonFeet = true;
                  } else {
                     int dx = tracked.pos.getX() - myPos.getX();
                     int dy = tracked.pos.getY() - myPos.getY();
                     int dz = tracked.pos.getZ() - myPos.getZ();
                     if (dy == 0 && Math.abs(dx) + Math.abs(dz) == 1) {
                        this.enemyMineNonFeet = true;
                        int ax;
                        int az;
                        int bx;
                        int bz;
                        if (dx != 0) {
                           ax = dx;
                           az = 1;
                           bx = dx;
                           bz = -1;
                        } else {
                           ax = 1;
                           az = dz;
                           bx = -1;
                           bz = dz;
                        }

                        BlockPos[] targets = new BlockPos[]{
                           tracked.pos.up(), tracked.pos.down(), myPos.add(ax, 0, az), myPos.add(bx, 0, bz), myPos.add(dx * 2, 0, dz * 2)
                        };

                        for (BlockPos target : targets) {
                           BlockPos p = target.toImmutable();
                           if (!p.equals(myPos) && this.isReplaceable(p)) {
                              list.add(p);
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private boolean isFeetNeighbour(BlockPos pos, Set<BlockPos> cells) {
      for (BlockPos cell : cells) {
         if (pos.getY() == cell.getY() && (pos.equals(cell.north()) || pos.equals(cell.south()) || pos.equals(cell.east()) || pos.equals(cell.west()))) {
            return true;
         }
      }

      return false;
   }

   private boolean isSingleCell() {
      Box box = this.mc.player.getBoundingBox();
      int minX = MathHelper.floor(box.minX + 0.01);
      int maxX = MathHelper.floor(box.maxX - 0.01);
      int minZ = MathHelper.floor(box.minZ + 0.01);
      int maxZ = MathHelper.floor(box.maxZ - 0.01);
      return minX == maxX && minZ == maxZ;
   }

   private void tryRocketProtect(BlockPos minedPos, long now) {
      if (this.rocketProtect.get() && minedPos != null) {
         BlockState state = this.mc.world.getBlockState(minedPos);
         if (!state.isAir() && !state.isReplaceable()) {
            if (this.lastRocketPos == null || !this.lastRocketPos.equals(minedPos) || now - this.lastRocketTs >= 200L) {
               Box box = new Box(minedPos).expand(0.2, 1.1, 0.2);
               if (this.mc.world.getOtherEntities(null, box, e -> e instanceof FireworkRocketEntity).isEmpty()) {
                  boolean used;
                  if (this.mc.player.getOffHandStack().isOf(Items.FIREWORK_ROCKET)) {
                     used = this.useFireworkOn(minedPos, Hand.OFF_HAND);
                  } else if (this.mc.player.getMainHandStack().isOf(Items.FIREWORK_ROCKET)) {
                     used = this.useFireworkOn(minedPos, Hand.MAIN_HAND);
                  } else if (ReviveClient.SWAP.beginSwap(Items.FIREWORK_ROCKET, true)) {
                     used = this.useFireworkOn(minedPos, Hand.MAIN_HAND);
                     ReviveClient.SWAP.endSwap(true);
                  } else {
                     used = false;
                  }

                  if (used) {
                     this.lastRocketPos = minedPos.toImmutable();
                     this.lastRocketTs = now;
                  }
               }
            }
         }
      }
   }

   private boolean useFireworkOn(BlockPos pos, Hand hand) {
      Vec3d hit = Vec3d.ofCenter(pos).add(0.0, 0.5, 0.0);
      BlockHitResult result = new BlockHitResult(hit, Direction.UP, pos, false);
      int sequence = ((IClientWorld)this.mc.world).revive$getAndIncrementSequence();
      this.mc.getNetworkHandler().sendPacket(new PlayerInteractBlockC2SPacket(hand, result, sequence));
      this.mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(hand));
      return true;
   }

   private Set<BlockPos> feetCells() {
      Set<BlockPos> cells = new HashSet<>();
      Box box = this.mc.player.getBoundingBox();
      int y = this.mc.player.getBlockPos().getY();
      int minX = MathHelper.floor(box.minX + 0.01);
      int maxX = MathHelper.floor(box.maxX - 0.01);
      int minZ = MathHelper.floor(box.minZ + 0.01);
      int maxZ = MathHelper.floor(box.maxZ - 0.01);

      for (int cx = minX; cx <= maxX; cx++) {
         for (int cz = minZ; cz <= maxZ; cz++) {
            cells.add(new BlockPos(cx, y, cz).toImmutable());
         }
      }

      return cells;
   }

   private boolean isWithinMyProtection(BlockPos pos, Set<BlockPos> cells) {
      for (BlockPos cell : cells) {
         if (pos.equals(cell.up())
            || pos.equals(cell.down())
            || pos.equals(cell.north())
            || pos.equals(cell.south())
            || pos.equals(cell.east())
            || pos.equals(cell.west())
            || pos.equals(cell)) {
            return true;
         }
      }

      return false;
   }

   private boolean helpsOpponent(BlockPos pos) {
      if (!this.avoidHelpingOpponents.get()) {
         return false;
      } else {
         for (PlayerEntity player : this.mc.world.getPlayers()) {
            if (player != this.mc.player && !Friends.get().isFriend(player) && !this.isBlockingEntity(player)) {
               BlockPos feet = player.getBlockPos();
               double dist = Math.sqrt(
                  Math.pow(feet.getX() - this.mc.player.getBlockPos().getX(), 2.0) + Math.pow(feet.getZ() - this.mc.player.getBlockPos().getZ(), 2.0)
               );
               if (!(dist > 5.0)) {
                  Box box = player.getBoundingBox();
                  int minX = MathHelper.floor(box.minX + 0.01);
                  int maxX = MathHelper.floor(box.maxX - 0.01);
                  int minZ = MathHelper.floor(box.minZ + 0.01);
                  int maxZ = MathHelper.floor(box.maxZ - 0.01);
                  int w = maxX - minX + 1;
                  int d = maxZ - minZ + 1;
                  if (w == 1 && d == 1) {
                     if (pos.equals(feet.north())
                        || pos.equals(feet.south())
                        || pos.equals(feet.east())
                        || pos.equals(feet.west())
                        || pos.equals(feet.down())
                        || pos.equals(feet)) {
                        return true;
                     }
                  } else {
                     Set<BlockPos> ring = new HashSet<>();

                     for (int x = minX; x < minX + w; x++) {
                        ring.add(new BlockPos(x, feet.getY(), minZ - 1).toImmutable());
                        ring.add(new BlockPos(x, feet.getY(), minZ + d).toImmutable());
                     }

                     for (int z = minZ; z < minZ + d; z++) {
                        ring.add(new BlockPos(minX - 1, feet.getY(), z).toImmutable());
                        ring.add(new BlockPos(minX + w, feet.getY(), z).toImmutable());
                     }

                     if (ring.contains(pos)) {
                        return true;
                     }
                  }
               }
            }
         }

         return false;
      }
   }

   private boolean isBlockingEntity(Entity entity) {
      return entity != null && !this.mc.world.getBlockState(entity.getBlockPos()).isReplaceable();
   }

   private boolean nearEnemy(BlockPos pos) {
      double x = pos.getX() + 0.5;
      double y = pos.getY() + 0.5;
      double z = pos.getZ() + 0.5;

      for (PlayerEntity player : this.mc.world.getPlayers()) {
         if (player != this.mc.player && !Friends.get().isFriend(player) && player.getBoundingBox().expand(2.0).contains(x, y, z)) {
            return true;
         }
      }

      return false;
   }

   private Set<BlockPos> protectionShape() {
      Set<BlockPos> shape = new HashSet<>();
      BlockPos base = this.mc.player.getBlockPos();
      if (!this.mc.player.isSwimming() && !this.mc.player.isGliding()) {
         for (int[] o : GROUND_SHAPE) {
            shape.add(base.add(o[0], o[1], o[2]).toImmutable());
         }
      } else {
         for (int[] o : AIR_SHAPE) {
            shape.add(base.add(o[0], o[1], o[2]).toImmutable());
         }
      }

      return shape;
   }

   private boolean partiallyTrapped() {
      int count = 0;
      Box box = this.mc.player.getBoundingBox();
      int y = this.mc.player.getBlockPos().getY();
      int minX = MathHelper.floor(box.minX + 0.01);
      int maxX = MathHelper.floor(box.maxX - 0.01);
      int minZ = MathHelper.floor(box.minZ + 0.01);
      int maxZ = MathHelper.floor(box.maxZ - 0.01);

      for (int cx = minX; cx <= maxX; cx++) {
         for (int cz = minZ; cz <= maxZ; cz++) {
            if (!this.mc.world.getBlockState(new BlockPos(cx, y, cz)).isReplaceable()) {
               count++;
            }
         }
      }

      return count >= 1;
   }

   private boolean isEating() {
      if (!this.pauseWhileEating.get()) {
         return false;
      } else if (this.mc.player.isUsingItem() && this.isConsumable(this.mc.player.getActiveItem())) {
         return true;
      } else {
         return !this.mc.options.attackKey.isPressed()
            ? false
            : this.isConsumable(this.mc.player.getMainHandStack()) || this.isConsumable(this.mc.player.getOffHandStack());
      }
   }

   private boolean isConsumable(ItemStack stack) {
      if (stack != null && !stack.isEmpty()) {
         UseAction action = stack.getUseAction();
         return action == UseAction.EAT || action == UseAction.DRINK;
      } else {
         return false;
      }
   }

   private boolean isReplaceable(BlockPos pos) {
      return this.mc.world.getBlockState(pos).isReplaceable();
   }

   private boolean hasCrystalIn(Box box) {
      return !this.mc.world.getOtherEntities(null, box, e -> e instanceof EndCrystalEntity).isEmpty();
   }

   private EndCrystalEntity nearestCrystal(Box box, Predicate<EndCrystalEntity> predicate) {
      EndCrystalEntity best = null;
      double bestDist = Double.MAX_VALUE;

      for (Entity entity : this.mc.world.getOtherEntities(null, box, e -> e instanceof EndCrystalEntity)) {
         EndCrystalEntity crystal = (EndCrystalEntity)entity;
         if (predicate.test(crystal)) {
            double dist = crystal.squaredDistanceTo(this.mc.player);
            if (dist < bestDist) {
               best = crystal;
               bestDist = dist;
            }
         }
      }

      return best;
   }

   @EventHandler
   private void onPacketReceive(PacketEvent.Receive event) {
      if (this.anchorExtendMode.get() == Surround.TrapMode.Smart && this.mc.player != null) {
         if (event.packet instanceof PlaySoundS2CPacket packet && packet.getSound() != null) {
            String id = ((SoundEvent)packet.getSound().value()).id().getPath();
            if (id.contains("respawn_anchor") && !id.contains("ambient")) {
               this.anchorTs = System.currentTimeMillis();
            }
         }
      }
   }

   @EventHandler
   private void onRender(Render3DEvent event) {
      if (this.render.get()) {
         long now = System.currentTimeMillis();
         long fadeMs = Math.max(1L, (long)(this.fadeTime.get() * 1000.0));
         this.renderMap(event, this.placedBlocks, this.sideColor.get(), this.lineColor.get(), now, fadeMs);
         this.renderMap(event, this.skippedBlocks, this.skippedSideColor.get(), this.skippedLineColor.get(), now, fadeMs);
      }
   }

   private void renderMap(Render3DEvent event, Map<BlockPos, Long> map, Color side, Color line, long now, long fadeMs) {
      Iterator<Entry<BlockPos, Long>> it = map.entrySet().iterator();

      while (it.hasNext()) {
         Entry<BlockPos, Long> entry = it.next();
         long age = now - entry.getValue();
         if (age > fadeMs) {
            it.remove();
         } else {
            double t = 1.0 - MathHelper.clamp((double)age / fadeMs, 0.0, 1.0);
            event.renderer.box(entry.getKey(), this.scaleAlpha(side, t), this.scaleAlpha(line, t), this.shapeMode.get(), 0);
         }
      }
   }

   @Override
   public JsonObject writeRemoteRender() {
      if (this.render.get() && (!this.placedBlocks.isEmpty() || !this.skippedBlocks.isEmpty())) {
         long now = System.currentTimeMillis();
         long fadeMs = Math.max(1L, (long)(this.fadeTime.get() * 1000.0));
         JsonObject state = new JsonObject();
         state.add("placed", PbFx.posTimes(this.placedBlocks, now, fadeMs, 48));
         state.add("skipped", PbFx.posTimes(this.skippedBlocks, now, fadeMs, 48));
         return state;
      } else {
         return null;
      }
   }

   @Override
   public void readRemoteRender(JsonObject state, PbFx.Clock clock) {
      PbFx.readPosTimes(state.get("placed"), clock, this.placedBlocks);
      PbFx.readPosTimes(state.get("skipped"), clock, this.skippedBlocks);
   }

   @Override
   public void drawRemote3D(Render3DEvent event) {
      this.onRender(event);
   }

   @Override
   public void clearRemote() {
      this.placedBlocks.clear();
      this.skippedBlocks.clear();
   }

   private Color scaleAlpha(Color color, double t) {
      int a = MathHelper.clamp((int)Math.round(color.a * t), 0, 255);
      return new Color(color.r, color.g, color.b, a);
   }

   public static enum BreakSwing {
      None,
      Client,
      Packet;
   }

   public static enum CrawlMode {
      None,
      Smart;
   }

   public static enum TrapMode {
      None,
      Smart,
      Always;
   }
}
