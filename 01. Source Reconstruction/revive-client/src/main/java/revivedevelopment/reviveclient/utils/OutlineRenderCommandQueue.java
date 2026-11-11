package revivedevelopment.reviveclient.utils;

import java.util.List;
import net.minecraft.block.BlockState;
import net.minecraft.client.font.TextRenderer.TextLayerType;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.block.MovingBlockRenderState;
import net.minecraft.client.render.command.BatchingRenderCommandQueue;
import net.minecraft.client.render.command.OrderedRenderCommandQueueImpl;
import net.minecraft.client.render.command.ModelCommandRenderer.CrumblingOverlayCommand;
import net.minecraft.client.render.command.OrderedRenderCommandQueue.Custom;
import net.minecraft.client.render.command.OrderedRenderCommandQueue.LayeredCustom;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.entity.state.EntityRenderState.LeashData;
import net.minecraft.client.render.entity.state.EntityRenderState.ShadowPiece;
import net.minecraft.client.render.item.ItemRenderState.Glint;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.render.model.BlockStateModel;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import revivedevelopment.reviveclient.utils.render.color.Color;

public class OutlineRenderCommandQueue extends OrderedRenderCommandQueueImpl {
   private int color;
   private int[] tints;

   public void setColor(Color color) {
      this.color = color.getPacked();
   }

   public BatchingRenderCommandQueue getBatchingQueue(int i) {
      return (BatchingRenderCommandQueue)this.batchingQueues.computeIfAbsent(i, order -> new OutlineRenderCommandQueue.OutlineBatchingRenderCommandQueue(this));
   }

   private class OutlineBatchingRenderCommandQueue extends BatchingRenderCommandQueue {
      public OutlineBatchingRenderCommandQueue(OrderedRenderCommandQueueImpl orderedQueueImpl) {
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

      public void submitFire(MatrixStack matrices, EntityRenderState renderState, Quaternionf rotation) {
      }

      public void submitLeash(MatrixStack matrices, LeashData leashData) {
      }

      public <S> void submitModel(
         Model<? super S> model,
         S state,
         MatrixStack matrices,
         RenderLayer renderLayer,
         int light,
         int overlay,
         int tintedColor,
         @Nullable Sprite sprite,
         int outlineColor,
         @Nullable CrumblingOverlayCommand crumblingOverlay
      ) {
         super.submitModel(model, state, matrices, renderLayer, light, overlay, OutlineRenderCommandQueue.this.color, sprite, 0, crumblingOverlay);
      }

      public void submitModelPart(
         ModelPart part,
         MatrixStack matrices,
         RenderLayer renderLayer,
         int light,
         int overlay,
         @Nullable Sprite sprite,
         boolean sheeted,
         boolean hasGlint,
         int tintedColor,
         @Nullable CrumblingOverlayCommand crumblingOverlay,
         int i
      ) {
         super.submitModelPart(
            part, matrices, renderLayer, light, overlay, sprite, sheeted, hasGlint, OutlineRenderCommandQueue.this.color, crumblingOverlay, i
         );
      }

      public void submitBlock(MatrixStack matrices, BlockState state, int light, int overlay, int outlineColor) {
      }

      public void submitMovingBlock(MatrixStack matrices, MovingBlockRenderState state) {
      }

      public void submitBlockStateModel(
         MatrixStack matrices, RenderLayer renderLayer, BlockStateModel model, float r, float g, float b, int light, int overlay, int outlineColor
      ) {
         r = Color.toRGBAR(OutlineRenderCommandQueue.this.color) / 255.0F;
         g = Color.toRGBAG(OutlineRenderCommandQueue.this.color) / 255.0F;
         b = Color.toRGBAB(OutlineRenderCommandQueue.this.color) / 255.0F;
         super.submitBlockStateModel(matrices, renderLayer, model, r, g, b, light, overlay, outlineColor);
      }

      public void submitItem(
         MatrixStack matrices,
         ItemDisplayContext displayContext,
         int light,
         int overlay,
         int outlineColors,
         int[] tintLayers,
         List<BakedQuad> quads,
         RenderLayer renderLayer,
         Glint glintType
      ) {
         if (OutlineRenderCommandQueue.this.tints == null || OutlineRenderCommandQueue.this.tints[0] != OutlineRenderCommandQueue.this.color) {
            OutlineRenderCommandQueue.this.tints = new int[]{
               OutlineRenderCommandQueue.this.color,
               OutlineRenderCommandQueue.this.color,
               OutlineRenderCommandQueue.this.color,
               OutlineRenderCommandQueue.this.color
            };
         }

         super.submitItem(matrices, displayContext, light, overlay, outlineColors, OutlineRenderCommandQueue.this.tints, quads, renderLayer, glintType);
      }

      public void submitCustom(MatrixStack matrices, RenderLayer renderLayer, Custom customRenderer) {
      }

      public void submitCustom(LayeredCustom customRenderer) {
      }
   }
}
