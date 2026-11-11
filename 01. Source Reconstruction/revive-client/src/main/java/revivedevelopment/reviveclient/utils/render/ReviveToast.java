package revivedevelopment.reviveclient.utils.render;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.toast.Toast;
import net.minecraft.client.toast.ToastManager;
import net.minecraft.client.toast.Toast.Visibility;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.utils.render.color.Color;

public class ReviveToast implements Toast {
   public static final int TITLE_COLOR = Color.fromRGBA(145, 61, 226, 255);
   public static final int TEXT_COLOR = Color.fromRGBA(220, 220, 220, 255);
   private static final Identifier TEXTURE = Identifier.of("toast/advancement");
   private ItemStack icon;
   private Text title;
   private Text text;
   private boolean justUpdated = true;
   private boolean playedSound;
   private long start;
   private long duration;
   private Visibility visibility = Visibility.HIDE;

   public ReviveToast(@Nullable Item item, @NotNull String title, @Nullable String text, long duration) {
      this.icon = item != null ? item.getDefaultStack() : null;
      this.title = Text.literal(title).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(TITLE_COLOR)));
      this.text = text != null ? Text.literal(text).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(TEXT_COLOR))) : null;
      this.duration = duration;
   }

   public ReviveToast(@Nullable Item item, @NotNull String title, @Nullable String text) {
      this.icon = item != null ? item.getDefaultStack() : null;
      this.title = Text.literal(title).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(TITLE_COLOR)));
      this.text = text != null ? Text.literal(text).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(TEXT_COLOR))) : null;
      this.duration = 6000L;
   }

   public Visibility getVisibility() {
      return this.visibility;
   }

   public void update(ToastManager manager, long time) {
      if (this.justUpdated) {
         this.start = time;
         this.justUpdated = false;
      }

      this.visibility = time - this.start >= this.duration ? Visibility.HIDE : Visibility.SHOW;
      if (!this.playedSound) {
         ReviveClient.mc.getSoundManager().play(this.getSound());
         this.playedSound = true;
      }
   }

   public void draw(DrawContext context, TextRenderer textRenderer, long startTime) {
      context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, TEXTURE, 0, 0, this.getWidth(), this.getHeight());
      int x = this.icon != null ? 28 : 12;
      int titleY = 12;
      if (this.text != null) {
         context.drawText(ReviveClient.mc.textRenderer, this.title, x, 18, TITLE_COLOR, false);
         titleY = 7;
      }

      context.drawText(ReviveClient.mc.textRenderer, this.title, x, titleY, TITLE_COLOR, false);
      if (this.icon != null) {
         context.drawItem(this.icon, 8, 8);
      }
   }

   public void setIcon(@Nullable Item item) {
      this.icon = item != null ? item.getDefaultStack() : null;
      this.justUpdated = true;
   }

   public void setTitle(@NotNull String title) {
      this.title = Text.literal(title).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(TITLE_COLOR)));
      this.justUpdated = true;
   }

   public void setText(@Nullable String text) {
      this.text = text != null ? Text.literal(text).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(TEXT_COLOR))) : null;
      this.justUpdated = true;
   }

   public void setDuration(long duration) {
      this.duration = duration;
      this.justUpdated = true;
   }

   public SoundInstance getSound() {
      return PositionedSoundInstance.master((SoundEvent)SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), 1.2F, 1.0F);
   }
}
