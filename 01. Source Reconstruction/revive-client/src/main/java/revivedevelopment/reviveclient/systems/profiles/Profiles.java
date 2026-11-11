package revivedevelopment.reviveclient.systems.profiles;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.nbt.NbtCompound;
import org.jetbrains.annotations.NotNull;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.events.game.GameJoinedEvent;
import revivedevelopment.reviveclient.systems.System;
import revivedevelopment.reviveclient.systems.Systems;
import revivedevelopment.reviveclient.utils.Utils;
import revivedevelopment.reviveclient.utils.misc.NbtUtils;

public class Profiles extends System<Profiles> implements Iterable<Profile> {
   public static final File FOLDER = new File(ReviveClient.FOLDER, "profiles");
   private List<Profile> profiles = new ArrayList<>();

   public Profiles() {
      super("profiles");
   }

   public static Profiles get() {
      return Systems.get(Profiles.class);
   }

   public void add(Profile profile) {
      if (!this.profiles.contains(profile)) {
         this.profiles.add(profile);
      }

      profile.save();
      this.save();
   }

   public void remove(Profile profile) {
      if (this.profiles.remove(profile)) {
         profile.delete();
      }

      this.save();
   }

   public Profile get(String name) {
      for (Profile profile : this) {
         if (profile.name.get().equalsIgnoreCase(name)) {
            return profile;
         }
      }

      return null;
   }

   public List<Profile> getAll() {
      return this.profiles;
   }

   @Override
   public File getFile() {
      return new File(FOLDER, "profiles.nbt");
   }

   @EventHandler
   private void onGameJoined(GameJoinedEvent event) {
      for (Profile profile : this) {
         if (profile.loadOnJoin.get().contains(Utils.getWorldName())) {
            profile.load();
         }
      }
   }

   public boolean isEmpty() {
      return this.profiles.isEmpty();
   }

   @NotNull
   @Override
   public Iterator<Profile> iterator() {
      return this.profiles.iterator();
   }

   @Override
   public NbtCompound toTag() {
      NbtCompound tag = new NbtCompound();
      tag.put("profiles", NbtUtils.listToTag(this.profiles));
      return tag;
   }

   public Profiles fromTag(NbtCompound tag) {
      this.profiles = NbtUtils.listFromTag(tag.getListOrEmpty("profiles"), Profile::new);
      return this;
   }
}
