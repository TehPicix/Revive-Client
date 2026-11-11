package revivedevelopment.reviveclient.utils.render;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.state.BlockEntityRenderState;
import net.minecraft.client.render.command.OrderedRenderCommandQueueImpl;
import net.minecraft.client.render.command.RenderDispatcher;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.render.model.BlockModelPart;
import net.minecraft.client.render.model.BlockStateModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import org.joml.Vector3fc;
import revivedevelopment.reviveclient.ReviveClient;

public abstract class SimpleBlockRenderer {
   private static final MatrixStack MATRICES = new MatrixStack();
   private static final List<BlockModelPart> PARTS = new ArrayList<>();
   private static final Direction[] DIRECTIONS = Direction.values();
   private static final Random RANDOM = Random.create();
   private static final OrderedRenderCommandQueueImpl renderCommandQueue = new OrderedRenderCommandQueueImpl();
   private static VertexConsumerProvider provider;
   private static final RenderDispatcher renderDispatcher = new RenderDispatcher(
      renderCommandQueue,
      ReviveClient.mc.getBlockRenderManager(),
      new WrapperImmediateVertexConsumerProvider(() -> provider),
      ReviveClient.mc.getAtlasManager(),
      NoopOutlineVertexConsumerProvider.INSTANCE,
      NoopImmediateVertexConsumerProvider.INSTANCE,
      ReviveClient.mc.textRenderer
   );

   private SimpleBlockRenderer() {
   }

   public static void renderWithBlockEntity(BlockEntity blockEntity, float tickDelta, IVertexConsumerProvider vertexConsumerProvider) {
      renderWithBlockEntity(blockEntity, tickDelta, vertexConsumerProvider, false);
   }

   public static void renderWithBlockEntity(BlockEntity blockEntity, float tickDelta, IVertexConsumerProvider vertexConsumerProvider, boolean textured) {
      vertexConsumerProvider.setOffset(blockEntity.getPos().getX(), blockEntity.getPos().getY(), blockEntity.getPos().getZ());
      if (textured) {
         ReviveClient.mc
            .getBlockRenderManager()
            .renderBlockAsEntity(blockEntity.getCachedState(), MATRICES, vertexConsumerProvider, 15728880, OverlayTexture.DEFAULT_UV);
      } else {
         render(blockEntity.getPos(), blockEntity.getCachedState(), vertexConsumerProvider);
      }

      BlockEntityRenderer<BlockEntity, BlockEntityRenderState> renderer = ReviveClient.mc.getBlockEntityRenderDispatcher().get(blockEntity);
      if (renderer != null && blockEntity.hasWorld() && blockEntity.getType().supports(blockEntity.getCachedState())) {
         provider = vertexConsumerProvider;
         BlockEntityRenderState state = renderer.createRenderState();
         renderer.updateRenderState(blockEntity, state, tickDelta, ReviveClient.mc.gameRenderer.getCamera().getCameraPos(), null);
         renderer.render(state, MATRICES, renderCommandQueue, ReviveClient.mc.gameRenderer.getEntityRenderStates().cameraRenderState);
         renderDispatcher.render();
         renderCommandQueue.onNextFrame();
         provider = null;
      }

      vertexConsumerProvider.setOffset(0, 0, 0);
   }

   public static void render(BlockPos pos, BlockState state, VertexConsumerProvider consumerProvider) {
      if (state.getRenderType() == BlockRenderType.MODEL) {
         VertexConsumer consumer = consumerProvider.getBuffer(RenderLayers.solid());
         BlockStateModel model = ReviveClient.mc.getBlockRenderManager().getModel(state);
         model.addParts(RANDOM, PARTS);
         Vec3d offset = state.getModelOffset(pos);
         float offsetX = (float)offset.x;
         float offsetY = (float)offset.y;
         float offsetZ = (float)offset.z;

         for (BlockModelPart part : PARTS) {
            for (Direction direction : DIRECTIONS) {
               List<BakedQuad> quads = part.getQuads(direction);
               if (!quads.isEmpty()) {
                  renderQuads(quads, offsetX, offsetY, offsetZ, consumer);
               }
            }

            List<BakedQuad> quads = part.getQuads(null);
            if (!quads.isEmpty()) {
               renderQuads(quads, offsetX, offsetY, offsetZ, consumer);
            }
         }

         PARTS.clear();
      }
   }

   private static void renderQuads(List<BakedQuad> quads, float offsetX, float offsetY, float offsetZ, VertexConsumer consumer) {
      for (BakedQuad quad : quads) {
         for (int j = 0; j < 4; j++) {
            Vector3fc vec = quad.getPosition(j);
            consumer.vertex(offsetX + vec.x(), offsetY + vec.y(), offsetZ + vec.z());
         }
      }
   }
}
