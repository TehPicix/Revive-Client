package revivedevelopment.reviveclient.utils.render;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import revivedevelopment.reviveclient.renderer.MeshBuilder;
import revivedevelopment.reviveclient.utils.render.color.Color;

public class TexturedMeshBuilderVertexConsumerProvider implements IVertexConsumerProvider {
   private final TexturedMeshBuilderVertexConsumerProvider.TexturedConsumer consumer;

   public TexturedMeshBuilderVertexConsumerProvider(MeshBuilder mesh) {
      this.consumer = new TexturedMeshBuilderVertexConsumerProvider.TexturedConsumer(mesh);
   }

   public VertexConsumer getBuffer(RenderLayer layer) {
      return this.consumer;
   }

   public void setColor(Color color) {
      this.consumer.fixedColor(color.r, color.g, color.b, color.a);
   }

   @Override
   public void setOffset(int offsetX, int offsetY, int offsetZ) {
      this.consumer.setOffset(offsetX, offsetY, offsetZ);
   }

   public void flush() {
      this.consumer.flush();
   }

   public static class TexturedConsumer implements VertexConsumer {
      private final MeshBuilder mesh;
      private int offsetX;
      private int offsetY;
      private int offsetZ;
      private final Color color = new Color();
      private boolean open;
      private double cx;
      private double cy;
      private double cz;
      private float cu;
      private float cv;
      private final double[] xs = new double[4];
      private final double[] ys = new double[4];
      private final double[] zs = new double[4];
      private final float[] us = new float[4];
      private final float[] vs = new float[4];
      private int n;

      public TexturedConsumer(MeshBuilder mesh) {
         this.mesh = mesh;
      }

      public void setOffset(int offsetX, int offsetY, int offsetZ) {
         this.offsetX = offsetX;
         this.offsetY = offsetY;
         this.offsetZ = offsetZ;
      }

      public void fixedColor(int red, int green, int blue, int alpha) {
         this.color.set(red, green, blue, alpha);
      }

      public void flush() {
         this.finalizeVertex();
         this.n = 0;
      }

      private void finalizeVertex() {
         if (this.open) {
            this.xs[this.n] = this.cx;
            this.ys[this.n] = this.cy;
            this.zs[this.n] = this.cz;
            this.us[this.n] = this.cu;
            this.vs[this.n] = this.cv;
            this.n++;
            this.open = false;
            if (this.n >= 4) {
               this.mesh.ensureQuadCapacity();
               this.mesh
                  .quad(
                     this.mesh.vec3(this.xs[0], this.ys[0], this.zs[0]).vec2(this.us[0], this.vs[0]).color(this.color).next(),
                     this.mesh.vec3(this.xs[1], this.ys[1], this.zs[1]).vec2(this.us[1], this.vs[1]).color(this.color).next(),
                     this.mesh.vec3(this.xs[2], this.ys[2], this.zs[2]).vec2(this.us[2], this.vs[2]).color(this.color).next(),
                     this.mesh.vec3(this.xs[3], this.ys[3], this.zs[3]).vec2(this.us[3], this.vs[3]).color(this.color).next()
                  );
               this.n = 0;
            }
         }
      }

      public VertexConsumer vertex(float x, float y, float z) {
         this.finalizeVertex();
         this.cx = (double)this.offsetX + x;
         this.cy = (double)this.offsetY + y;
         this.cz = (double)this.offsetZ + z;
         this.cu = 0.0F;
         this.cv = 0.0F;
         this.open = true;
         return this;
      }

      public VertexConsumer texture(float u, float v) {
         this.cu = u;
         this.cv = v;
         return this;
      }

      public VertexConsumer color(int red, int green, int blue, int alpha) {
         return this;
      }

      public VertexConsumer color(int argb) {
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
}
