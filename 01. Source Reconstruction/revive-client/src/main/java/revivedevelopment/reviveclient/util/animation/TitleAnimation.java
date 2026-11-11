package revivedevelopment.reviveclient.util.animation;

public class TitleAnimation {
   private long startTime;
   private long duration;
   private float startValue;
   private float endValue;
   private TitleAnimation.Easing easing;
   private float currentValue;
   private boolean paused = false;
   private long pauseTime = 0L;
   private boolean completed = false;

   public TitleAnimation(long duration, float startValue, float endValue, TitleAnimation.Easing easing) {
      this.startTime = System.currentTimeMillis();
      this.duration = duration;
      this.startValue = startValue;
      this.endValue = endValue;
      this.easing = easing;
      this.currentValue = startValue;
   }

   public void reset() {
      this.startTime = System.currentTimeMillis();
      this.paused = false;
      this.pauseTime = 0L;
      this.completed = false;
   }

   public void reset(float startValue, float endValue) {
      this.startTime = System.currentTimeMillis();
      this.startValue = startValue;
      this.endValue = endValue;
      this.currentValue = startValue;
      this.paused = false;
      this.pauseTime = 0L;
      this.completed = false;
   }

   public void update(float delta) {
      if (!this.paused) {
         long elapsed = System.currentTimeMillis() - this.startTime;
         float progress = Math.max(0.0F, Math.min(1.0F, (float)elapsed / (float)this.duration));
         this.currentValue = this.startValue + (this.endValue - this.startValue) * this.easing.ease(progress);
         if (elapsed >= this.duration && !this.completed) {
            this.completed = true;
         }
      }
   }

   public float getValue() {
      if (!this.paused) {
         long elapsed = System.currentTimeMillis() - this.startTime;
         float progress = Math.max(0.0F, Math.min(1.0F, (float)elapsed / (float)this.duration));
         return this.startValue + (this.endValue - this.startValue) * this.easing.ease(progress);
      } else {
         return this.currentValue;
      }
   }

   public boolean isDone() {
      return !this.paused && System.currentTimeMillis() - this.startTime >= this.duration;
   }

   public float getProgress() {
      if (this.paused) {
         return (this.currentValue - this.startValue) / (this.endValue - this.startValue);
      } else {
         long elapsed = System.currentTimeMillis() - this.startTime;
         return Math.max(0.0F, Math.min(1.0F, (float)elapsed / (float)this.duration));
      }
   }

   public static int rgba(int r, int g, int b, int a) {
      return (a & 0xFF) << 24 | (r & 0xFF) << 16 | (g & 0xFF) << 8 | b & 0xFF;
   }

   public static enum Easing {
      LINEAR(p -> p),
      EASE_IN_QUAD(p -> p * p),
      EASE_OUT_QUAD(p -> p * (2.0F - p)),
      EASE_OUT_CUBIC(p -> 1.0F - (float)Math.pow(1.0F - p, 3.0)),
      EASE_IN_CUBIC(p -> (float)Math.pow(p, 3.0)),
      EASE_OUT_EXPO(p -> p >= 1.0F ? 1.0F : 1.0F - (float)Math.pow(2.0, -10.0F * p)),
      EASE_IN_EXPO(p -> p <= 0.0F ? 0.0F : (float)Math.pow(2.0, 10.0F * p - 10.0F)),
      ELASTIC(p -> {
         float c4 = (float) (Math.PI * 2.0 / 3.0);
         return p <= 0.0F ? 0.0F : (p >= 1.0F ? 1.0F : (float)(-Math.pow(2.0, 10.0F * p - 10.0F) * Math.sin((p * 10.0F - 10.75) * c4)));
      }),
      BOUNCE(p -> {
         float n1 = 7.5625F;
         float d1 = 2.75F;
         if (p < 1.0F / d1) {
            return n1 * p * p;
         } else if (p < 2.0F / d1) {
            p -= 1.5F / d1;
            return n1 * p * p + 0.75F;
         } else if (p < 2.5 / d1) {
            p -= 2.25F / d1;
            return n1 * p * p + 0.9375F;
         } else {
            p -= 2.625F / d1;
            return n1 * p * p + 0.984375F;
         }
      });

      private final TitleAnimation.Easing.EasingFunction function;

      private Easing(TitleAnimation.Easing.EasingFunction function) {
         this.function = function;
      }

      public float ease(float progress) {
         return this.function.ease(progress);
      }

      @FunctionalInterface
      private interface EasingFunction {
         float ease(float var1);
      }
   }
}
