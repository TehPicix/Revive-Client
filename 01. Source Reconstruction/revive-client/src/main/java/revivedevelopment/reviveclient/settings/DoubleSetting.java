package revivedevelopment.reviveclient.settings;

import java.util.function.Consumer;
import net.minecraft.nbt.NbtCompound;

public class DoubleSetting extends Setting<Double> {
   public final double min;
   public final double max;
   public final double sliderMin;
   public final double sliderMax;
   public final boolean onSliderRelease;
   public final int decimalPlaces;
   public final boolean noSlider;

   private DoubleSetting(
      String name,
      String description,
      double defaultValue,
      Consumer<Double> onChanged,
      Consumer<Setting<Double>> onModuleActivated,
      IVisible visible,
      double min,
      double max,
      double sliderMin,
      double sliderMax,
      boolean onSliderRelease,
      int decimalPlaces,
      boolean noSlider
   ) {
      super(name, description, defaultValue, onChanged, onModuleActivated, visible);
      this.min = min;
      this.max = max;
      this.sliderMin = sliderMin;
      this.sliderMax = sliderMax;
      this.decimalPlaces = decimalPlaces;
      this.onSliderRelease = onSliderRelease;
      this.noSlider = noSlider;
   }

   protected Double parseImpl(String str) {
      try {
         return Double.parseDouble(str.trim());
      } catch (NumberFormatException var3) {
         return null;
      }
   }

   protected boolean isValueValid(Double value) {
      return value >= this.min && value <= this.max;
   }

   @Override
   protected NbtCompound save(NbtCompound tag) {
      tag.putDouble("value", this.get());
      return tag;
   }

   public Double load(NbtCompound tag) {
      this.set(tag.getDouble("value", 0.0));
      return this.get();
   }

   public static class Builder extends Setting.SettingBuilder<DoubleSetting.Builder, Double, DoubleSetting> {
      public double min = Double.NEGATIVE_INFINITY;
      public double max = Double.POSITIVE_INFINITY;
      public double sliderMin = 0.0;
      public double sliderMax = 10.0;
      public boolean onSliderRelease = false;
      public int decimalPlaces = 3;
      public boolean noSlider = false;

      public Builder() {
         super(0.0);
      }

      public DoubleSetting.Builder defaultValue(double defaultValue) {
         this.defaultValue = defaultValue;
         return this;
      }

      public DoubleSetting.Builder min(double min) {
         this.min = min;
         return this;
      }

      public DoubleSetting.Builder max(double max) {
         this.max = max;
         return this;
      }

      public DoubleSetting.Builder range(double min, double max) {
         this.min = Math.min(min, max);
         this.max = Math.max(min, max);
         return this;
      }

      public DoubleSetting.Builder sliderMin(double min) {
         this.sliderMin = min;
         return this;
      }

      public DoubleSetting.Builder sliderMax(double max) {
         this.sliderMax = max;
         return this;
      }

      public DoubleSetting.Builder sliderRange(double min, double max) {
         this.sliderMin = min;
         this.sliderMax = max;
         return this;
      }

      public DoubleSetting.Builder onSliderRelease() {
         this.onSliderRelease = true;
         return this;
      }

      public DoubleSetting.Builder decimalPlaces(int decimalPlaces) {
         this.decimalPlaces = decimalPlaces;
         return this;
      }

      public DoubleSetting.Builder noSlider() {
         this.noSlider = true;
         return this;
      }

      public DoubleSetting build() {
         return new DoubleSetting(
            this.name,
            this.description,
            this.defaultValue,
            this.onChanged,
            this.onModuleActivated,
            this.visible,
            this.min,
            this.max,
            Math.max(this.sliderMin, this.min),
            Math.min(this.sliderMax, this.max),
            this.onSliderRelease,
            this.decimalPlaces,
            this.noSlider
         );
      }
   }
}
