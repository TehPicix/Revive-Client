package revivedevelopment.reviveclient.systems.friends;

import com.mojang.util.UndashedUuid;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import org.jetbrains.annotations.NotNull;
import revivedevelopment.reviveclient.systems.System;
import revivedevelopment.reviveclient.systems.Systems;
import revivedevelopment.reviveclient.utils.misc.NbtUtils;
import revivedevelopment.reviveclient.utils.network.ReviveExecutor;

public class Friends extends System<Friends> implements Iterable<Friend> {
   private final List<Friend> friends = new ArrayList<>();

   public Friends() {
      super("friends");
   }

   public static Friends get() {
      return Systems.get(Friends.class);
   }

   public boolean add(Friend friend) {
      if (friend.name.isEmpty() || friend.name.contains(" ")) {
         return false;
      } else if (this.get(friend.name) != null) {
         return false;
      } else {
         this.friends.add(friend);
         this.save();
         return true;
      }
   }

   public boolean remove(Friend friend) {
      int index = this.friends.indexOf(friend);
      if (index >= 0) {
         this.friends.remove(index).releaseHead();
         this.save();
         return true;
      } else {
         return false;
      }
   }

   public Friend get(String name) {
      for (Friend friend : this.friends) {
         if (friend.name.equalsIgnoreCase(name)) {
            return friend;
         }
      }

      return null;
   }

   public Friend get(PlayerEntity player) {
      return this.get(player.getName().getString());
   }

   public Friend get(PlayerListEntry player) {
      return this.get(player.getProfile().name());
   }

   public boolean isFriend(PlayerEntity player) {
      return player != null && this.get(player) != null;
   }

   public boolean isFriend(PlayerListEntry player) {
      return this.get(player) != null;
   }

   public boolean shouldAttack(PlayerEntity player) {
      return !this.isFriend(player);
   }

   public int count() {
      return this.friends.size();
   }

   public boolean isEmpty() {
      return this.friends.isEmpty();
   }

   @NotNull
   @Override
   public Iterator<Friend> iterator() {
      return this.friends.iterator();
   }

   @Override
   public NbtCompound toTag() {
      NbtCompound tag = new NbtCompound();
      tag.put("friends", NbtUtils.listToTag(this.friends));
      return tag;
   }

   public Friends fromTag(NbtCompound tag) {
      for (Friend friend : this.friends) {
         friend.releaseHead();
      }

      this.friends.clear();

      for (NbtElement itemTag : tag.getListOrEmpty("friends")) {
         NbtCompound friendTag = (NbtCompound)itemTag;
         if (friendTag.contains("name")) {
            String name = friendTag.getString("name", "");
            if (this.get(name) == null) {
               String uuid = friendTag.getString("id", "");
               Friend friend = !uuid.isBlank() ? new Friend(name, UndashedUuid.fromStringLenient(uuid)) : new Friend(name);
               this.friends.add(friend);
            }
         }
      }

      Collections.sort(this.friends);
      ReviveExecutor.execute(() -> this.friends.forEach(Friend::updateInfo));
      return this;
   }
}
