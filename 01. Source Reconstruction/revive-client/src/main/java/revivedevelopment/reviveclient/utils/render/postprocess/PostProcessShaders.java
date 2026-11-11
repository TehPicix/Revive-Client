package revivedevelopment.reviveclient.utils.render.postprocess;

import meteordevelopment.orbit.EventHandler;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.events.render.Render2DEvent;
import revivedevelopment.reviveclient.utils.PreInit;

public class PostProcessShaders {
   public static EntityShader CHAMS;
   public static EntityShader ENTITY_OUTLINE;
   public static EntityShader SWORD_AURA;
   private static boolean loaded;

   private PostProcessShaders() {
   }

   @PreInit
   public static void init() {
      ReviveClient.EVENT_BUS.subscribe(PostProcessShaders.class);
   }

   public static void ensureLoaded() {
      if (!loaded) {
         loaded = true;
         CHAMS = new ChamsShader();
         ENTITY_OUTLINE = new EntityOutlineShader();
         SWORD_AURA = new SwordAuraShader();
      }
   }

   public static void beginRender() {
      ensureLoaded();
      CHAMS.clearTexture();
      ENTITY_OUTLINE.clearTexture();
      SWORD_AURA.clearTexture();
   }

   public static void submitEntityVertices() {
      ensureLoaded();
      CHAMS.submitVertices();
      ENTITY_OUTLINE.submitVertices();
      SWORD_AURA.submitVertices();
   }

   @EventHandler
   private static void onRender(Render2DEvent event) {
      ensureLoaded();
      CHAMS.render();
      ENTITY_OUTLINE.render();
      SWORD_AURA.render();
   }

   public static void onResized(int width, int height) {
      if (ReviveClient.mc != null && loaded) {
         CHAMS.onResized(width, height);
         ENTITY_OUTLINE.onResized(width, height);
         SWORD_AURA.onResized(width, height);
      }
   }
}
