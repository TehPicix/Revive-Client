package revivedevelopment.reviveclient.systems.hud;

import net.minecraft.nbt.NbtCompound;
import revivedevelopment.reviveclient.utils.Utils;
import revivedevelopment.reviveclient.utils.misc.ISerializable;

public class HudBox implements ISerializable<HudBox> {
   private final HudElement element;
   public XAnchor xAnchor = XAnchor.Left;
   public YAnchor yAnchor = YAnchor.Top;
   public int x;
   public int y;
   int width;
   int height;

   public HudBox(HudElement element) {
      this.element = element;
   }

   public void setSize(double width, double height) {
      if (width >= 0.0) {
         this.width = (int)Math.ceil(width);
      }

      if (height >= 0.0) {
         this.height = (int)Math.ceil(height);
      }
   }

   public void setPos(int x, int y) {
      this.x = x;
      this.y = y;
   }

   public void setXAnchor(XAnchor anchor) {
      if (this.xAnchor != anchor) {
         int renderX = this.getRenderX();
         switch (anchor) {
            case Left:
               this.x = renderX;
               break;
            case Center:
               this.x = renderX + this.width / 2 - Utils.getWindowWidth() / 2;
               break;
            case Right:
               this.x = renderX + this.width - Utils.getWindowWidth();
         }

         this.xAnchor = anchor;
      }
   }

   public void setYAnchor(YAnchor anchor) {
      if (this.yAnchor != anchor) {
         int renderY = this.getRenderY();
         switch (anchor) {
            case Top:
               this.y = renderY;
               break;
            case Center:
               this.y = renderY + this.height / 2 - Utils.getWindowHeight() / 2;
               break;
            case Bottom:
               this.y = renderY + this.height - Utils.getWindowHeight();
         }

         this.yAnchor = anchor;
      }
   }

   public void updateAnchors() {
      this.setXAnchor(this.getXAnchor(this.getRenderX()));
      this.setYAnchor(this.getYAnchor(this.getRenderY()));
   }

   public void move(int deltaX, int deltaY) {
      this.x += deltaX;
      this.y += deltaY;
      if (this.element.autoAnchors) {
         this.updateAnchors();
      }

      int border = Hud.get().getBorder();
      if (this.xAnchor == XAnchor.Left && this.x < border) {
         this.x = border;
      } else if (this.xAnchor == XAnchor.Right && this.x > border) {
         this.x = border;
      }

      if (this.yAnchor == YAnchor.Top && this.y < border) {
         this.y = border;
      } else if (this.yAnchor == YAnchor.Bottom && this.y > border) {
         this.y = border;
      }
   }

   public XAnchor getXAnchor(double x) {
      double splitLeft = Utils.getWindowWidth() / 3.0;
      double splitRight = splitLeft * 2.0;
      boolean left = x <= splitLeft;
      boolean right = x + this.width >= splitRight;
      if ((!left || !right) && (left || right)) {
         return left ? XAnchor.Left : XAnchor.Right;
      } else {
         return XAnchor.Center;
      }
   }

   public YAnchor getYAnchor(double y) {
      double splitTop = Utils.getWindowHeight() / 3.0;
      double splitBottom = splitTop * 2.0;
      boolean top = y <= splitTop;
      boolean bottom = y + this.height >= splitBottom;
      if ((!top || !bottom) && (top || bottom)) {
         return top ? YAnchor.Top : YAnchor.Bottom;
      } else {
         return YAnchor.Center;
      }
   }

   public int getRenderX() {
      return switch (this.xAnchor) {
         case Left -> this.x;
         case Center -> Utils.getWindowWidth() / 2 - this.width / 2 + this.x;
         case Right -> Utils.getWindowWidth() - this.width + this.x;
      };
   }

   public int getRenderY() {
      return switch (this.yAnchor) {
         case Top -> this.y;
         case Center -> Utils.getWindowHeight() / 2 - this.height / 2 + this.y;
         case Bottom -> Utils.getWindowHeight() - this.height + this.y;
      };
   }

   public double alignX(double selfWidth, double width, Alignment alignment) {
      XAnchor anchor = this.xAnchor;
      if (alignment == Alignment.Left) {
         anchor = XAnchor.Left;
      } else if (alignment == Alignment.Center) {
         anchor = XAnchor.Center;
      } else if (alignment == Alignment.Right) {
         anchor = XAnchor.Right;
      }
      return switch (anchor) {
         case Left -> 0.0;
         case Center -> selfWidth / 2.0 - width / 2.0;
         case Right -> selfWidth - width;
      };
   }

   @Override
   public NbtCompound toTag() {
      NbtCompound tag = new NbtCompound();
      tag.putString("x-anchor", this.xAnchor.name());
      tag.putString("y-anchor", this.yAnchor.name());
      tag.putInt("x", this.x);
      tag.putInt("y", this.y);
      return tag;
   }

   public HudBox fromTag(NbtCompound tag) {
      if (tag.contains("x-anchor")) {
         this.xAnchor = XAnchor.valueOf(tag.getString("x-anchor").orElse("Left"));
      }

      if (tag.contains("y-anchor")) {
         this.yAnchor = YAnchor.valueOf(tag.getString("y-anchor").orElse("Top"));
      }

      if (tag.contains("x")) {
         this.x = tag.getInt("x").orElse(0);
      }

      if (tag.contains("y")) {
         this.y = tag.getInt("y").orElse(0);
      }

      return this;
   }
}
