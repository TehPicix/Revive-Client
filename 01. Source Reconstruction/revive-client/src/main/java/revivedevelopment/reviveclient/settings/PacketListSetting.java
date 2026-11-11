package revivedevelopment.reviveclient.settings;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.network.packet.Packet;
import revivedevelopment.reviveclient.utils.network.PacketUtils;

public class PacketListSetting extends Setting<Set<Class<? extends Packet<?>>>> {
   public final Predicate<Class<? extends Packet<?>>> filter;
   private static List<String> suggestions;

   public PacketListSetting(
      String name,
      String description,
      Set<Class<? extends Packet<?>>> defaultValue,
      Consumer<Set<Class<? extends Packet<?>>>> onChanged,
      Consumer<Setting<Set<Class<? extends Packet<?>>>>> onModuleActivated,
      Predicate<Class<? extends Packet<?>>> filter,
      IVisible visible
   ) {
      super(name, description, defaultValue, onChanged, onModuleActivated, visible);
      this.filter = filter;
   }

   @Override
   public void resetImpl() {
      this.value = new ObjectOpenHashSet(this.defaultValue);
   }

   protected Set<Class<? extends Packet<?>>> parseImpl(String str) {
      String[] values = str.split(",");
      Set<Class<? extends Packet<?>>> packets = new ObjectOpenHashSet(values.length);

      try {
         for (String value : values) {
            Class<? extends Packet<?>> packet = PacketUtils.getPacket(value.trim());
            if (packet != null && (this.filter == null || this.filter.test(packet))) {
               packets.add(packet);
            }
         }
      } catch (Exception var9) {
      }

      return packets;
   }

   protected boolean isValueValid(Set<Class<? extends Packet<?>>> value) {
      return true;
   }

   @Override
   public List<String> getSuggestions() {
      if (suggestions == null) {
         suggestions = new ArrayList<>(PacketUtils.getC2SPackets().size() + PacketUtils.getS2CPackets().size());

         for (Class<? extends Packet<?>> packet : PacketUtils.getC2SPackets()) {
            suggestions.add(PacketUtils.getName(packet));
         }

         for (Class<? extends Packet<?>> packet : PacketUtils.getS2CPackets()) {
            suggestions.add(PacketUtils.getName(packet));
         }
      }

      return suggestions;
   }

   @Override
   public NbtCompound save(NbtCompound tag) {
      NbtList valueTag = new NbtList();

      for (Class<? extends Packet<?>> packet : this.get()) {
         valueTag.add(NbtString.of(PacketUtils.getName(packet)));
      }

      tag.put("value", valueTag);
      return tag;
   }

   public Set<Class<? extends Packet<?>>> load(NbtCompound tag) {
      this.get().clear();
      NbtElement valueTag = tag.get("value");
      if (valueTag instanceof NbtList) {
         for (NbtElement t : (NbtList)valueTag) {
            Class<? extends Packet<?>> packet = PacketUtils.getPacket(t.asString().orElse(""));
            if (packet != null && (this.filter == null || this.filter.test(packet))) {
               this.get().add(packet);
            }
         }
      }

      return this.get();
   }

   public static class Builder extends Setting.SettingBuilder<PacketListSetting.Builder, Set<Class<? extends Packet<?>>>, PacketListSetting> {
      private Predicate<Class<? extends Packet<?>>> filter;

      public Builder() {
         super(new ObjectOpenHashSet(0));
      }

      public PacketListSetting.Builder filter(Predicate<Class<? extends Packet<?>>> filter) {
         this.filter = filter;
         return this;
      }

      public PacketListSetting build() {
         return new PacketListSetting(this.name, this.description, this.defaultValue, this.onChanged, this.onModuleActivated, this.filter, this.visible);
      }
   }
}
