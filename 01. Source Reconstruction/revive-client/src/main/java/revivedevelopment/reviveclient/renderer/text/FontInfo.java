package revivedevelopment.reviveclient.renderer.text;

public record FontInfo(String family, FontInfo.Type type) {
   @Override
   public String toString() {
      return this.family + " " + this.type;
   }

   public boolean equals(FontInfo info) {
      if (this == info) {
         return true;
      } else {
         return info != null && this.family != null && this.type != null ? this.family.equals(info.family) && this.type == info.type : false;
      }
   }

   public static enum Type {
      Regular,
      Bold,
      Italic,
      BoldItalic;

      public static FontInfo.Type fromString(String str) {
         return switch (str) {
            case "Bold" -> Bold;
            case "Italic" -> Italic;
            case "Bold Italic", "BoldItalic" -> BoldItalic;
            default -> Regular;
         };
      }

      @Override
      public String toString() {
         return switch (this) {
            case Bold -> "Bold";
            case Italic -> "Italic";
            case BoldItalic -> "Bold Italic";
            default -> "Regular";
         };
      }
   }
}
