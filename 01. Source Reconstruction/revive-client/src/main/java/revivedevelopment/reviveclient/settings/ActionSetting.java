package revivedevelopment.reviveclient.settings;

import java.util.List;
import net.minecraft.nbt.NbtCompound;

public class ActionSetting extends Setting<Boolean> {
   public final Runnable action;
   public final String buttonLabel;

   private ActionSetting(String name, String description, Runnable action, String buttonLabel, IVisible visible) {
      super(name, description, false, null, null, visible);
      this.action = action;
      this.buttonLabel = buttonLabel;
   }

   protected Boolean parseImpl(String str) {
      return false;
   }

   protected boolean isValueValid(Boolean value) {
      return true;
   }

   @Override
   public List<String> getSuggestions() {
      return List.of();
   }

   @Override
   public NbtCompound save(NbtCompound tag) {
      return tag;
   }

   public Boolean load(NbtCompound tag) {
      return false;
   }

   public static class Builder extends Setting.SettingBuilder<ActionSetting.Builder, Boolean, ActionSetting> {
      private Runnable action = () -> {};
      private String buttonLabel = "open";

      public Builder() {
         super(false);
      }

      public ActionSetting.Builder action(Runnable action) {
         this.action = action;
         return this;
      }

      public ActionSetting.Builder buttonLabel(String label) {
         this.buttonLabel = label;
         return this;
      }

      public ActionSetting build() {
         return new ActionSetting(this.name, this.description, this.action, this.buttonLabel, this.visible);
      }
   }
}
