package revivedevelopment.reviveclient.systems.modules.combat;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.consume.UseAction;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket.Mode;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.PositionAndOnGround;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.events.entity.player.SendMovementPacketsEvent;
import revivedevelopment.reviveclient.events.render.Render3DEvent;
import revivedevelopment.reviveclient.events.world.TickEvent;
import revivedevelopment.reviveclient.pingbypass.PbFakes;
import revivedevelopment.reviveclient.pingbypass.PbFx;
import revivedevelopment.reviveclient.pingbypass.PbProtocol;
import revivedevelopment.reviveclient.pingbypass.PbRenderable;
import revivedevelopment.reviveclient.renderer.ShapeMode;
import revivedevelopment.reviveclient.settings.BoolSetting;
import revivedevelopment.reviveclient.settings.ColorSetting;
import revivedevelopment.reviveclient.settings.DoubleSetting;
import revivedevelopment.reviveclient.settings.EntityTypeListSetting;
import revivedevelopment.reviveclient.settings.EnumSetting;
import revivedevelopment.reviveclient.settings.IntSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.systems.friends.Friends;
import revivedevelopment.reviveclient.systems.modules.Categories;
import revivedevelopment.reviveclient.systems.modules.Module;
import revivedevelopment.reviveclient.systems.modules.Modules;
import revivedevelopment.reviveclient.systems.modules.movement.Sprint;
import revivedevelopment.reviveclient.utils.entity.EntityUtils;
import revivedevelopment.reviveclient.utils.entity.fakeplayer.FakePlayerEntity;
import revivedevelopment.reviveclient.utils.player.ChatUtils;
import revivedevelopment.reviveclient.utils.player.FindItemResult;
import revivedevelopment.reviveclient.utils.player.PlayerUtils;
import revivedevelopment.reviveclient.utils.render.color.Color;
import revivedevelopment.reviveclient.utils.render.color.SettingColor;
import revivedevelopment.reviveclient.utils.world.TickRate;

public class SwordAura extends Module implements PbRenderable {
   private static final String ROTATION_ID = "sword-aura";
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgTargets = this.settings.createGroup("Targets");
   private final SettingGroup sgCrits = this.settings.createGroup("Crits");
   private final SettingGroup sgRender = this.settings.createGroup("Render");
   private final Setting<Double> range = this.sgGeneral
      .add(new DoubleSetting.Builder().name("range").description("Attack reach distance.").defaultValue(3.0).min(1.0).sliderMax(6.0).build());
   private final Setting<Boolean> rotate = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("rotate")
            .description(
               "Aim at the target before attacking (server-side only; your camera stays put). Standing still this uses a silent look packet; while moving it rides the tick's own move packet so your movement is never interrupted."
            )
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> swordPull = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("sword-pull")
            .description(
               "Sends a 180° rotation right after the hit to pull the target toward you. Only applies while standing still - while moving, a flipped yaw on a real move packet reverses what your movement keys mean to the server and earns a setback."
            )
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> tpsSync = this.sgGeneral
      .add(new BoolSetting.Builder().name("tps-sync").description("Adjusts attack speed to match the server's TPS.").defaultValue(true).build());
   private final Setting<Boolean> pauseOnCrystal = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("pause-on-crystal")
            .description("Don't attack while holding an end crystal in either hand (so AutoCrystal can place uninterrupted).")
            .defaultValue(false)
            .build()
      );
   private final Setting<Boolean> pauseOnEat = this.sgGeneral
      .add(new BoolSetting.Builder().name("pause-on-eat").description("Don't attack while eating or drinking.").defaultValue(true).build());
   private final Setting<Boolean> pauseInAir = this.sgGeneral
      .add(new BoolSetting.Builder().name("pause-in-air").description("Don't attack while rising during a jump.").defaultValue(false).build());
   private final Setting<Boolean> pauseOnInventory = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("pause-on-inventory")
            .description("Don't attack while an inventory / container screen is open.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> debug = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("debug")
            .description("Log targeting decisions to chat (focus changes, attack path, sprint aim conflicts, held attacks).")
            .defaultValue(false)
            .build()
      );
   private final Setting<Boolean> targetPlayers = this.sgTargets
      .add(new BoolSetting.Builder().name("players").description("Attack other players (friends are always ignored).").defaultValue(true).build());
   private final Setting<Boolean> targetMobs = this.sgTargets
      .add(new BoolSetting.Builder().name("mobs").description("Attack mobs.").defaultValue(false).build());
   private final Setting<Set<EntityType<?>>> mobWhitelist = this.sgTargets
      .add(
         new EntityTypeListSetting.Builder()
            .name("mob-whitelist")
            .description("Only attack these mob types. Empty = attack all mobs (subject to hostile-only).")
            .onlyAttackable()
            .visible(this.targetMobs::get)
            .build()
      );
   private final Setting<Boolean> hostileOnly = this.sgTargets
      .add(
         new BoolSetting.Builder()
            .name("hostile-only")
            .description("Only attack hostile mobs. Ignored while the whitelist has entries — the whitelist already says exactly what to hit.")
            .defaultValue(true)
            .visible(() -> this.targetMobs.get() && this.mobWhitelist.get().isEmpty())
            .build()
      );
   private final Setting<Boolean> throughWalls = this.sgTargets
      .add(
         new BoolSetting.Builder()
            .name("through-walls")
            .description(
               "Also target entities with no line of sight to you. Off, entities behind blocks are skipped - anti-cheats throw those hits out anyway, and a wall target used to hog the focus while a reachable enemy hit you freely."
            )
            .defaultValue(false)
            .build()
      );
   private final Setting<Boolean> ignoreNamed = this.sgTargets
      .add(
         new BoolSetting.Builder()
            .name("ignore-named")
            .description("Skip entities with a custom nametag.")
            .defaultValue(true)
            .visible(this.targetMobs::get)
            .build()
      );
   private final Setting<SwordAura.TargetPriority> priority = this.sgTargets
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("priority"))
                  .description("Which target to focus when several are in range. The focused target keeps getting hit until it dies or leaves range."))
               .defaultValue(SwordAura.TargetPriority.Closest))
            .build()
      );
   private final Setting<SwordAura.CritMode> critMode = this.sgCrits
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("crit-mode"))
                  .description("Packet critical hits. OldWall only crits while phased in a block; OldAlways always."))
               .defaultValue(SwordAura.CritMode.None))
            .build()
      );
   private final Setting<Boolean> awaitJumpCrit = this.sgCrits
      .add(
         new BoolSetting.Builder()
            .name("await-jump-crit")
            .description("Wait until you are falling after a jump to perform a vanilla critical hit.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> critsPauseOnMove = this.sgCrits
      .add(
         new BoolSetting.Builder()
            .name("crits-pause-on-move")
            .description("Only send crit packets when standing still.")
            .defaultValue(true)
            .visible(() -> this.critMode.get() != SwordAura.CritMode.None)
            .build()
      );
   private final Setting<Boolean> critsOnlyOnSword = this.sgCrits
      .add(
         new BoolSetting.Builder()
            .name("crits-only-on-sword")
            .description("Only send crit packets when you were already holding the sword (not on a swap).")
            .defaultValue(true)
            .visible(() -> this.critMode.get() != SwordAura.CritMode.None)
            .build()
      );
   private final Setting<Boolean> render = this.sgRender
      .add(new BoolSetting.Builder().name("render").description("Render the target as the hit charges, fading away after the hit.").defaultValue(false).build());
   private final Setting<SwordAura.RenderMode> renderMode = this.sgRender
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("render-mode"))
                     .description("Box draws a fading box; Shader uses the same entity-outline shader as ESP."))
                  .defaultValue(SwordAura.RenderMode.Box))
               .visible(this.render::get))
            .build()
      );
   private final Setting<ShapeMode> shapeMode = this.sgRender
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("shape-mode"))
                     .description("How the box is rendered."))
                  .defaultValue(ShapeMode.Both))
               .visible(() -> this.render.get() && this.renderMode.get() == SwordAura.RenderMode.Box))
            .build()
      );
   private final Setting<SettingColor> sideColor = this.sgRender
      .add(
         new ColorSetting.Builder()
            .name("side-color")
            .description("The side color.")
            .defaultValue(new SettingColor(160, 0, 225, 35))
            .visible(() -> this.render.get() && this.renderMode.get() == SwordAura.RenderMode.Box && this.shapeMode.get().sides())
            .build()
      );
   private final Setting<SettingColor> lineColor = this.sgRender
      .add(
         new ColorSetting.Builder()
            .name("line-color")
            .description("The line color.")
            .defaultValue(new SettingColor(255, 255, 255, 50))
            .visible(() -> this.render.get() && this.renderMode.get() == SwordAura.RenderMode.Box && this.shapeMode.get().lines())
            .build()
      );
   public final Setting<SettingColor> shaderColor = this.sgRender
      .add(
         new ColorSetting.Builder()
            .name("shader-color")
            .description("The color of the shader outline.")
            .defaultValue(new SettingColor(160, 0, 225, 255))
            .visible(() -> this.render.get() && this.renderMode.get() == SwordAura.RenderMode.Shader)
            .build()
      );
   public final Setting<Integer> shaderOutlineWidth = this.sgRender
      .add(
         new IntSetting.Builder()
            .name("outline-width")
            .description("The width of the shader outline.")
            .defaultValue(2)
            .range(1, 10)
            .sliderRange(1, 5)
            .visible(() -> this.render.get() && this.renderMode.get() == SwordAura.RenderMode.Shader)
            .build()
      );
   public final Setting<Double> shaderFillOpacity = this.sgRender
      .add(
         new DoubleSetting.Builder()
            .name("shader-fill-opacity")
            .description("The opacity of the shader fill.")
            .defaultValue(0.3)
            .range(0.0, 1.0)
            .sliderMax(1.0)
            .visible(() -> this.render.get() && this.renderMode.get() == SwordAura.RenderMode.Shader)
            .build()
      );
   public final Setting<ShapeMode> shaderShapeMode = this.sgRender
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("shader-shape-mode"))
                     .description("How the shader shape is rendered."))
                  .defaultValue(ShapeMode.Both))
               .visible(() -> this.render.get() && this.renderMode.get() == SwordAura.RenderMode.Shader))
            .build()
      );
   public final Setting<Double> shaderGlowMultiplier = this.sgRender
      .add(
         new DoubleSetting.Builder()
            .name("glow-multiplier")
            .description("Multiplier for the shader glow effect.")
            .defaultValue(3.5)
            .min(0.0)
            .sliderMax(10.0)
            .decimalPlaces(3)
            .visible(() -> this.render.get() && this.renderMode.get() == SwordAura.RenderMode.Shader)
            .build()
      );
   private final Setting<Double> fadeTime = this.sgRender
      .add(
         new DoubleSetting.Builder()
            .name("fade-time")
            .description("How long the render fades after the hit (seconds).")
            .min(0.0)
            .sliderMax(2.0)
            .defaultValue(0.8)
            .visible(this.render::get)
            .build()
      );
   private static final double SILENT_AIM_PRIORITY = 1000.0;
   private long lastAttackTime = 0L;
   private LivingEntity currentTarget = null;
   private Entity lastAttackedEntity = null;
   private Entity pendingTarget;
   private FindItemResult pendingSword;
   private float pendingYaw;
   private float pendingPitch;
   private boolean pendingAimOnPacket;
   private final Color shaderColorBuffer = new Color();
   private String lastHoldDebug;

   public SwordAura() {
      super(Categories.Combat, "sword-aura", "Automatically attacks entities with your sword.");
   }

   @Override
   public void onDeactivate() {
      this.lastAttackedEntity = null;
      this.currentTarget = null;
      this.pendingTarget = null;
      this.pendingSword = null;
      this.pendingAimOnPacket = false;
   }

   @EventHandler(
      priority = 50
   )
   public void onTick(TickEvent.Pre event) {
      this.pendingTarget = null;
      this.pendingSword = null;
      this.pendingAimOnPacket = false;
      if (this.mc.player != null && this.mc.world != null && !this.mc.player.isDead() && !this.mc.player.isSpectator()) {
         if (!this.pauseOnInventory.get() || !(this.mc.currentScreen instanceof HandledScreen)) {
            if (!this.pauseOnEat.get() || !this.eating()) {
               if (!this.pauseOnCrystal.get()
                  || !this.mc.player.getMainHandStack().isOf(Items.END_CRYSTAL) && !this.mc.player.getOffHandStack().isOf(Items.END_CRYSTAL)) {
                  Offhand offhand = Modules.get().get(Offhand.class);
                  if (offhand == null || !offhand.isActive() || !offhand.isGapping()) {
                     FindItemResult result = ReviveClient.SWAP.getSlot(Items.NETHERITE_SWORD);
                     if (!result.found()) {
                        result = ReviveClient.SWAP.getSlot(Items.DIAMOND_SWORD);
                     }

                     if (result.found()) {
                        Entity target = this.selectTarget();
                        if (target != null) {
                           if (this.delayCheck(result.slot())) {
                              boolean isFalling = !this.mc.player.isOnGround()
                                 && this.mc.player.getVelocity().y < -0.1
                                 && !this.mc.player.isTouchingWater()
                                 && !this.mc.player.isInLava()
                                 && !this.mc.player.isSneaking()
                                 && !this.mc.player.isClimbing();
                              boolean isMoving = this.isMoving();
                              boolean isStandingOnGround = this.mc.player.isOnGround()
                                 && !isMoving
                                 && !this.mc.player.isTouchingWater()
                                 && !this.mc.player.isInLava();
                              boolean airborneOnLand = !this.mc.player.isOnGround()
                                 && !this.mc.player.isTouchingWater()
                                 && !this.mc.player.isInLava()
                                 && !this.mc.player.isClimbing()
                                 && !this.mc.player.hasVehicle();
                              if (!this.pauseInAir.get() || !airborneOnLand) {
                                 if (!this.awaitJumpCrit.get() || !airborneOnLand || isFalling || this.mc.player.isSneaking()) {
                                    boolean sendCritPackets = isStandingOnGround && this.critMode.get() != SwordAura.CritMode.None;
                                    Vec3d point = this.getClosestPointOnBox(target.getBoundingBox(), this.mc.player.getEyePos());
                                    float[] aim = ReviveClient.ROTATION.getRotation(this.mc.player.getEyePos(), point);
                                    boolean standingStill = this.mc.player.isOnGround()
                                       && !this.isMoving()
                                       && this.mc.player.getVelocity().horizontalLengthSquared() < 9.0E-6;
                                    if (!standingStill && !this.mc.player.hasVehicle()) {
                                       if (this.rotate.get()) {
                                          Sprint sprint = Sprint.get();
                                          if (sprint != null && sprint.isMotionSpoofActive()) {
                                             this.debugHold("standing omni-sprint down to carry the aim (~2 ticks)");
                                             sprint.stopSprinting(2);
                                             return;
                                          }

                                          if (this.sprintAimConflicts(aim[0]) && !this.cameraOnTarget(target)) {
                                             LivingEntity fallback = this.selectDeliverableTarget(target);
                                             if (fallback == null) {
                                                this.debugHold("holding attack: no target hittable while sprinting (aim can't ride the sprint packet)");
                                                if (sprint != null) {
                                                   sprint.stopSprinting(2);
                                                }

                                                return;
                                             }

                                             this.debug(
                                                "focus %s unhittable while sprinting — handing focus to %s",
                                                target.getName().getString(),
                                                fallback.getName().getString()
                                             );
                                             this.currentTarget = fallback;
                                             target = fallback;
                                             aim = ReviveClient.ROTATION
                                                .getRotation(
                                                   this.mc.player.getEyePos(), this.getClosestPointOnBox(fallback.getBoundingBox(), this.mc.player.getEyePos())
                                                );
                                          }

                                          if (!this.sprintAimConflicts(aim[0])) {
                                             ReviveClient.ROTATION.requestMotionRotation(aim[0], aim[1], 1000.0);
                                             this.pendingAimOnPacket = true;
                                          } else {
                                             this.debug("aim conflicts with sprint but camera is on %s — attacking unaimed", target.getName().getString());
                                          }
                                       }

                                       this.pendingTarget = target;
                                       this.pendingSword = result;
                                       this.pendingYaw = aim[0];
                                       this.pendingPitch = aim[1];
                                    } else {
                                       if (this.rotate.get() && !ReviveClient.ROTATION.canSendSilentRotation("sword-aura", 1000.0)) {
                                          this.debugHold("holding attack: this tick's silent-aim slots went to Surround / AutoCrystal - retry next tick");
                                          return;
                                       }

                                       boolean isHolding = result.isMainHand();
                                       if (isHolding) {
                                          this.attack(target, false, sendCritPackets, aim[0], aim[1], false);
                                       } else if (ReviveClient.SWAP.beginSwap(result, true)) {
                                          try {
                                             this.attack(target, true, sendCritPackets, aim[0], aim[1], false);
                                          } finally {
                                             ReviveClient.SWAP.endSwap(true);
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
            }
         }
      }
   }

   @EventHandler
   private void onSendMovementPost(SendMovementPacketsEvent.Post event) {
      Entity target = this.pendingTarget;
      FindItemResult sword = this.pendingSword;
      this.pendingTarget = null;
      this.pendingSword = null;
      boolean aimWasOnPacket = this.pendingAimOnPacket;
      this.pendingAimOnPacket = false;
      if (target != null && sword != null) {
         if (this.mc.player != null && this.mc.getNetworkHandler() != null) {
            if (!target.isAlive()) {
               this.debug("staged attack dropped (%s died mid-tick)", target.getName().getString());
            } else if (!aimWasOnPacket
               || !(Math.abs(MathHelper.wrapDegrees(ReviveClient.ROTATION.getServerYaw() - this.pendingYaw)) > 1.0F)
                  && !(Math.abs(ReviveClient.ROTATION.getServerPitch() - this.pendingPitch) > 1.0F)) {
               if (!this.pauseOnEat.get() || !this.eating()) {
                  if (!this.pauseOnInventory.get() || !(this.mc.currentScreen instanceof HandledScreen)) {
                     if (!this.pauseOnCrystal.get()
                        || !this.mc.player.getMainHandStack().isOf(Items.END_CRYSTAL) && !this.mc.player.getOffHandStack().isOf(Items.END_CRYSTAL)) {
                        Offhand offhand = Modules.get().get(Offhand.class);
                        if (offhand == null || !offhand.isActive() || !offhand.isGapping()) {
                           if (sword.isMainHand()) {
                              this.attack(target, false, false, this.pendingYaw, this.pendingPitch, true);
                           } else if (ReviveClient.SWAP.beginSwap(sword, true)) {
                              try {
                                 this.attack(target, true, false, this.pendingYaw, this.pendingPitch, true);
                              } finally {
                                 ReviveClient.SWAP.endSwap(true);
                              }
                           }
                        }
                     }
                  }
               }
            } else {
               this.debug("staged attack dropped (aim lost the move packet's rotation slot)");
            }
         }
      }
   }

   private boolean eating() {
      if (!this.mc.player.isUsingItem()) {
         return false;
      } else if (this.mc.player.getActiveHand() == Hand.MAIN_HAND) {
         return true;
      } else {
         UseAction action = this.mc.player.getActiveItem().getUseAction();
         return action == UseAction.EAT || action == UseAction.DRINK;
      }
   }

   private void attack(Entity target, boolean didSwap, boolean sendCritPackets, float aimYaw, float aimPitch, boolean aimOnMovePacket) {
      if (this.mc.player != null && this.mc.getNetworkHandler() != null) {
         boolean pulling = this.swordPull.get() && !sendCritPackets && !aimOnMovePacket;
         boolean wasSprinting = this.mc.player.isSprinting() && !this.mc.player.isGliding();
         if (!aimOnMovePacket && this.rotate.get() && !this.requestSilentAim(aimYaw, aimPitch)) {
            this.debugHold("holding attack: silent aim lost this tick's send to a higher-priority aim - retry next tick");
         } else {
            if (wasSprinting) {
               this.mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(this.mc.player, Mode.STOP_SPRINTING));
            }

            try {
               if (sendCritPackets) {
                  this.sendCrits(didSwap);
               }

               this.mc.getNetworkHandler().sendPacket(PlayerInteractEntityC2SPacket.attack(target, this.mc.player.isSneaking()));
               if (pulling) {
                  float spoofYaw = (aimYaw + 180.0F) % 360.0F;
                  this.requestSilentAim(spoofYaw, aimPitch);
               }
            } finally {
               if (wasSprinting && this.mc.player != null && !this.mc.player.isGliding()) {
                  this.mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(this.mc.player, Mode.START_SPRINTING));
               }
            }

            this.mc.player.swingHand(Hand.MAIN_HAND);
            this.lastAttackedEntity = target;
            this.lastAttackTime = System.currentTimeMillis();
            this.lastHoldDebug = null;
            if (this.debug.get()) {
               double dist = this.mc.player.getEyePos().distanceTo(this.getClosestPointOnBox(target.getBoundingBox(), this.mc.player.getEyePos()));
               String path = aimOnMovePacket ? "move-packet aim" : (this.rotate.get() ? "silent aim" : "no aim");
               this.debug("hit %s (%.2fm, %s%s%s)", target.getName().getString(), dist, path, didSwap ? ", swapped" : "", sendCritPackets ? ", crits" : "");
            }
         }
      }
   }

   private boolean requestSilentAim(float yaw, float pitch) {
      return ReviveClient.ROTATION.requestSilentRotation("sword-aura", 1000.0, yaw, pitch);
   }

   private boolean delayCheck(int slot) {
      if (this.mc.player == null) {
         return false;
      } else {
         PlayerInventory inventory = this.mc.player.getInventory();
         ItemStack itemStack = slot == 45 ? this.mc.player.getOffHandStack() : inventory.getStack(slot);
         EntityAttributeInstance original = this.mc.player.getAttributeInstance(EntityAttributes.ATTACK_SPEED);
         EntityAttributeInstance copy = new EntityAttributeInstance(EntityAttributes.ATTACK_SPEED, o -> {});
         copy.setBaseValue(original.getBaseValue());

         for (EntityAttributeModifier modifier : original.getModifiers()) {
            copy.addTemporaryModifier(modifier);
         }

         copy.removeModifier(Item.BASE_ATTACK_SPEED_MODIFIER_ID);
         AttributeModifiersComponent attributeModifiers = (AttributeModifiersComponent)itemStack.get(DataComponentTypes.ATTRIBUTE_MODIFIERS);
         if (attributeModifiers != null) {
            attributeModifiers.applyModifiers(EquipmentSlot.MAINHAND, (entry, modifier) -> {
               if (entry == EntityAttributes.ATTACK_SPEED) {
                  copy.updateModifier(modifier);
               }
            });
         }

         double attackSpeed = copy.getValue();
         if (attackSpeed <= 0.0) {
            return true;
         } else {
            double attackCooldownTicks = 1.0 / attackSpeed * 20.0;
            if (this.tpsSync.get()) {
               double tps = TickRate.INSTANCE.getTickRate() * 0.95;
               if (tps < 19.5) {
                  attackCooldownTicks /= tps / 20.0;
               }
            }

            long currentTime = System.currentTimeMillis();
            return (currentTime - this.lastAttackTime) / 50.0 > attackCooldownTicks;
         }
      }
   }

   private void sendCrits(boolean didSwap) {
      if (this.mc.player != null && this.mc.getNetworkHandler() != null) {
         if (!this.critsPauseOnMove.get() || !this.isMoving()) {
            if (!this.critsOnlyOnSword.get() || !didSwap) {
               if (this.critMode.get() != SwordAura.CritMode.OldWall || PlayerUtils.isPlayerPhased()) {
                  Vec3d pos = this.mc.player.getEntityPos();
                  boolean col = this.mc.player.horizontalCollision;
                  this.mc.getNetworkHandler().sendPacket(new PositionAndOnGround(pos.x, pos.y, pos.z, true, col));
                  this.mc.getNetworkHandler().sendPacket(new PositionAndOnGround(pos.x, pos.y + 0.0625, pos.z, false, col));
                  this.mc.getNetworkHandler().sendPacket(new PositionAndOnGround(pos.x, pos.y + 0.045, pos.z, false, col));
               }
            }
         }
      }
   }

   @EventHandler
   private void onRender3D(Render3DEvent event) {
      if (this.render.get() && this.renderMode.get() == SwordAura.RenderMode.Box) {
         if (this.lastAttackedEntity != null && this.mc.world != null && this.mc.player != null) {
            double secondsSinceAttack = (System.currentTimeMillis() - this.lastAttackTime) / 1000.0;
            if (!(secondsSinceAttack > this.fadeTime.get())) {
               double alpha = 1.0 - secondsSinceAttack / this.fadeTime.get();
               double x = MathHelper.lerp(event.tickDelta, this.lastAttackedEntity.lastRenderX, this.lastAttackedEntity.getX())
                  - this.lastAttackedEntity.getX();
               double y = MathHelper.lerp(event.tickDelta, this.lastAttackedEntity.lastRenderY, this.lastAttackedEntity.getY())
                  - this.lastAttackedEntity.getY();
               double z = MathHelper.lerp(event.tickDelta, this.lastAttackedEntity.lastRenderZ, this.lastAttackedEntity.getZ())
                  - this.lastAttackedEntity.getZ();
               Box box = this.lastAttackedEntity.getBoundingBox();
               event.renderer
                  .box(
                     x + box.minX,
                     y + box.minY,
                     z + box.minZ,
                     x + box.maxX,
                     y + box.maxY,
                     z + box.maxZ,
                     this.sideColor.get().copy().a((int)(this.sideColor.get().a * alpha)),
                     this.lineColor.get().copy().a((int)(this.lineColor.get().a * alpha)),
                     this.shapeMode.get(),
                     0
                  );
            }
         }
      }
   }

   @Override
   public JsonObject writeRemoteRender() {
      if (this.render.get() && this.lastAttackedEntity != null) {
         if (System.currentTimeMillis() - this.lastAttackTime > this.fadeTime.get() * 1000.0) {
            return null;
         } else {
            JsonObject state = new JsonObject();
            if (this.lastAttackedEntity instanceof FakePlayerEntity fake) {
               state.addProperty("u", fake.getUuidAsString());
            } else {
               state.addProperty("e", this.lastAttackedEntity.getId());
            }

            state.addProperty("t", this.lastAttackTime);
            return state;
         }
      } else {
         return null;
      }
   }

   @Override
   public void readRemoteRender(JsonObject state, PbFx.Clock clock) {
      int id = (int)PbFx.num(state, "e", -1.0);
      if (state.has("u")) {
         this.lastAttackedEntity = PbFakes.find(PbProtocol.str(state, "u", ""));
      } else {
         this.lastAttackedEntity = this.mc.world != null && id >= 0 ? this.mc.world.getEntityById(id) : null;
      }

      this.lastAttackTime = PbFx.time(state, "t", clock, 0L);
   }

   @Override
   public void drawRemote3D(Render3DEvent event) {
      this.onRender3D(event);
   }

   @Override
   public void clearRemote() {
      this.lastAttackedEntity = null;
   }

   public boolean isShaderActive() {
      return this.isActive() && this.render.get() && this.renderMode.get() == SwordAura.RenderMode.Shader;
   }

   public Color getShaderColor(Entity entity) {
      if (this.isShaderActive() && entity != null) {
         double alpha = this.fadeAlpha(entity);
         if (alpha <= 0.01) {
            return null;
         } else {
            SettingColor base = this.shaderColor.get();
            return this.shaderColorBuffer.set(base.r, base.g, base.b, (int)(base.a * alpha));
         }
      } else {
         return null;
      }
   }

   private double fadeAlpha(Entity entity) {
      if (entity != this.lastAttackedEntity) {
         return 0.0;
      } else {
         double secondsSinceAttack = (System.currentTimeMillis() - this.lastAttackTime) / 1000.0;
         return !(this.fadeTime.get() <= 0.0) && !(secondsSinceAttack > this.fadeTime.get()) ? 1.0 - secondsSinceAttack / this.fadeTime.get() : 0.0;
      }
   }

   private void debug(String format, Object... args) {
      if (this.debug.get()) {
         ChatUtils.infoPrefix("SwordAura", format, args);
      }
   }

   private void debugHold(String line) {
      if (this.debug.get() && !line.equals(this.lastHoldDebug)) {
         this.lastHoldDebug = line;
         ChatUtils.infoPrefix("SwordAura", "%s", line);
      }
   }

   private Entity selectTarget() {
      List<LivingEntity> candidates = this.getEntityTargets();
      if (candidates.isEmpty()) {
         if (this.currentTarget != null) {
            this.debug("focus lost (no candidates in range)");
         }

         this.currentTarget = null;
         return null;
      } else if (this.currentTarget != null && candidates.contains(this.currentTarget)) {
         return this.currentTarget;
      } else {
         LivingEntity previous = this.currentTarget;
         this.currentTarget = this.bestByPriority(candidates);
         if (this.currentTarget != null && this.currentTarget != previous) {
            this.debug("focus -> %s (%s of %d in range)", this.currentTarget.getName().getString(), this.priority.get(), candidates.size());
         }

         return this.currentTarget;
      }
   }

   private LivingEntity bestByPriority(List<LivingEntity> candidates) {
      Vec3d eye = this.mc.player.getEyePos();
      Comparator<LivingEntity> byDistance = Comparator.comparingDouble(e -> eye.squaredDistanceTo(this.getClosestPointOnBox(e.getBoundingBox(), eye)));

      return candidates.stream().min(switch ((SwordAura.TargetPriority)this.priority.get()) {
         case Closest -> byDistance;
         case LowestHealth -> Comparator.comparingDouble(LivingEntity::getHealth).thenComparing(byDistance);
      }).orElse(null);
   }

   private LivingEntity selectDeliverableTarget(Entity exclude) {
      List<LivingEntity> candidates = this.getEntityTargets();
      Vec3d eye = this.mc.player.getEyePos();
      candidates.removeIf(e -> {
         if (e == exclude) {
            return true;
         } else {
            float[] a = ReviveClient.ROTATION.getRotation(eye, this.getClosestPointOnBox(e.getBoundingBox(), eye));
            return this.sprintAimConflicts(a[0]) && !this.cameraOnTarget(e);
         }
      });
      return this.bestByPriority(candidates);
   }

   private boolean sprintAimConflicts(float aimYaw) {
      if (!this.mc.player.isSprinting()) {
         return false;
      } else {
         Vec3d vel = this.mc.player.getVelocity();
         if (vel.horizontalLengthSquared() <= 0.01) {
            return false;
         } else {
            float travelYaw = (float)Math.toDegrees(Math.atan2(vel.z, vel.x)) - 90.0F;
            return Math.abs(MathHelper.wrapDegrees(aimYaw - travelYaw)) > 35.0F;
         }
      }
   }

   private boolean cameraOnTarget(Entity target) {
      return ReviveClient.ROTATION.raytraceCheck(this.mc.player.getEyePos(), this.mc.player.getYaw(), this.mc.player.getPitch(), target.getBoundingBox());
   }

   private List<LivingEntity> getEntityTargets() {
      List<LivingEntity> list = new ArrayList<>();
      if (this.mc.player != null && this.mc.world != null) {
         double r = this.range.get();
         Vec3d eye = this.mc.player.getEyePos();

         for (Entity entity : this.mc.world.getEntities()) {
            if (entity != this.mc.player
               && entity instanceof LivingEntity living
               && entity.isAlive()
               && !entity.isSpectator()
               && EntityUtils.isAttackable(entity.getType())
               && !(eye.distanceTo(this.getClosestPointOnBox(entity.getBoundingBox(), eye)) > r)
               && (this.throughWalls.get() || PlayerUtils.canSeeEntity(entity))
               && !(
                  entity instanceof PlayerEntity player
                     ? !this.targetPlayers.get() || player == this.mc.player || Friends.get().isFriend(player)
                     : !this.targetMobs.get()
                        || (
                           !this.mobWhitelist.get().isEmpty()
                              ? !this.mobWhitelist.get().contains(entity.getType())
                              : this.hostileOnly.get() && !(entity instanceof HostileEntity)
                        )
                        || this.ignoreNamed.get() && entity.hasCustomName()
               )) {
               list.add(living);
            }
         }

         return list;
      } else {
         return list;
      }
   }

   private Vec3d getClosestPointOnBox(Box box, Vec3d point) {
      double x = Math.max(box.minX, Math.min(point.x, box.maxX));
      double y = Math.max(box.minY, Math.min(point.y, box.maxY));
      double z = Math.max(box.minZ, Math.min(point.z, box.maxZ));
      return new Vec3d(x, y, z);
   }

   private boolean isMoving() {
      return this.mc.player != null && this.mc.player.input != null
         ? this.mc.player.input.playerInput.forward()
            || this.mc.player.input.playerInput.backward()
            || this.mc.player.input.playerInput.left()
            || this.mc.player.input.playerInput.right()
         : false;
   }

   public static enum CritMode {
      None,
      OldWall,
      OldAlways;
   }

   public static enum RenderMode {
      Box,
      Shader;
   }

   public static enum TargetPriority {
      Closest,
      LowestHealth;
   }
}
