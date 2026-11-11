package revivedevelopment.reviveclient.utils.render;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.List;
import net.minecraft.client.font.TextRenderer.TextLayerType;
import net.minecraft.client.render.OutputTarget;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider.Immediate;
import net.minecraft.client.render.command.BatchingRenderCommandQueue;
import net.minecraft.client.render.command.OrderedRenderCommandQueueImpl;
import net.minecraft.client.render.command.RenderDispatcher;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.entity.state.EntityRenderState.ShadowPiece;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.events.render.Render3DEvent;
import revivedevelopment.reviveclient.mixin.RenderLayerAccessor;
import revivedevelopment.reviveclient.mixin.RenderSetupAccessor;
import revivedevelopment.reviveclient.mixininterface.IEntityRenderState;
import revivedevelopment.reviveclient.renderer.Renderer3D;
import revivedevelopment.reviveclient.renderer.ShapeMode;
import revivedevelopment.reviveclient.utils.render.color.Color;

public class WireframeEntityRenderer {
   private static final MatrixStack matrices = new MatrixStack();
   private static Renderer3D renderer;
   private static final OrderedRenderCommandQueueImpl renderCommandQueue = new WireframeEntityRenderer.GeometryOnlyQueue();
   private static final RenderDispatcher renderDispatcher = new RenderDispatcher(
      renderCommandQueue,
      ReviveClient.mc.getBlockRenderManager(),
      WireframeEntityRenderer.MyVertexConsumerProvider.INSTANCE,
      ReviveClient.mc.getAtlasManager(),
      NoopOutlineVertexConsumerProvider.INSTANCE,
      NoopImmediateVertexConsumerProvider.INSTANCE,
      ReviveClient.mc.textRenderer
   );
   private static Color sideColor;
   private static Color lineColor;
   private static ShapeMode shapeMode;
   private static double offsetX;
   private static double offsetY;
   private static double offsetZ;

   private WireframeEntityRenderer() {
   }

   public static void render(Render3DEvent event, Entity entity, double scale, Color sideColor, Color lineColor, ShapeMode shapeMode) {
      WireframeEntityRenderer.renderer = event.renderer;
      WireframeEntityRenderer.sideColor = sideColor;
      WireframeEntityRenderer.lineColor = lineColor;
      WireframeEntityRenderer.shapeMode = shapeMode;
      float tickDelta = ReviveClient.mc.world.getTickManager().isFrozen() ? 1.0F : event.tickDelta;
      offsetX = MathHelper.lerp(tickDelta, entity.lastRenderX, entity.getX());
      offsetY = MathHelper.lerp(tickDelta, entity.lastRenderY, entity.getY());
      offsetZ = MathHelper.lerp(tickDelta, entity.lastRenderZ, entity.getZ());
      EntityRenderer<Entity, EntityRenderState> renderer = (EntityRenderer<Entity, EntityRenderState>)(EntityRenderer<?, ?>)ReviveClient.mc.getEntityRenderDispatcher().getRenderer(entity);
      EntityRenderState state = renderer.getAndUpdateRenderState(entity, tickDelta);
      ((IEntityRenderState)state).revive$setEntity(entity);
      Vec3d entityOffset = renderer.getPositionOffset(state);
      offsetX = offsetX + entityOffset.x;
      offsetY = offsetY + entityOffset.y;
      offsetZ = offsetZ + entityOffset.z;

      try {
         matrices.push();

         try {
            matrices.scale((float)scale, (float)scale, (float)scale);
            renderer.render(state, matrices, renderCommandQueue, ReviveClient.mc.gameRenderer.getEntityRenderStates().cameraRenderState);
         } finally {
            matrices.pop();
         }

         renderDispatcher.render();
      } finally {
         renderCommandQueue.onNextFrame();
         WireframeEntityRenderer.MyVertexConsumerProvider.INSTANCE.resetQuads();
      }
   }

   private static class GeometryOnlyBatchingQueue extends BatchingRenderCommandQueue {
      GeometryOnlyBatchingQueue(OrderedRenderCommandQueueImpl orderedQueueImpl) {
         super(orderedQueueImpl);
      }

      public void submitShadowPieces(MatrixStack matrices, float shadowRadius, List<ShadowPiece> shadowPieces) {
      }

      public void submitLabel(
         MatrixStack matrices,
         @Nullable Vec3d nameLabelPos,
         int y,
         Text label,
         boolean notSneaking,
         int light,
         double squaredDistanceToCamera,
         CameraRenderState cameraState
      ) {
      }

      public void submitText(
         MatrixStack matrices,
         float x,
         float y,
         OrderedText text,
         boolean dropShadow,
         TextLayerType layerType,
         int light,
         int color,
         int backgroundColor,
         int outlineColor
      ) {
      }
   }

   private static class GeometryOnlyQueue extends OrderedRenderCommandQueueImpl {
      public BatchingRenderCommandQueue getBatchingQueue(int i) {
         return (BatchingRenderCommandQueue)this.batchingQueues.computeIfAbsent(i, order -> new WireframeEntityRenderer.GeometryOnlyBatchingQueue(this));
      }
   }

   private static class MyVertexConsumer implements VertexConsumer {
      private final float[] xs = new float[4];
      private final float[] ys = new float[4];
      private final float[] zs = new float[4];
      private int i = 0;

      public VertexConsumer vertex(float x, float y, float z) {
         this.xs[this.i] = x;
         this.ys[this.i] = y;
         this.zs[this.i] = z;
         this.i++;
         if (this.i == 4) {
            WireframeEntityRenderer.renderer
               .side(
                  WireframeEntityRenderer.offsetX + this.xs[0],
                  WireframeEntityRenderer.offsetY + this.ys[0],
                  WireframeEntityRenderer.offsetZ + this.zs[0],
                  WireframeEntityRenderer.offsetX + this.xs[1],
                  WireframeEntityRenderer.offsetY + this.ys[1],
                  WireframeEntityRenderer.offsetZ + this.zs[1],
                  WireframeEntityRenderer.offsetX + this.xs[2],
                  WireframeEntityRenderer.offsetY + this.ys[2],
                  WireframeEntityRenderer.offsetZ + this.zs[2],
                  WireframeEntityRenderer.offsetX + this.xs[3],
                  WireframeEntityRenderer.offsetY + this.ys[3],
                  WireframeEntityRenderer.offsetZ + this.zs[3],
                  WireframeEntityRenderer.sideColor,
                  WireframeEntityRenderer.lineColor,
                  WireframeEntityRenderer.shapeMode
               );
            this.i = 0;
         }

         return this;
      }

      public VertexConsumer color(int red, int green, int blue, int alpha) {
         return this;
      }

      public VertexConsumer color(int argb) {
         return this;
      }

      public VertexConsumer texture(float u, float v) {
         return this;
      }

      public VertexConsumer overlay(int u, int v) {
         return this;
      }

      public VertexConsumer light(int u, int v) {
         return this;
      }

      public VertexConsumer normal(float x, float y, float z) {
         return this;
      }

      public VertexConsumer lineWidth(float width) {
         return this;
      }
   }

   private static class MyVertexConsumerProvider extends Immediate {
      public static final WireframeEntityRenderer.MyVertexConsumerProvider INSTANCE = new WireframeEntityRenderer.MyVertexConsumerProvider();
      private final Object2ObjectOpenHashMap<RenderLayer, WireframeEntityRenderer.MyVertexConsumer> buffers = new Object2ObjectOpenHashMap();

      protected MyVertexConsumerProvider() {
         super(null, null);
      }

      public VertexConsumer getBuffer(RenderLayer layer) {
         Object setup = ((RenderLayerAccessor)layer).getRenderSetup();
         if (((RenderSetupAccessor)setup).getOutputTarget() == OutputTarget.ITEM_ENTITY_TARGET) {
            return NoopVertexConsumer.INSTANCE;
         } else {
            WireframeEntityRenderer.MyVertexConsumer vertexConsumer = (WireframeEntityRenderer.MyVertexConsumer)this.buffers.get(layer);
            if (vertexConsumer == null) {
               vertexConsumer = new WireframeEntityRenderer.MyVertexConsumer();
               this.buffers.put(layer, vertexConsumer);
            }

            return vertexConsumer;
         }
      }

      void resetQuads() {
         ObjectIterator var1 = this.buffers.values().iterator();

         while (var1.hasNext()) {
            WireframeEntityRenderer.MyVertexConsumer consumer = (WireframeEntityRenderer.MyVertexConsumer)var1.next();
            consumer.i = 0;
         }
      }

      public void draw() {
         throw new RuntimeException();
      }

      public void draw(RenderLayer layer) {
         throw new RuntimeException();
      }
   }
}
