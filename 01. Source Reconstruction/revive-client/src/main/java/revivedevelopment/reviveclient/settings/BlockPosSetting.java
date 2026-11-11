package revivedevelopment.reviveclient.settings;

import java.util.List;
import java.util.function.Consumer;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;

public class BlockPosSetting extends Setting<BlockPos> {
   public BlockPosSetting(
      String name, String description, BlockPos defaultValue, Consumer<BlockPos> onChanged, Consumer<Setting<BlockPos>> onModuleActivated, IVisible visible
   ) {
      super(name, description, defaultValue, onChanged, onModuleActivated, visible);
   }

   protected BlockPos parseImpl(String str) {
      List<String> values = List.of(str.split(","));
      if (values.size() != 3) {
         return null;
      } else {
         BlockPos bp = null;

         try {
            bp = new BlockPos(Integer.parseInt(values.get(0)), Integer.parseInt(values.get(1)), Integer.parseInt(values.get(2)));
         } catch (NumberFormatException var5) {
         }

         return bp;
      }
   }

   protected boolean isValueValid(BlockPos value) {
      return true;
   }

   @Override
   protected NbtCompound save(NbtCompound tag) {
      tag.putIntArray("value", new int[]{this.value.getX(), this.value.getY(), this.value.getZ()});
      return tag;
   }

   protected BlockPos load(NbtCompound tag) {
      if (tag.getIntArray("value").isPresent()) {
         int[] value = (int[])tag.getIntArray("value").get();
         this.set(new BlockPos(value[0], value[1], value[2]));
      }

      return this.get();
   }

   public static class Builder extends Setting.SettingBuilder<BlockPosSetting.Builder, BlockPos, BlockPosSetting> {
      public Builder() {
         super(new BlockPos(0, 0, 0));
      }

      public BlockPosSetting build() {
         return new BlockPosSetting(this.name, this.description, this.defaultValue, this.onChanged, this.onModuleActivated, this.visible);
      }
   }
}
