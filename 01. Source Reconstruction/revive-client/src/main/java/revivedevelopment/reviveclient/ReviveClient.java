package revivedevelopment.reviveclient;

import java.io.File;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import meteordevelopment.orbit.EventBus;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.IEventBus;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.MouseInput;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.MixinEnvironment;
import revivedevelopment.reviveclient.addons.AddonManager;
import revivedevelopment.reviveclient.addons.ReviveAddon;
import revivedevelopment.reviveclient.events.game.OpenScreenEvent;
import revivedevelopment.reviveclient.events.revive.KeyEvent;
import revivedevelopment.reviveclient.events.revive.MouseButtonEvent;
import revivedevelopment.reviveclient.events.world.TickEvent;
import revivedevelopment.reviveclient.gui.GuiThemes;
import revivedevelopment.reviveclient.gui.WidgetScreen;
import revivedevelopment.reviveclient.gui.newgui.NewGuiScreen;
import revivedevelopment.reviveclient.gui.newgui.modern.ModernGuiScreen;
import revivedevelopment.reviveclient.systems.Systems;
import revivedevelopment.reviveclient.systems.config.Config;
import revivedevelopment.reviveclient.systems.hud.screens.HudEditorScreen;
import revivedevelopment.reviveclient.systems.managers.BlockPlacementManager;
import revivedevelopment.reviveclient.systems.managers.EmoteSyncManager;
import revivedevelopment.reviveclient.systems.managers.InformationManager;
import revivedevelopment.reviveclient.systems.managers.PacketManager;
import revivedevelopment.reviveclient.systems.managers.RotationManager;
import revivedevelopment.reviveclient.systems.managers.SwapManager;
import revivedevelopment.reviveclient.systems.managers.TargetManager;
import revivedevelopment.reviveclient.systems.managers.TeamSyncManager;
import revivedevelopment.reviveclient.systems.modules.Categories;
import revivedevelopment.reviveclient.systems.modules.Modules;
import revivedevelopment.reviveclient.systems.modules.gui.Gui;
import revivedevelopment.reviveclient.utils.Auth;
import revivedevelopment.reviveclient.utils.Obf;
import revivedevelopment.reviveclient.utils.PostInit;
import revivedevelopment.reviveclient.utils.PreInit;
import revivedevelopment.reviveclient.utils.ReflectInit;
import revivedevelopment.reviveclient.utils.Utils;
import revivedevelopment.reviveclient.utils.misc.Version;
import revivedevelopment.reviveclient.utils.misc.input.Input;
import revivedevelopment.reviveclient.utils.misc.input.KeyAction;
import revivedevelopment.reviveclient.utils.misc.input.KeyBinds;
import revivedevelopment.reviveclient.utils.network.OnlinePlayers;

public class ReviveClient implements ClientModInitializer {
   public static final String MOD_ID = "revive-client";
   public static final ModMetadata MOD_META;
   public static final String NAME;
   public static final Version VERSION;
   public static final String BUILD_NUMBER;
   public static ReviveClient INSTANCE;
   public static ReviveAddon ADDON;
   public static MinecraftClient mc;
   public static final IEventBus EVENT_BUS = new EventBus();
   public static final File FOLDER = FabricLoader.getInstance().getGameDir().resolve("revive-client").toFile();
   public static final Logger LOG;
   public static RotationManager ROTATION;
   public static InformationManager INFO;
   public static SwapManager SWAP;
   public static BlockPlacementManager BLOCK;
   public static PacketManager PACKET;
   public static TargetManager TARGET;
   public static TeamSyncManager TEAM;
   public static EmoteSyncManager EMOTES;
   private static boolean initialized;
   private boolean wasWidgetScreen;
   private boolean wasHudHiddenRoot;

   public ReviveClient() {
      INSTANCE = this;
   }

   public void onInitializeClient() {
      if (initialized) {
         LOG.warn("Skipping duplicate ReviveClient initialization");
      } else {
         initialized = true;
         mc = MinecraftClient.getInstance();
         if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
            LOG.info("Force loading mixins");
            MixinEnvironment.getCurrentEnvironment().audit();
         }

         LOG.info("Initializing {}", NAME);
         if (!FOLDER.exists()) {
            FOLDER.getParentFile().mkdirs();
            FOLDER.mkdir();
         }

         AddonManager.init();
         AddonManager.ADDONS
            .forEach(
               addon -> {
                  try {
                     EVENT_BUS.registerLambdaFactory(
                        addon.getPackage(), (lookupInMethod, klass) -> (Lookup)lookupInMethod.invoke(null, klass, MethodHandles.lookup())
                     );
                  } catch (AbstractMethodError var2) {
                     throw new RuntimeException("Addon \"%s\" is too old and cannot be ran.".formatted(addon.name), var2);
                  }
               }
            );
         ReflectInit.registerPackages();
         ReflectInit.init(PreInit.class);
         Categories.init();
         Systems.init();
         ROTATION = new RotationManager();
         INFO = new InformationManager();
         SWAP = new SwapManager();
         BLOCK = new BlockPlacementManager();
         PACKET = new PacketManager();
         TARGET = new TargetManager();
         TEAM = new TeamSyncManager();
         EMOTES = new EmoteSyncManager();
         EVENT_BUS.subscribe(this);
         AddonManager.ADDONS.forEach(ReviveAddon::onInitialize);
         Modules.get().sortModules();
         Systems.load();
         ReflectInit.init(PostInit.class);
         Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
               OnlinePlayers.leave();
            } catch (Throwable var3) {
            }

            try {
               Systems.save();
            } catch (Throwable var2) {
               LOG.error("Failed to save systems on shutdown", var2);
            }

            try {
               GuiThemes.save();
            } catch (Throwable var1) {
            }
         }));
      }
   }

   private void checkAuth() {
      String secret = Auth.fetchSecret();
      if (secret == null) {
         throw new RuntimeException("Authentication failed.");
      } else {
         Obf.KEY = secret;
      }
   }

   @EventHandler
   private void onTick(TickEvent.Post event) {
      if (mc.currentScreen == null && mc.getOverlay() == null && KeyBinds.OPEN_COMMANDS.wasPressed()) {
         mc.setScreen(new ChatScreen(Config.get().prefix.get(), true));
      }
   }

   @EventHandler
   private void onKey(KeyEvent event) {
      if (event.action == KeyAction.Press && event.key == Input.getKey(KeyBinds.OPEN_GUI)) {
         this.toggleGui();
      }
   }

   @EventHandler
   private void onMouseButton(MouseButtonEvent event) {
      if (event.action == KeyAction.Press && KeyBinds.OPEN_GUI.matchesMouse(new Click(0.0, 0.0, new MouseInput(event.button, 0)))) {
         this.toggleGui();
      }
   }

   private void toggleGui() {
      if (Utils.canCloseGui()) {
         mc.currentScreen.close();
      } else if (Utils.canOpenGui()) {
         mc.setScreen(openGuiScreen());
      }
   }

   public static Screen openGuiScreen() {
      Gui gui = Modules.get().get(Gui.class);
      return (Screen)(gui != null && gui.style.get() == Gui.GuiStyle.Modern ? new ModernGuiScreen() : new NewGuiScreen());
   }

   @EventHandler(
      priority = -200
   )
   private void onOpenScreen(OpenScreenEvent event) {
      if (event.screen instanceof WidgetScreen) {
         if (!this.wasWidgetScreen) {
            this.wasHudHiddenRoot = mc.options.hudHidden;
         }

         if (GuiThemes.get().hideHUD() || this.wasHudHiddenRoot) {
            mc.options.hudHidden = !(event.screen instanceof HudEditorScreen);
         }
      } else {
         if (this.wasWidgetScreen) {
            mc.options.hudHidden = this.wasHudHiddenRoot;
         }

         this.wasHudHiddenRoot = mc.options.hudHidden;
      }

      this.wasWidgetScreen = event.screen instanceof WidgetScreen;
   }

   public static Identifier identifier(String path) {
      return Identifier.of("revive-client", path);
   }

   static {
      try {
         EVENT_BUS.registerLambdaFactory(
            "revivedevelopment.reviveclient", (lookupInMethod, klass) -> (Lookup)lookupInMethod.invoke(null, klass, MethodHandles.lookup())
         );
      } catch (Exception var1) {
         throw new RuntimeException("Failed to register lambda factory for reviveclient", var1);
      }

      MOD_META = ((ModContainer)FabricLoader.getInstance().getModContainer("revive-client").orElseThrow()).getMetadata();
      NAME = MOD_META.getName();
      LOG = LoggerFactory.getLogger(NAME);
      String versionString = MOD_META.getVersion().getFriendlyString();
      if (versionString.contains("-")) {
         versionString = versionString.split("-")[0];
      }

      if (versionString.equals("${version}")) {
         versionString = "0.0.0";
      }

      VERSION = new Version(versionString);
      BUILD_NUMBER = MOD_META.getCustomValue("revive-client:build_number").getAsString();
   }
}
