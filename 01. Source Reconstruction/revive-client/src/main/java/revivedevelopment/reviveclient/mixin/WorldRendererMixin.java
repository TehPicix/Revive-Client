package revivedevelopment.reviveclient.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import it.unimi.dsi.fastutil.Stack;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.function.Function;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.DefaultFramebufferSet;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.command.RenderDispatcher;
import net.minecraft.client.render.entity.EntityRenderManager;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.state.WorldRenderState;
import net.minecraft.client.util.Handle;
import net.minecraft.client.util.ObjectAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.events.render.SubmitEntitiesEvent;
import revivedevelopment.reviveclient.mixininterface.IEntityRenderState;
import revivedevelopment.reviveclient.mixininterface.IWorldRenderer;
import revivedevelopment.reviveclient.systems.modules.Modules;
import revivedevelopment.reviveclient.systems.modules.combat.SwordAura;
import revivedevelopment.reviveclient.systems.modules.render.ESP;
import revivedevelopment.reviveclient.utils.OutlineRenderCommandQueue;
import revivedevelopment.reviveclient.utils.render.NoopImmediateVertexConsumerProvider;
import revivedevelopment.reviveclient.utils.render.NoopOutlineVertexConsumerProvider;
import revivedevelopment.reviveclient.utils.render.WrapperImmediateVertexConsumerProvider;
import revivedevelopment.reviveclient.utils.render.color.Color;
import revivedevelopment.reviveclient.utils.render.postprocess.EntityShader;
import revivedevelopment.reviveclient.utils.render.postprocess.PostProcessShaders;

@Mixin({WorldRenderer.class})
public abstract class WorldRendererMixin implements IWorldRenderer {
   @Shadow
   private Framebuffer field_53080;
   @Shadow
   @Final
   private DefaultFramebufferSet field_53081;
   @Shadow
   @Final
   private EntityRenderManager field_4109;
   @Unique
   private final OutlineRenderCommandQueue outlineRenderCommandQueue = new OutlineRenderCommandQueue();
   @Unique
   private VertexConsumerProvider provider;
   @Unique
   private RenderDispatcher renderDispatcher;
   @Unique
   private Stack<Framebuffer> framebufferStack;
   @Unique
   private Stack<Handle<Framebuffer>> framebufferHandleStack;
   @Unique
   private ESP esp;
   @Unique
   private SwordAura swordAura;

   @Inject(
      method = {"<init>"},
      at = {@At("TAIL")}
   )
   private void init$IWorldRenderer(CallbackInfo info) {
      this.framebufferStack = new ObjectArrayList();
      this.framebufferHandleStack = new ObjectArrayList();
   }

   @Inject(
      method = {"method_22710"},
      at = {@At("HEAD")}
   )
   private void onRenderHead(
      ObjectAllocator allocator,
      RenderTickCounter tickCounter,
      boolean renderBlockOutline,
      Camera camera,
      Matrix4f positionMatrix,
      Matrix4f projectionMatrix,
      Matrix4f matrix4f2,
      GpuBufferSlice fog,
      Vector4f fogColor,
      boolean shouldRenderSky,
      CallbackInfo ci
   ) {
      PostProcessShaders.beginRender();
   }

   @Inject(
      method = {"method_72916"},
      at = {@At("TAIL")}
   )
   private void onPushEntityRenders(MatrixStack matrices, WorldRenderState worldState, OrderedRenderCommandQueue queue, CallbackInfo info) {
      ReviveClient.EVENT_BUS.post(SubmitEntitiesEvent.get(matrices, queue, worldState.cameraRenderState));
      if (this.renderDispatcher == null) {
         this.renderDispatcher = new RenderDispatcher(
            this.outlineRenderCommandQueue,
            ReviveClient.mc.getBlockRenderManager(),
            new WrapperImmediateVertexConsumerProvider(() -> this.provider),
            ReviveClient.mc.getAtlasManager(),
            NoopOutlineVertexConsumerProvider.INSTANCE,
            NoopImmediateVertexConsumerProvider.INSTANCE,
            ReviveClient.mc.textRenderer
         );
      }

      if (this.esp == null) {
         this.esp = Modules.get().get(ESP.class);
      }

      if (this.swordAura == null) {
         this.swordAura = Modules.get().get(SwordAura.class);
      }

      this.draw(worldState, matrices, PostProcessShaders.CHAMS, entity -> Color.WHITE);
      this.draw(worldState, matrices, PostProcessShaders.ENTITY_OUTLINE, entity -> this.esp.getColor(entity));
      this.draw(worldState, matrices, PostProcessShaders.SWORD_AURA, entity -> this.swordAura.getShaderColor(entity));
   }

   @Unique
   private void draw(WorldRenderState worldState, MatrixStack matrices, EntityShader shader, Function<Entity, Color> colorGetter) {
      Vec3d camera = worldState.cameraRenderState.pos;
      boolean empty = true;

      for (EntityRenderState state : worldState.entityRenderStates) {
         Entity entity = ((IEntityRenderState)state).revive$getEntity();
         if (entity != null && shader.shouldDraw(entity)) {
            Color color = colorGetter.apply(entity);
            if (color != null) {
               this.outlineRenderCommandQueue.setColor(color);
               EntityRenderer<?, EntityRenderState> renderer = (EntityRenderer<?, EntityRenderState>)(EntityRenderer<?, ?>)this.field_4109.getRenderer(state);
               Vec3d offset = renderer.getPositionOffset(state);
               matrices.push();

               try {
                  matrices.translate(state.x - camera.x + offset.x, state.y - camera.y + offset.y, state.z - camera.z + offset.z);
                  renderer.render(state, matrices, this.outlineRenderCommandQueue, worldState.cameraRenderState);
               } finally {
                  matrices.pop();
               }

               empty = false;
            }
         }
      }

      if (!empty) {
         this.revive$pushEntityOutlineFramebuffer(shader.framebuffer);
         this.provider = shader.vertexConsumerProvider;

         try {
            this.renderDispatcher.render();
         } finally {
            this.outlineRenderCommandQueue.onNextFrame();
            this.provider = null;
            this.revive$popEntityOutlineFramebuffer();
         }
      }
   }

   @Inject(
      method = {"method_62214"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/class_4618;method_23285()V"
      )}
   )
   private void onSubmitEntityVertices(CallbackInfo ci) {
      PostProcessShaders.submitEntityVertices();
   }

   @Inject(
      method = {"method_3242"},
      at = {@At("HEAD")}
   )
   private void onResized(int width, int height, CallbackInfo info) {
      PostProcessShaders.onResized(width, height);
   }

   @Override
   public void revive$pushEntityOutlineFramebuffer(Framebuffer framebuffer) {
      this.framebufferStack.push(this.field_53080);
      this.field_53080 = framebuffer;
      this.framebufferHandleStack.push(this.field_53081.entityOutlineFramebuffer);
      this.field_53081.entityOutlineFramebuffer = () -> framebuffer;
   }

   @Override
   public void revive$popEntityOutlineFramebuffer() {
      this.field_53080 = (Framebuffer)this.framebufferStack.pop();
      this.field_53081.entityOutlineFramebuffer = (Handle)this.framebufferHandleStack.pop();
   }
}
