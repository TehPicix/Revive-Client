package revivedevelopment.reviveclient.systems.modules.combat;

import com.google.gson.JsonObject;
import net.minecraft.util.math.BlockPos;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.events.render.Render2DEvent;
import revivedevelopment.reviveclient.events.render.Render3DEvent;
import revivedevelopment.reviveclient.pingbypass.PbFx;
import revivedevelopment.reviveclient.pingbypass.PbRenderable;
import revivedevelopment.reviveclient.renderer.ShapeMode;
import revivedevelopment.reviveclient.settings.BoolSetting;
import revivedevelopment.reviveclient.settings.ColorSetting;
import revivedevelopment.reviveclient.settings.DoubleSetting;
import revivedevelopment.reviveclient.settings.EnumSetting;
import revivedevelopment.reviveclient.settings.IntSetting;
import revivedevelopment.reviveclient.settings.KeybindSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.systems.modules.Categories;
import revivedevelopment.reviveclient.systems.modules.Module;
import revivedevelopment.reviveclient.systems.modules.combat.autocrystal.AutoCrystalFeature;
import revivedevelopment.reviveclient.utils.misc.Keybind;
import revivedevelopment.reviveclient.utils.render.color.SettingColor;

public class AutoCrystal extends Module implements PbRenderable {
   private static AutoCrystal INSTANCE;
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgPlace = this.settings.createGroup("Place");
   private final SettingGroup sgFacePlace = this.settings.createGroup("Face Place");
   private final SettingGroup sgBreak = this.settings.createGroup("Break");
   private final SettingGroup sgRotate = this.settings.createGroup("Rotate");
   private final SettingGroup sgSwing = this.settings.createGroup("Swing");
   private final SettingGroup sgRange = this.settings.createGroup("Range");
   private final SettingGroup sgTargeting = this.settings.createGroup("Targeting");
   private final SettingGroup sgDamage = this.settings.createGroup("Damage");
   private final SettingGroup sgSafety = this.settings.createGroup("Safety");
   private final SettingGroup sgCrawl = this.settings.createGroup("Crawl / Anti-Surround");
   private final SettingGroup sgAdvanced = this.settings.createGroup("Advanced");
   private final SettingGroup sgMisc = this.settings.createGroup("Misc");
   private final SettingGroup sgRender = this.settings.createGroup("Render");
   public final Setting<Boolean> placeCrystals = this.sgGeneral
      .add(new BoolSetting.Builder().name("place").description("Places crystals.").defaultValue(true).build());
   public final Setting<Boolean> pauseEatPlace = this.sgGeneral
      .add(new BoolSetting.Builder().name("pause-eat-place").description("Pauses placing while eating / using an item.").defaultValue(true).build());
   public final Setting<Boolean> breakCrystals = this.sgGeneral
      .add(new BoolSetting.Builder().name("break").description("Breaks crystals.").defaultValue(true).build());
   public final Setting<Boolean> pauseEatBreak = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("pause-eat-break")
            .description("Pauses breaking while eating / using an item (Grim flags attacking while using an item).")
            .defaultValue(true)
            .build()
      );
   public final Setting<Boolean> ignoreNakeds = this.sgGeneral
      .add(new BoolSetting.Builder().name("ignore-nakeds").description("Ignore players wearing no armor.").defaultValue(false).build());
   public final Setting<Boolean> setPlayerDead = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("set-player-dead-instantly")
            .description("Tries to not blow up loot by instantly killing the player client-side in the packet they die.")
            .defaultValue(true)
            .build()
      );
   public final Setting<Boolean> predictBlock = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("predict-block")
            .description(
               "Pre-places a crystal on top of an obsidian/bedrock block another player is mining (tracked by BreakIndicators) so it lands as the block breaks (timing from ping-compensation)."
            )
            .defaultValue(false)
            .build()
      );
   public final Setting<AutoCrystal.PingCompensation> pingCompensation = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("ping-compensation"))
                     .description(
                        "When predict-block / cev-head place: a mined block qualifies once its predicted progress at the moment our place reaches the server is at the server's break point (1.0 for a vanilla miner; with BreakIndicators active its full-completion / rebreak-completion, ~0.7 for a packet miner's rebreak). The lead is about one round trip: we see their progress ~ping/2 late and our place lands ~ping/2 after we send it. Auto = your tab-list ping, Manual = manual-ping, Off = no lead (progress must already be at the break point)."
                     ))
                  .defaultValue(AutoCrystal.PingCompensation.Auto))
               .visible(() -> this.predictBlock.get() || this.cevHead.get()))
            .build()
      );
   public final Setting<Integer> manualPing = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("manual-ping")
            .description("Round-trip ping (ms) used by ping-compensation Manual, and by Auto while the tab list has no ping for you.")
            .defaultValue(100)
            .min(0)
            .max(1000)
            .sliderRange(0, 500)
            .visible(() -> (this.predictBlock.get() || this.cevHead.get()) && this.pingCompensation.get() == AutoCrystal.PingCompensation.Manual)
            .build()
      );
   public final Setting<Boolean> cevHead = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("cev-head")
            .description(
               "Pre-places a crystal on the obsidian above a nearby (crawling) target's head as it breaks (BreakIndicators, timed by ping-compensation, or your SilentMine's next STOP), priced as if the block were already gone. AutoMine's Crawl meta only mines a crawling target's ceiling while this is on (otherwise the target is kept crawling)."
            )
            .defaultValue(false)
            .build()
      );
   public final Setting<Boolean> laser = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("Laser")
            .description(
               "It BEAMS people: breaks any crystal in range while an enemy is targeted, even below min-break (self-damage safety still applies). Damage-dealing crystals are still broken first."
            )
            .defaultValue(true)
            .build()
      );
   public final Setting<Double> placeSpeedLimit = this.sgPlace
      .add(
         new DoubleSetting.Builder()
            .name("place-speed-limit")
            .description("Maximum number of crystals to place every second (0 = unlimited).")
            .defaultValue(30.0)
            .min(0.0)
            .sliderRange(0.0, 40.0)
            .build()
      );
   public final Setting<Double> minPlace = this.sgPlace
      .add(
         new DoubleSetting.Builder().name("min-place").description("Minimum enemy damage to place.").defaultValue(7.0).min(0.0).sliderRange(0.0, 20.0).build()
      );
   public final Setting<Double> maxPlace = this.sgPlace
      .add(new DoubleSetting.Builder().name("max-place").description("Max self damage to place.").defaultValue(20.0).min(0.0).sliderRange(0.0, 20.0).build());
   public final Setting<Double> maxSelfDamage = this.maxPlace;
   public final Setting<Boolean> antiSurround = this.sgPlace
      .add(
         new BoolSetting.Builder()
            .name("anti-surround")
            .description(
               "Crystals into the block SilentMine rebreaks and blocks AutoMine's anti-surround re-places. Enemy damage prices SilentMine's primary as air from the tick before its STOP (blocks still standing shield, as on the server). A crystal goes on top of the primary just before its STOP and is hit only after it, and spots on top of a block being broken are preferred, unless that block shields AutoMine's crawled / phased target."
            )
            .defaultValue(true)
            .build()
      );
   public final Setting<Double> placeDelay = this.sgPlace
      .add(
         new DoubleSetting.Builder()
            .name("place-delay")
            .description("The number of seconds to wait to retry placing a crystal at a position (cleared when the crystal spawns).")
            .defaultValue(0.05)
            .min(0.0)
            .sliderMax(0.6)
            .build()
      );
   public final Setting<Boolean> ignoreItems = this.sgPlace
      .add(
         new BoolSetting.Builder()
            .name("ignore-items")
            .description(
               "Places on freshly dropped items (at most 5 ticks old) right behind our own crystal hit, whose explosion clears them before the place is handled. At SilentMine's blocks and AutoMine's anti-surround spot an item is our own break's drop (the server refuses a crystal over any entity): placed over only when our hit went out after that break, at a crystal close enough with a clear line to the spot."
            )
            .defaultValue(true)
            .build()
      );
   public final Setting<Boolean> ignoreTrapdoors = this.sgPlace
      .add(
         new BoolSetting.Builder()
            .name("ignore-trapdoors")
            .description(
               "Treats a (non-iron) trapdoor in the crystal spot as free space. Vanilla/Paper still reject a crystal into a non-air block, so this only pays off when the trapdoor is about to be removed."
            )
            .defaultValue(false)
            .build()
      );
   public final Setting<Boolean> shorelineMode = this.sgPlace
      .add(
         new BoolSetting.Builder()
            .name("shoreline-mode")
            .description("Places in the corners of the target's surround next to blocks AutoMine is mining (needs Laser).")
            .defaultValue(false)
            .build()
      );
   public final Setting<Boolean> facePlace = this.sgFacePlace
      .add(
         new BoolSetting.Builder()
            .name("face-place")
            .description("Face places (ignores min-place / min-break) when conditions are met.")
            .defaultValue(true)
            .build()
      );
   public final Setting<Boolean> facePlaceMissingArmor = this.sgFacePlace
      .add(
         new BoolSetting.Builder()
            .name("face-place-missing-armor")
            .description("Face places on missing armor.")
            .defaultValue(true)
            .visible(() -> this.facePlace.get())
            .build()
      );
   public final Setting<Integer> facePlaceHealth = this.sgFacePlace
      .add(
         new IntSetting.Builder()
            .name("face-place-health")
            .description("Face places when target health is below this value.")
            .defaultValue(8)
            .min(1)
            .max(36)
            .sliderRange(1, 36)
            .visible(() -> this.facePlace.get())
            .build()
      );
   public final Setting<Integer> facePlaceArmor = this.sgFacePlace
      .add(
         new IntSetting.Builder()
            .name("face-place-armor")
            .description("Face places when target armor durability percentage is below this value.")
            .defaultValue(20)
            .min(1)
            .max(100)
            .sliderRange(1, 100)
            .visible(() -> this.facePlace.get())
            .build()
      );
   public final Setting<Keybind> forceFacePlace = this.sgFacePlace
      .add(new KeybindSetting.Builder().name("force-face-place").description("Keybind to force face place.").defaultValue(Keybind.none()).build());
   public final Setting<Double> breakSpeedLimit = this.sgBreak
      .add(
         new DoubleSetting.Builder()
            .name("break-speed-limit")
            .description("Maximum number of crystals to break every second (0 = unlimited).")
            .defaultValue(30.0)
            .min(0.0)
            .sliderRange(0.0, 40.0)
            .build()
      );
   public final Setting<Boolean> packetBreak = this.sgBreak
      .add(
         new BoolSetting.Builder()
            .name("packet-break")
            .description("Breaks a crystal the moment it spawns (when the server already holds an aim on it), instead of waiting for the next tick.")
            .defaultValue(true)
            .build()
      );
   public final Setting<Boolean> packetPlace = this.sgBreak
      .add(
         new BoolSetting.Builder()
            .name("packet-place")
            .description(
               "Right behind a packet-break, places the next crystal on the same spot (when the server already holds an aim on its base) instead of at the next tick. The server takes the hit first, so the spot is free: one crystal per spawn, at your ping."
            )
            .defaultValue(true)
            .visible(this.packetBreak::get)
            .build()
      );
   public final Setting<Double> minBreak = this.sgBreak
      .add(
         new DoubleSetting.Builder().name("min-break").description("Minimum enemy damage to break.").defaultValue(3.0).min(0.0).sliderRange(0.0, 20.0).build()
      );
   public final Setting<Double> maxBreak = this.sgBreak
      .add(new DoubleSetting.Builder().name("max-break").description("Max self damage to break.").defaultValue(20.0).min(0.0).sliderRange(0.0, 20.0).build());
   public final Setting<Double> breakDelay = this.sgBreak
      .add(
         new DoubleSetting.Builder()
            .name("break-delay")
            .description(
               "The number of seconds to wait to retry breaking a crystal. Also stops attack spam on a crystal whose destroy packet is still in flight."
            )
            .defaultValue(0.05)
            .min(0.0)
            .sliderMax(0.6)
            .build()
      );
   public final Setting<Boolean> rotatePlace = this.sgRotate
      .add(
         new BoolSetting.Builder()
            .name("rotate-place")
            .description("Rotates server-side towards the crystals when placed (needed for Grim).")
            .defaultValue(true)
            .build()
      );
   public final Setting<Boolean> rotateBreak = this.sgRotate
      .add(new BoolSetting.Builder().name("rotate-break").description("Rotates server-side towards the crystals when broken.").defaultValue(true).build());
   public final Setting<AutoCrystal.RotationTiming> rotationTiming = this.sgRotate
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("timing"))
                  .description(
                     "Pre = act only when this tick's move packet carries the aim (places go out just before it, once its final rotation is known; breaks right after it). Post = act at once, before the move packet, without waiting for the aim. Pre is required for GrimAC to accept the action."
                  ))
               .defaultValue(AutoCrystal.RotationTiming.Pre))
            .build()
      );
   public final Setting<AutoCrystal.SwingMode> breakSwingMode = this.sgSwing
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("break-swing-mode"))
                  .description("Mode for swinging your hand when breaking."))
               .defaultValue(AutoCrystal.SwingMode.Packet))
            .build()
      );
   public final Setting<AutoCrystal.SwingMode> placeSwingMode = this.sgSwing
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("place-swing-mode"))
                  .description("Mode for swinging your hand when placing."))
               .defaultValue(AutoCrystal.SwingMode.None))
            .build()
      );
   public final Setting<Double> placeRange = this.sgRange
      .add(
         new DoubleSetting.Builder()
            .name("place-range")
            .description("Maximum distance from your eye to the clicked base block (Grim allows 4.5).")
            .defaultValue(4.0)
            .min(1.0)
            .max(6.0)
            .sliderRange(1.0, 6.0)
            .build()
      );
   public final Setting<Double> breakRange = this.sgRange
      .add(
         new DoubleSetting.Builder()
            .name("break-range")
            .description(
               "Maximum distance from your eye to a crystal's hitbox, for breaking it and for any crystal placed to deal damage (Grim allows 3.0). The aim is computed from and checked against the same eye."
            )
            .defaultValue(3.0)
            .min(1.0)
            .max(7.0)
            .sliderRange(1.0, 7.0)
            .build()
      );
   public final Setting<AutoCrystal.TargetMode> targetMode = this.sgTargeting
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("target-mode"))
                  .description(
                     "How targets are prioritized. LowestHealth / Closest only crystal the max-targets best enemies; HighestDamage weighs every enemy in range."
                  ))
               .defaultValue(AutoCrystal.TargetMode.HighestDamage))
            .build()
      );
   public final Setting<Double> targetRange = this.sgTargeting
      .add(
         new DoubleSetting.Builder()
            .name("target-range")
            .description("Maximum range for target selection.")
            .defaultValue(12.0)
            .min(0.0)
            .max(16.0)
            .sliderRange(0.0, 16.0)
            .build()
      );
   public final Setting<Integer> maxTargets = this.sgTargeting
      .add(
         new IntSetting.Builder()
            .name("max-targets")
            .description("Maximum number of enemies crystalled at once (LowestHealth / Closest).")
            .defaultValue(1)
            .min(1)
            .max(5)
            .sliderRange(1, 5)
            .visible(() -> this.targetMode.get() != AutoCrystal.TargetMode.HighestDamage)
            .build()
      );
   public final Setting<Boolean> targetPlayers = this.sgTargeting
      .add(new BoolSetting.Builder().name("target-players").description("Place crystals to damage enemy players.").defaultValue(true).build());
   public final Setting<Boolean> targetMobs = this.sgTargeting
      .add(
         new BoolSetting.Builder()
            .name("target-mobs")
            .description("Also place crystals to damage hostile mobs (zombies, skeletons, etc.). Off by default so AutoCrystal only fights players.")
            .defaultValue(false)
            .build()
      );
   public final Setting<Boolean> ignoreFakePlayers = this.sgTargeting
      .add(
         new BoolSetting.Builder()
            .name("ignore-fake-players")
            .description(
               "Skip client-side fake players. They cannot actually be hurt, so crystals placed at one are real crystals spent on a target that never dies - and a fake player spawned on yourself makes it crystal your own feet."
            )
            .defaultValue(true)
            .build()
      );
   public final Setting<Boolean> survivalOnly = this.sgTargeting
      .add(
         new BoolSetting.Builder()
            .name("survival-only")
            .description("Only target players in survival mode. Creative and spectator players take no crystal damage.")
            .defaultValue(true)
            .build()
      );
   public final Setting<Boolean> assumeBestArmor = this.sgDamage
      .add(new BoolSetting.Builder().name("assume-best-armor").description("Assumes best possible armor values.").defaultValue(true).build());
   public final Setting<Boolean> ignoreTerrain = this.sgDamage
      .add(new BoolSetting.Builder().name("ignore-terrain").description("Ignores non-blast-resistant blocks for enemy damage calc.").defaultValue(true).build());
   public final Setting<Boolean> noSelfPop = this.sgSafety
      .add(
         new BoolSetting.Builder()
            .name("no-self-pop")
            .description("Rejects places/breaks whose self damage would pop your totem (or kill you).")
            .defaultValue(true)
            .build()
      );
   public final Setting<Double> mineThreshold = this.sgCrawl
      .add(
         new DoubleSetting.Builder()
            .name("mine-threshold")
            .description(
               "Extra gate on the one mined block priced as air for enemy damage (needs anti-surround): SilentMine's primary, on the tick before its STOP (and for the crystal placed on top of it just before), only once its progress reaches this. 0 = no extra gate. The double-mine and rebreak blocks are priced as they stand."
            )
            .defaultValue(0.0)
            .min(0.0)
            .max(1.0)
            .sliderRange(0.0, 1.0)
            .visible(() -> this.antiSurround.get())
            .build()
      );
   public final Setting<Double> rebreakDmgThreshold = this.sgCrawl
      .add(
         new DoubleSetting.Builder()
            .name("rebreak-dmg-threshold")
            .description("If the best placement does this much more damage than the rebreak placement, use the best instead.")
            .defaultValue(6.0)
            .min(0.0)
            .max(20.0)
            .sliderRange(0.0, 20.0)
            .visible(() -> this.antiSurround.get())
            .build()
      );
   public final Setting<Boolean> basePlace = this.sgCrawl
      .add(
         new BoolSetting.Builder()
            .name("base-place")
            .description(
               "When there's a target but no obsidian/bedrock base to crystal on nearby (or a better crystal spot only needs a base), automatically place an obsidian block to make the base, then crystal on it next tick. Requires obsidian in your inventory."
            )
            .defaultValue(false)
            .build()
      );
   public final Setting<Double> basePlaceThreshold = this.sgCrawl
      .add(
         new DoubleSetting.Builder()
            .name("base-place-threshold")
            .description(
               "Extra crystal damage a base-needed spot must beat the best ready (already-based) spot by before obsidian is placed there. 0 = place a base whenever it beats or ties the ready spot; only matters when a ready spot already exists."
            )
            .defaultValue(3.0)
            .min(0.0)
            .max(20.0)
            .sliderRange(0.0, 20.0)
            .visible(() -> this.basePlace.get())
            .build()
      );
   public final Setting<Boolean> strictDirection = this.sgAdvanced
      .add(new BoolSetting.Builder().name("strict-direction").description("Strict (raycast) face selection for placement.").defaultValue(false).build());
   public final Setting<AutoCrystal.CrystalSwapMode> swapMode = this.sgAdvanced
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("swap-mode"))
                  .description(
                     "How crystals / base obsidian are swapped to. Silent = SwapManager instant swap (follows AntiCheatConfig item-swap-mode), OffhandSilent = into the offhand and back, None = hands only."
                  ))
               .defaultValue(AutoCrystal.CrystalSwapMode.Silent))
            .build()
      );
   public final Setting<Boolean> autoRestockHotbar = this.sgAdvanced
      .add(
         new BoolSetting.Builder()
            .name("auto-restock")
            .description("Automatically refills the hotbar with end crystals from inventory.")
            .defaultValue(true)
            .build()
      );
   public final Setting<Boolean> fakeCps = this.sgMisc
      .add(
         new BoolSetting.Builder()
            .name("fake-cps")
            .description("Inflates the CPS shown in the module info readout by +8 (display only; does not change actual placement/break rate).")
            .defaultValue(false)
            .build()
      );
   public final Setting<Boolean> debug = this.sgMisc
      .add(new BoolSetting.Builder().name("debug").description("Prints async calculation debug info.").defaultValue(false).build());
   public final Setting<AutoCrystal.RenderMode> renderMode = this.sgRender
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("render-mode"))
                  .description(
                     "FADE / SLIDE: one box on the last place. DelayDraw (gware): a fading box per place and per broken crystal. Simple (gware): the last place briefly. Debug (gware): every valid candidate base."
                  ))
               .defaultValue(AutoCrystal.RenderMode.FADE))
            .build()
      );
   public final Setting<ShapeMode> shapeMode = this.sgRender
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("shape-mode"))
                     .description("How the FADE / SLIDE / Simple / Debug boxes are drawn."))
                  .defaultValue(ShapeMode.Both))
               .visible(() -> this.renderMode.get() != AutoCrystal.RenderMode.DelayDraw && this.renderMode.get() != AutoCrystal.RenderMode.NONE))
            .build()
      );
   public final Setting<Double> renderFadeTime = this.sgRender
      .add(
         new DoubleSetting.Builder()
            .name("fade-time")
            .description("Duration of fade animation in seconds.")
            .defaultValue(1.0)
            .min(0.0)
            .max(2.5)
            .sliderRange(0.0, 2.5)
            .visible(() -> this.renderMode.get() == AutoCrystal.RenderMode.FADE)
            .build()
      );
   public final Setting<Integer> renderSlideSteps = this.sgRender
      .add(
         new IntSetting.Builder()
            .name("slide-steps")
            .description("Number of interpolation steps (frames) for slide.")
            .defaultValue(10)
            .min(0)
            .max(30)
            .sliderRange(0, 30)
            .visible(() -> this.renderMode.get() == AutoCrystal.RenderMode.SLIDE)
            .build()
      );
   public final Setting<Integer> renderLineOpacity = this.sgRender
      .add(
         new IntSetting.Builder()
            .name("line-opacity")
            .description("Transparency of the box outline.")
            .defaultValue(255)
            .min(0)
            .max(255)
            .sliderRange(0, 255)
            .visible(() -> this.isFadeOrSlide())
            .build()
      );
   public final Setting<Integer> renderFillOpacity = this.sgRender
      .add(
         new IntSetting.Builder()
            .name("fill-opacity")
            .description("Transparency of the box fill.")
            .defaultValue(60)
            .min(0)
            .max(255)
            .sliderRange(0, 255)
            .visible(() -> this.isFadeOrSlide())
            .build()
      );
   public final Setting<SettingColor> renderColor = this.sgRender
      .add(
         new ColorSetting.Builder()
            .name("color")
            .description("FADE / SLIDE box color.")
            .defaultValue(new SettingColor(255, 0, 128, 255))
            .visible(() -> this.isFadeOrSlide())
            .build()
      );
   public final Setting<SettingColor> simpleColor = this.sgRender
      .add(
         new ColorSetting.Builder()
            .name("simple-color")
            .description("Simple / Debug box color.")
            .defaultValue(new SettingColor(255, 0, 0, 40))
            .visible(() -> this.renderMode.get() == AutoCrystal.RenderMode.Simple || this.renderMode.get() == AutoCrystal.RenderMode.Debug)
            .build()
      );
   public final Setting<Double> simpleDrawTime = this.sgRender
      .add(
         new DoubleSetting.Builder()
            .name("simple-draw-time")
            .description("How long Simple draws the last place (seconds).")
            .defaultValue(0.15)
            .min(0.0)
            .sliderMax(1.0)
            .visible(() -> this.renderMode.get() == AutoCrystal.RenderMode.Simple)
            .build()
      );
   public final Setting<ShapeMode> placeDelayShapeMode = this.sgRender
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("place-delay-shape-mode"))
                     .description("How the DelayDraw place boxes are drawn."))
                  .defaultValue(ShapeMode.Both))
               .visible(() -> this.renderMode.get() == AutoCrystal.RenderMode.DelayDraw))
            .build()
      );
   public final Setting<SettingColor> placeDelayColor = this.sgRender
      .add(
         new ColorSetting.Builder()
            .name("place-delay-color")
            .description("DelayDraw place box color.")
            .defaultValue(new SettingColor(110, 0, 255, 40))
            .visible(() -> this.renderMode.get() == AutoCrystal.RenderMode.DelayDraw)
            .build()
      );
   public final Setting<Double> placeDelayFadeTime = this.sgRender
      .add(
         new DoubleSetting.Builder()
            .name("place-delay-fade-time")
            .description("How long a DelayDraw place box fades (seconds).")
            .defaultValue(0.7)
            .min(0.0)
            .sliderMax(2.0)
            .visible(() -> this.renderMode.get() == AutoCrystal.RenderMode.DelayDraw)
            .build()
      );
   public final Setting<ShapeMode> breakDelayShapeMode = this.sgRender
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("break-delay-shape-mode"))
                     .description("How the DelayDraw broken-crystal boxes are drawn."))
                  .defaultValue(ShapeMode.Both))
               .visible(() -> this.renderMode.get() == AutoCrystal.RenderMode.DelayDraw))
            .build()
      );
   public final Setting<SettingColor> breakDelayColor = this.sgRender
      .add(
         new ColorSetting.Builder()
            .name("break-delay-color")
            .description("DelayDraw broken-crystal box color (gware default: invisible).")
            .defaultValue(new SettingColor(0, 0, 0, 0))
            .visible(() -> this.renderMode.get() == AutoCrystal.RenderMode.DelayDraw)
            .build()
      );
   public final Setting<Double> breakDelayFadeTime = this.sgRender
      .add(
         new DoubleSetting.Builder()
            .name("break-delay-fade-time")
            .description("How long a DelayDraw broken-crystal box fades (seconds).")
            .defaultValue(0.4)
            .min(0.0)
            .sliderMax(2.0)
            .visible(() -> this.renderMode.get() == AutoCrystal.RenderMode.DelayDraw)
            .build()
      );
   public final Setting<Double> breakDelayFadeExponent = this.sgRender
      .add(
         new DoubleSetting.Builder()
            .name("break-delay-fade-exponent")
            .description("Exponent applied to the broken-crystal fade.")
            .defaultValue(1.6)
            .min(0.2)
            .sliderMax(4.0)
            .visible(() -> this.renderMode.get() == AutoCrystal.RenderMode.DelayDraw)
            .build()
      );
   public final Setting<Boolean> renderDamage = this.sgRender
      .add(
         new BoolSetting.Builder()
            .name("damage-render")
            .description("Render damage text on the FADE / SLIDE box.")
            .defaultValue(true)
            .visible(() -> this.isFadeOrSlide())
            .build()
      );
   private final AutoCrystalFeature feature = new AutoCrystalFeature();

   public static AutoCrystal get() {
      return INSTANCE;
   }

   public AutoCrystal() {
      super(Categories.Combat, "auto-crystal", "Automatically places and breaks end crystals.");
      INSTANCE = this;
   }

   @Override
   public void onActivate() {
      this.feature.onEnable();
      ReviveClient.EVENT_BUS.subscribe(this.feature);
   }

   @Override
   public void onDeactivate() {
      ReviveClient.EVENT_BUS.unsubscribe(this.feature);
      this.feature.onDisable();
   }

   public void featureInfo(String key, String message) {
      this.info("%s", new Object[]{message});
   }

   private boolean isFadeOrSlide() {
      return this.renderMode.get() == AutoCrystal.RenderMode.FADE || this.renderMode.get() == AutoCrystal.RenderMode.SLIDE;
   }

   public boolean preplaceCrystal(BlockPos pos, boolean snap) {
      return this.isRunningHere() && this.feature.preplaceCrystal(pos, snap);
   }

   public boolean preplaceCrystal(BlockPos pos, boolean snap, BlockPos pretendAir) {
      return this.isRunningHere() && this.feature.preplaceCrystal(pos, snap, pretendAir);
   }

   public boolean isOwnBasePlace(BlockPos pos) {
      return this.isRunningHere() && this.feature.isOwnBasePlace(pos);
   }

   public boolean canCrystalAt(BlockPos pos) {
      return this.isRunningHere() && this.feature.canCrystalAt(pos);
   }

   public boolean glassSealOwns(BlockPos pos) {
      return this.isRunningHere() && this.feature.glassSealOwns(pos);
   }

   @Override
   public String getInfoString() {
      return this.feature.getInfoString();
   }

   @Override
   public JsonObject writeRemoteRender() {
      return this.feature.writeRemoteRender(this);
   }

   @Override
   public void readRemoteRender(JsonObject state, PbFx.Clock clock) {
      this.feature.readRemoteRender(state, clock);
   }

   @Override
   public void drawRemote3D(Render3DEvent event) {
      this.feature.onRender3D(event);
   }

   @Override
   public void drawRemote2D(Render2DEvent event) {
      this.feature.onRender2D(event);
   }

   @Override
   public void clearRemote() {
      this.feature.clearRemoteRender();
   }

   public int getBreaksPerSecond() {
      return this.feature.getBreaksPerSecond();
   }

   public int getPlacesPerSecond() {
      return this.feature.getPlacesPerSecond();
   }

   public static enum CrystalSwapMode {
      Silent,
      OffhandSilent,
      None;
   }

   public static enum PingCompensation {
      Auto,
      Manual,
      Off;
   }

   public static enum RenderMode {
      FADE,
      SLIDE,
      DelayDraw,
      Simple,
      Debug,
      NONE;
   }

   public static enum RotationTiming {
      Pre,
      Post;
   }

   public static enum SwingMode {
      Packet,
      Client,
      None;
   }

   public static enum TargetMode {
      LowestHealth,
      Closest,
      HighestDamage;
   }
}
