package revivedevelopment.reviveclient.utils.render.postprocess;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import java.nio.ByteBuffer;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gl.DynamicUniformStorage;
import net.minecraft.client.gl.DynamicUniformStorage.Uploadable;
import net.minecraft.entity.Entity;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.events.game.ResourcePacksReloadedEvent;
import revivedevelopment.reviveclient.renderer.MeshRenderer;
import revivedevelopment.reviveclient.renderer.ReviveRenderPipelines;
import revivedevelopment.reviveclient.renderer.Texture;
import revivedevelopment.reviveclient.systems.modules.Modules;
import revivedevelopment.reviveclient.systems.modules.render.Chams;
import revivedevelopment.reviveclient.utils.PostInit;
import revivedevelopment.reviveclient.utils.render.color.Color;

public class ChamsShader extends EntityShader {
   private static Texture IMAGE_TEX;
   private static Chams chams;
   private static boolean texLoaded;
   private static final int UNIFORM_SIZE = new Std140SizeCalculator().putVec4().putVec4().putVec4().putVec4().get();
   private static DynamicUniformStorage<ChamsShader.UniformData> UNIFORM_STORAGE;

   public ChamsShader() {
      super(ReviveRenderPipelines.POST_IMAGE);
      ReviveClient.EVENT_BUS.subscribe(ChamsShader.class);
   }

   @PostInit
   public static void load() {
      if (texLoaded) {
         loadTexture();
      }
   }

   private static void loadTexture() {
      Texture tex = Texture.readResource("/assets/revive-client/textures/chams.jpg", true, FilterMode.NEAREST);
      if (tex == null) {
         tex = Texture.readResource("/assets/revive-client/textures/chams.png", true, FilterMode.NEAREST);
      }

      if (tex != null) {
         Texture old = IMAGE_TEX;
         IMAGE_TEX = tex;
         if (old != null && old != tex) {
            old.close();
         }
      }
   }

   private static void ensureTexture() {
      if (!texLoaded) {
         texLoaded = true;
         loadTexture();
      }
   }

   @EventHandler
   private static void onResourcePacksReloaded(ResourcePacksReloadedEvent event) {
      load();
   }

   @Override
   protected void setupPass(MeshRenderer renderer) {
      ensureTexture();
      Color fill = chams.fillColor();
      Color gs = chams.gradientStart.get();
      Color ge = chams.gradientEnd.get();
      renderer.uniform(
         "ImageData",
         uniformStorage()
            .write(
               new ChamsShader.UniformData(
                  fill.r / 255.0F,
                  fill.g / 255.0F,
                  fill.b / 255.0F,
                  fill.a / 255.0F,
                  gs.r / 255.0F,
                  gs.g / 255.0F,
                  gs.b / 255.0F,
                  ge.r / 255.0F,
                  ge.g / 255.0F,
                  ge.b / 255.0F,
                  (float)chams.mode.get().ordinal(),
                  (float)chams.gradientDirection.get().ordinal(),
                  chams.warpScale.get().floatValue(),
                  chams.mode.get() == Chams.Mode.Plasma ? chams.plasmaSpeed.get().floatValue() : chams.warpSpeed.get().floatValue()
               )
            )
      );
      if (IMAGE_TEX != null) {
         renderer.sampler("u_TextureI", IMAGE_TEX.getGlTextureView(), RenderSystem.getSamplerCache().get(FilterMode.NEAREST));
      }
   }

   @Override
   public boolean shouldDraw() {
      if (chams == null) {
         chams = Modules.get().get(Chams.class);
      }

      return chams.isActive() && chams.throughWalls.get() && chams.hasWorldTargets() || chams.handChamsEnabled();
   }

   @Override
   public boolean shouldDraw(Entity entity) {
      return !this.shouldDraw() ? false : chams.shouldCham(entity);
   }

   private static DynamicUniformStorage<ChamsShader.UniformData> uniformStorage() {
      if (UNIFORM_STORAGE == null) {
         UNIFORM_STORAGE = new DynamicUniformStorage("Revive - Image UBO", UNIFORM_SIZE, 16);
      }

      return UNIFORM_STORAGE;
   }

   public static void flipFrame() {
      uniformStorage().clear();
   }

   private record UniformData(
      float fr,
      float fg,
      float fb,
      float fa,
      float gsr,
      float gsg,
      float gsb,
      float ger,
      float geg,
      float geb,
      float mode,
      float gradientDir,
      float warpScale,
      float warpSpeed
   ) implements Uploadable {
      public void write(ByteBuffer buffer) {
         Std140Builder.intoBuffer(buffer)
            .putVec4(this.fr, this.fg, this.fb, this.fa)
            .putVec4(this.gsr, this.gsg, this.gsb, 0.0F)
            .putVec4(this.ger, this.geg, this.geb, 0.0F)
            .putVec4(this.mode, this.gradientDir, this.warpScale, this.warpSpeed);
      }
   }
}
