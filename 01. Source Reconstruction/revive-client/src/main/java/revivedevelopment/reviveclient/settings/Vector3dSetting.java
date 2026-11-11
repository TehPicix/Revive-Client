package revivedevelopment.reviveclient.settings;

import java.util.function.Consumer;
import net.minecraft.nbt.NbtCompound;
import org.joml.Vector3d;
import org.joml.Vector3dc;

public class Vector3dSetting extends Setting<Vector3d> {
   public final double min;
   public final double max;
   public final double sliderMin;
   public final double sliderMax;
   public final boolean onSliderRelease;
   public final int decimalPlaces;
   public final boolean noSlider;

   public Vector3dSetting(
      String name,
      String description,
      Vector3d defaultValue,
      Consumer<Vector3d> onChanged,
      Consumer<Setting<Vector3d>> onModuleActivated,
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

   public boolean set(double x, double y, double z) {
      this.value.set(x, y, z);
      return super.set(this.value);
   }

   @Override
   protected void resetImpl() {
      if (this.value == null) {
         this.value = new Vector3d();
      }

      this.value.set((Vector3dc)this.defaultValue);
   }

   protected Vector3d parseImpl(String str) {
      try {
         String[] strs = str.split(" ");
         return new Vector3d(Double.parseDouble(strs[0]), Double.parseDouble(strs[1]), Double.parseDouble(strs[2]));
      } catch (NumberFormatException | IndexOutOfBoundsException var3) {
         return null;
      }
   }

   protected boolean isValueValid(Vector3d value) {
      return value.x >= this.min && value.x <= this.max && value.y >= this.min && value.y <= this.max && value.z >= this.min && value.z <= this.max;
   }

   @Override
   protected NbtCompound save(NbtCompound tag) {
      NbtCompound valueTag = new NbtCompound();
      valueTag.putDouble("x", this.get().x);
      valueTag.putDouble("y", this.get().y);
      valueTag.putDouble("z", this.get().z);
      tag.put("value", valueTag);
      return tag;
   }

   protected Vector3d load(NbtCompound tag) {
      if (tag.getCompound("value").isEmpty()) {
         return this.get();
      } else {
         NbtCompound valueTag = (NbtCompound)tag.getCompound("value").get();
         this.set(valueTag.getDouble("x", 0.0), valueTag.getDouble("y", 0.0), valueTag.getDouble("z", 0.0));
         return this.get();
      }
   }

   public static class Builder extends Setting.SettingBuilder<Vector3dSetting.Builder, Vector3d, Vector3dSetting> {
      public double min = Double.NEGATIVE_INFINITY;
      public double max = Double.POSITIVE_INFINITY;
      public double sliderMin = 0.0;
      public double sliderMax = 10.0;
      public boolean onSliderRelease = false;
      public int decimalPlaces = 3;
      public boolean noSlider = false;

      public Builder() {
         super(new Vector3d());
      }

      public Vector3dSetting.Builder defaultValue(Vector3d defaultValue) {
         this.defaultValue.set(defaultValue);
         return this;
      }

      public Vector3dSetting.Builder defaultValue(double x, double y, double z) {
         this.defaultValue.set(x, y, z);
         return this;
      }

      public Vector3dSetting.Builder min(double min) {
         this.min = min;
         return this;
      }

      public Vector3dSetting.Builder max(double max) {
         this.max = max;
         return this;
      }

      public Vector3dSetting.Builder range(double min, double max) {
         this.min = Math.min(min, max);
         this.max = Math.max(min, max);
         return this;
      }

      public Vector3dSetting.Builder sliderMin(double min) {
         this.sliderMin = min;
         return this;
      }

      public Vector3dSetting.Builder sliderMax(double max) {
         this.sliderMax = max;
         return this;
      }

      public Vector3dSetting.Builder sliderRange(double min, double max) {
         this.sliderMin = min;
         this.sliderMax = max;
         return this;
      }

      public Vector3dSetting.Builder onSliderRelease() {
         this.onSliderRelease = true;
         return this;
      }

      public Vector3dSetting.Builder decimalPlaces(int decimalPlaces) {
         this.decimalPlaces = decimalPlaces;
         return this;
      }

      public Vector3dSetting.Builder noSlider() {
         this.noSlider = true;
         return this;
      }

      public Vector3dSetting build() {
         return new Vector3dSetting(
            this.name,
            this.description,
            this.defaultValue,
            this.onChanged,
            this.onModuleActivated,
            this.visible,
            this.min,
            this.max,
            this.sliderMin,
            this.sliderMax,
            this.onSliderRelease,
            this.decimalPlaces,
            this.noSlider
         );
      }
   }
}
