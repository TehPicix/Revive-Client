package revivedevelopment.reviveclient.systems.hud.elements;

import revivedevelopment.reviveclient.systems.hud.Hud;
import revivedevelopment.reviveclient.systems.hud.HudElementInfo;

public class ReviveTextHud {
   public static final HudElementInfo<TextHud> INFO = new HudElementInfo<>(Hud.GROUP, "text", "Displays arbitrary text with Starscript.", ReviveTextHud::create);
   public static final HudElementInfo<TextHud>.Preset FPS = addPreset("FPS", "FPS: #1{fps}", 0);
   public static final HudElementInfo<TextHud>.Preset TPS = addPreset("TPS", "TPS: #1{round(server.tps, 1)}");
   public static final HudElementInfo<TextHud>.Preset PING = addPreset("Ping", "Ping: #1{ping}");
   public static final HudElementInfo<TextHud>.Preset SPEED = addPreset("Speed", "Speed: #1{round(player.speed, 1)}", 0);
   public static final HudElementInfo<TextHud>.Preset GAME_MODE = addPreset("Game mode", "Game mode: #1{player.gamemode}", 0);
   public static final HudElementInfo<TextHud>.Preset DURABILITY = addPreset("Durability", "Durability: #1{player.hand_or_offhand.durability}");
   public static final HudElementInfo<TextHud>.Preset POSITION = addPreset(
      "Position", "Pos: #1{floor(camera.pos.x)}, {floor(camera.pos.y)}, {floor(camera.pos.z)}", 0
   );
   public static final HudElementInfo<TextHud>.Preset OPPOSITE_POSITION = addPreset(
      "Opposite Position",
      "{player.opposite_dimension != \"End\" ? player.opposite_dimension + \":\" : \"\"} #1{player.opposite_dimension != \"End\" ? \"\" + floor(camera.opposite_dim_pos.x) + \", \" + floor(camera.opposite_dim_pos.y) + \", \" + floor(camera.opposite_dim_pos.z) : \"\"}",
      0
   );
   public static final HudElementInfo<TextHud>.Preset LOOKING_AT = addPreset("Looking at", "Looking at: #1{crosshair_target.value}", 0);
   public static final HudElementInfo<TextHud>.Preset LOOKING_AT_WITH_POSITION = addPreset(
      "Looking at with position",
      "Looking at: #1{crosshair_target.value} {crosshair_target.type != \"miss\" ? \"(\" + \"\" + floor(crosshair_target.value.pos.x) + \", \" + floor(crosshair_target.value.pos.y) + \", \" + floor(crosshair_target.value.pos.z) + \")\" : \"\"}",
      0
   );
   public static final HudElementInfo<TextHud>.Preset BREAKING_PROGRESS = addPreset(
      "Breaking progress", "Breaking progress: #1{round(player.breaking_progress * 100)}%", 0
   );
   public static final HudElementInfo<TextHud>.Preset SERVER = addPreset("Server", "Server: #1{server}");
   public static final HudElementInfo<TextHud>.Preset BIOME = addPreset("Biome", "Biome: #1{player.biome}", 0);
   public static final HudElementInfo<TextHud>.Preset WORLD_TIME = addPreset("World time", "Time: #1{server.time}");
   public static final HudElementInfo<TextHud>.Preset REAL_TIME = addPreset("Real time", "Time: #1{time}");
   public static final HudElementInfo<TextHud>.Preset ROTATION = addPreset(
      "Rotation", "{camera.direction} #1({round(camera.yaw, 1)}, {round(camera.pitch, 1)})", 0
   );
   public static final HudElementInfo<TextHud>.Preset MODULE_ENABLED = addPreset(
      "Module enabled", "Kill Aura: {revive.is_module_active(\"kill-aura\") ? #2 \"ON\" : #3 \"OFF\"}", 0
   );
   public static final HudElementInfo<TextHud>.Preset MODULE_ENABLED_WITH_INFO = addPreset(
      "Module enabled with info", "Kill Aura: {revive.is_module_active(\"kill-aura\") ? #2 \"ON\" : #3 \"OFF\"} #1{revive.get_module_info(\"kill-aura\")}", 0
   );
   public static final HudElementInfo<TextHud>.Preset WATERMARK = addPreset("Watermark", "{revive.name} #1{revive.version}");
   public static final HudElementInfo<TextHud>.Preset BARITONE = addPreset("Baritone", "Baritone: #1{baritone.process_name}");
   public static final HudElementInfo<TextHud>.Preset PING_BYPASS = addPreset(
      "PingBypass",
      "VPS: #1{pingbypass.link_ping >= 0 ? \"\" + pingbypass.link_ping + \"ms\" : \"off\"} #0Server: #1{pingbypass.server_ping >= 0 ? \"\" + round(pingbypass.server_ping, 1) + \"ms\" : \"-\"} #0AC: #1{pingbypass.crystal_cps >= 0 ? \"\" + pingbypass.crystal_cps + \" CPS\" : \"-\"}",
      0
   );

   private static TextHud create() {
      return new TextHud(INFO);
   }

   private static HudElementInfo<TextHud>.Preset addPreset(String title, String text, int updateDelay) {
      return INFO.addPreset(title, textHud -> {
         if (text != null) {
            textHud.text.set(text);
         }

         if (updateDelay != -1) {
            textHud.updateDelay.set(updateDelay);
         }
      });
   }

   private static HudElementInfo<TextHud>.Preset addPreset(String title, String text) {
      return addPreset(title, text, -1);
   }

   static {
      addPreset("Empty", null);
   }
}
