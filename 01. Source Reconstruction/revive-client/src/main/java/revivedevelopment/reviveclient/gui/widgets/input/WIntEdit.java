package revivedevelopment.reviveclient.gui.widgets.input;

import revivedevelopment.reviveclient.gui.widgets.containers.WHorizontalList;

public class WIntEdit extends WHorizontalList {
   private int value;
   public final int min;
   public final int max;
   private final int sliderMin;
   private final int sliderMax;
   public boolean noSlider = false;
   public boolean small = false;
   public Runnable action;
   public Runnable actionOnRelease;
   private WTextBox textBox;
   private WSlider slider;

   public WIntEdit(int value, int min, int max, int sliderMin, int sliderMax, boolean noSlider) {
      this.value = value;
      this.min = min;
      this.max = max;
      this.sliderMin = sliderMin;
      this.sliderMax = sliderMax;
      if (noSlider || sliderMin == 0 && sliderMax == 0) {
         this.noSlider = true;
      }
   }

   @Override
   public void init() {
      this.textBox = this.add(this.theme.textBox(Integer.toString(this.value), this::filter)).minWidth(75.0).widget();
      if (this.noSlider) {
         this.add(this.theme.button("+")).widget().action = () -> this.setButton(this.get() + 1);
         this.add(this.theme.button("-")).widget().action = () -> this.setButton(this.get() - 1);
      } else {
         this.slider = this.add(this.theme.slider(this.value, this.sliderMin, this.sliderMax))
            .minWidth(this.small ? 125.0 - this.spacing : 200.0)
            .centerY()
            .expandX()
            .widget();
      }

      this.textBox.actionOnUnfocused = () -> {
         int lastValue = this.value;
         if (this.textBox.get().isEmpty()) {
            this.value = 0;
         } else if (this.textBox.get().equals("-")) {
            this.value = 0;
         } else {
            try {
               this.value = Integer.parseInt(this.textBox.get());
            } catch (NumberFormatException var3) {
            }
         }

         if (this.slider != null) {
            this.slider.set(this.value);
         }

         if (this.value != lastValue) {
            if (this.action != null) {
               this.action.run();
            }

            if (this.actionOnRelease != null) {
               this.actionOnRelease.run();
            }
         }
      };
      if (this.slider != null) {
         this.slider.action = () -> {
            int lastValue = this.value;
            this.value = (int)Math.round(this.slider.get());
            this.textBox.set(Integer.toString(this.value));
            if (this.action != null && this.value != lastValue) {
               this.action.run();
            }
         };
         this.slider.actionOnRelease = () -> {
            if (this.actionOnRelease != null) {
               this.actionOnRelease.run();
            }
         };
      }
   }

   private boolean filter(String text, char c) {
      boolean validate = true;
      boolean good;
      if (c == '-' && !text.contains("-") && this.textBox.cursor == 0) {
         good = true;
         validate = false;
      } else {
         good = Character.isDigit(c);
      }

      if (good && validate) {
         try {
            Integer.parseInt(text + c);
         } catch (NumberFormatException var6) {
            good = false;
         }
      }

      return good;
   }

   private void setButton(int v) {
      if (this.value != v) {
         if (v < this.min) {
            this.value = this.min;
         } else {
            this.value = Math.min(v, this.max);
         }

         if (this.value == v) {
            this.textBox.set(Integer.toString(this.value));
            if (this.slider != null) {
               this.slider.set(this.value);
            }

            if (this.action != null) {
               this.action.run();
            }

            if (this.actionOnRelease != null) {
               this.actionOnRelease.run();
            }
         }
      }
   }

   public int get() {
      return this.value;
   }

   public void set(int value) {
      this.value = value;
      this.textBox.set(Integer.toString(value));
      if (this.slider != null) {
         this.slider.set(value);
      }
   }
}
