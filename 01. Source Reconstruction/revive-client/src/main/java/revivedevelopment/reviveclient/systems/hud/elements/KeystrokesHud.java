package revivedevelopment.reviveclient.systems.hud.elements;

import net.minecraft.client.option.KeyBinding;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.renderer.Renderer2D;
import revivedevelopment.reviveclient.renderer.text.TextRenderer;
import revivedevelopment.reviveclient.settings.DoubleSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.systems.hud.Hud;
import revivedevelopment.reviveclient.systems.hud.HudElement;
import revivedevelopment.reviveclient.systems.hud.HudElementInfo;
import revivedevelopment.reviveclient.systems.hud.HudRenderer;
import revivedevelopment.reviveclient.systems.hud.HudStyle;
import revivedevelopment.reviveclient.utils.Utils;
import revivedevelopment.reviveclient.utils.misc.input.Input;

public class KeystrokesHud extends HudElement {
   public static final HudElementInfo<KeystrokesHud> INFO = new HudElementInfo<>(Hud.GROUP, "keystrokes", "Displays your key presses.", KeystrokesHud::new);
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Double> scale = this.sgGeneral
      .add(new DoubleSetting.Builder().name("scale").description("Scale of the keystrokes display.").defaultValue(1.0).min(0.5).sliderRange(0.5, 3.0).build());

   public KeystrokesHud() {
      super(INFO);
   }

   @Override
   public void render(HudRenderer renderer) {
      double scale = this.scale.get();
      double keySize = 30.0 * scale;
      double gap = 2.0 * scale;
      renderer.post(
         () -> {
            double x = this.x;
            double y = this.y;
            this.drawKey(
               renderer, x + keySize + gap, y, keySize, keyLabel(ReviveClient.mc.options.forwardKey), Input.isPressed(ReviveClient.mc.options.forwardKey)
            );
            this.drawKey(renderer, x, y + keySize + gap, keySize, keyLabel(ReviveClient.mc.options.leftKey), Input.isPressed(ReviveClient.mc.options.leftKey));
            this.drawKey(
               renderer,
               x + keySize + gap,
               y + keySize + gap,
               keySize,
               keyLabel(ReviveClient.mc.options.backKey),
               Input.isPressed(ReviveClient.mc.options.backKey)
            );
            this.drawKey(
               renderer,
               x + (keySize + gap) * 2.0,
               y + keySize + gap,
               keySize,
               keyLabel(ReviveClient.mc.options.rightKey),
               Input.isPressed(ReviveClient.mc.options.rightKey)
            );
            this.drawKey(
               renderer,
               x,
               y + (keySize + gap) * 2.0,
               keySize * 3.0 + gap * 2.0,
               keyLabel(ReviveClient.mc.options.jumpKey),
               Input.isPressed(ReviveClient.mc.options.jumpKey)
            );
            boolean lmbPressed = Input.isButtonPressed(0);
            this.drawKey(renderer, x, y + (keySize + gap) * 3.0, (keySize * 3.0 + gap * 2.0) / 2.0 - gap / 2.0, "LMB", lmbPressed);
            boolean rmbPressed = Input.isButtonPressed(1);
            this.drawKey(
               renderer,
               x + (keySize * 3.0 + gap * 2.0) / 2.0 + gap / 2.0,
               y + (keySize + gap) * 3.0,
               (keySize * 3.0 + gap * 2.0) / 2.0 - gap / 2.0,
               "RMB",
               rmbPressed
            );
         }
      );
      this.setSize(keySize * 3.0 + gap * 2.0, keySize * 4.0 + gap * 3.0);
   }

   private static String keyLabel(KeyBinding bind) {
      int key = Input.getKey(bind);
      return key == -1 ? "?" : Utils.getKeyName(key);
   }

   private void drawKey(HudRenderer renderer, double x, double y, double width, String text, boolean pressed) {
      double keyHeight = 30.0 * this.scale.get();
      Renderer2D.color().begin();
      Renderer2D.color().quad(x, y, width, keyHeight, pressed ? HudStyle.ACCENT : HudStyle.BG_MODULE);
      Renderer2D.color().render();
      TextRenderer textRenderer = TextRenderer.get();
      textRenderer.begin(0.4 * this.scale.get(), false, true);
      double textWidth = textRenderer.getWidth(text);
      double textHeight = textRenderer.getHeight();
      double textX = x + (width - textWidth) / 2.0;
      double textY = y + (keyHeight - textHeight) / 2.0;
      textRenderer.render(text, textX, textY, HudStyle.TEXT_PRIMARY);
      textRenderer.end();
   }
}
