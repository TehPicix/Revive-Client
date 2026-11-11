package revivedevelopment.reviveclient.systems.friends;

import com.mojang.util.UndashedUuid;
import java.net.http.HttpResponse;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import org.jetbrains.annotations.NotNull;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.utils.misc.ISerializable;
import revivedevelopment.reviveclient.utils.network.FailedHttpResponse;
import revivedevelopment.reviveclient.utils.network.Http;
import revivedevelopment.reviveclient.utils.render.PlayerHeadTexture;
import revivedevelopment.reviveclient.utils.render.PlayerHeadUtils;

public class Friend implements ISerializable<Friend>, Comparable<Friend> {
   public volatile String name;
   @Nullable
   private volatile UUID id;
   @Nullable
   private volatile PlayerHeadTexture headTexture;
   private volatile boolean updating;

   public Friend(String name, @Nullable UUID id) {
      this.name = name;
      this.id = id;
      this.headTexture = null;
   }

   public Friend(PlayerEntity player) {
      this(player.getName().getString(), player.getUuid());
   }

   public Friend(String name) {
      this(name, null);
   }

   public String getName() {
      return this.name;
   }

   public PlayerHeadTexture getHead() {
      return this.headTexture != null ? this.headTexture : PlayerHeadUtils.STEVE_HEAD;
   }

   public void updateInfo() {
      this.updating = true;
      HttpResponse<Friend.APIResponse> res = null;
      if (this.id != null) {
         res = Http.get("https://sessionserver.mojang.com/session/minecraft/profile/" + UndashedUuid.toString(this.id))
            .exceptionHandler(e -> ReviveClient.LOG.error("Error while trying to connect session server for friend '{}'", this.name))
            .sendJsonResponse(Friend.APIResponse.class);
      }

      if (res == null || res.statusCode() != 200) {
         res = Http.get("https://api.mojang.com/users/profiles/minecraft/" + this.name)
            .exceptionHandler(e -> ReviveClient.LOG.error("Error while trying to update info for friend '{}'", this.name))
            .sendJsonResponse(Friend.APIResponse.class);
      }

      if (res != null && res.statusCode() == 200 && res.body() != null && res.body().name != null && res.body().id != null) {
         this.name = res.body().name;
         this.id = UndashedUuid.fromStringLenient(res.body().id);
         ReviveClient.mc.execute(() -> {
            PlayerHeadTexture old = this.headTexture;
            this.headTexture = PlayerHeadUtils.fetchHead(this.id);
            if (old != null && old != this.headTexture && old != PlayerHeadUtils.STEVE_HEAD) {
               old.close();
            }
         });
      } else if (!(res instanceof FailedHttpResponse)) {
         this.id = null;
      }

      this.updating = false;
   }

   public void releaseHead() {
      PlayerHeadTexture old = this.headTexture;
      this.headTexture = null;
      if (old != null && old != PlayerHeadUtils.STEVE_HEAD) {
         ReviveClient.mc.execute(old::close);
      }
   }

   public boolean headTextureNeedsUpdate() {
      return !this.updating && this.headTexture == null;
   }

   @Override
   public NbtCompound toTag() {
      NbtCompound tag = new NbtCompound();
      tag.putString("name", this.name);
      if (this.id != null) {
         tag.putString("id", UndashedUuid.toString(this.id));
      }

      return tag;
   }

   public Friend fromTag(NbtCompound tag) {
      return this;
   }

   @Override
   public boolean equals(Object o) {
      if (this == o) {
         return true;
      } else if (o != null && this.getClass() == o.getClass()) {
         Friend friend = (Friend)o;
         return Objects.equals(this.name, friend.name);
      } else {
         return false;
      }
   }

   @Override
   public int hashCode() {
      return Objects.hash(this.name);
   }

   public int compareTo(@NotNull Friend friend) {
      return this.name.compareToIgnoreCase(friend.name);
   }

   private static class APIResponse {
      String name;
      String id;
   }
}
