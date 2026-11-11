package revivedevelopment.loader.api;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Json {
   private Json() {
   }

   public static String quote(String value) {
      StringBuilder out = new StringBuilder(value.length() + 2);
      out.append('"');

      for (int i = 0; i < value.length(); i++) {
         char c = value.charAt(i);
         switch (c) {
            case '\t':
               out.append("\\t");
               break;
            case '\n':
               out.append("\\n");
               break;
            case '\r':
               out.append("\\r");
               break;
            case '"':
               out.append("\\\"");
               break;
            case '\\':
               out.append("\\\\");
               break;
            default:
               if (c < ' ') {
                  out.append(String.format("\\u%04x", Integer.valueOf(c)));
               } else {
                  out.append(c);
               }
         }
      }

      return out.append('"').toString();
   }

   public static Map<String, Object> parseObject(String text) {
      Object value = new Json.Parser(text).parseValue();
      if (!(value instanceof Map)) {
         throw new IllegalArgumentException("Expected a JSON object");
      } else {
         return (Map<String, Object>)value;
      }
   }

   public static String getString(Map<String, Object> object, String key) {
      Object value = object.get(key);
      return value instanceof String ? (String)value : null;
   }

   public static boolean getBoolean(Map<String, Object> object, String key) {
      Object value = object.get(key);
      return value instanceof Boolean && (Boolean)value;
   }

   private static final class Parser {
      private final String s;
      private int i;

      Parser(String s) {
         this.s = s;
      }

      Object parseValue() {
         this.skipWhitespace();
         char c = this.peek();

         return switch (c) {
            case '"' -> this.parseString();
            case '[' -> this.parseArray();
            case 'f', 't' -> this.parseBoolean();
            case 'n' -> this.parseNull();
            case '{' -> this.parseObjectValue();
            default -> this.parseNumber();
         };
      }

      private Map<String, Object> parseObjectValue() {
         Map<String, Object> map = new LinkedHashMap<>();
         this.expect('{');
         this.skipWhitespace();
         if (this.peek() == '}') {
            this.i++;
            return map;
         } else {
            char c;
            do {
               this.skipWhitespace();
               String key = this.parseString();
               this.skipWhitespace();
               this.expect(':');
               map.put(key, this.parseValue());
               this.skipWhitespace();
               c = this.next();
               if (c == '}') {
                  return map;
               }
            } while (c == ',');

            throw this.error("Expected , or }");
         }
      }

      private List<Object> parseArray() {
         List<Object> list = new ArrayList<>();
         this.expect('[');
         this.skipWhitespace();
         if (this.peek() == ']') {
            this.i++;
            return list;
         } else {
            char c;
            do {
               list.add(this.parseValue());
               this.skipWhitespace();
               c = this.next();
               if (c == ']') {
                  return list;
               }
            } while (c == ',');

            throw this.error("Expected , or ]");
         }
      }

      private String parseString() {
         this.expect('"');
         StringBuilder out = new StringBuilder();

         while (true) {
            char c = this.next();
            if (c == '"') {
               return out.toString();
            }

            if (c == '\\') {
               char esc = this.next();
               switch (esc) {
                  case '"':
                     out.append('"');
                     break;
                  case '/':
                     out.append('/');
                     break;
                  case '\\':
                     out.append('\\');
                     break;
                  case 'b':
                     out.append('\b');
                     break;
                  case 'f':
                     out.append('\f');
                     break;
                  case 'n':
                     out.append('\n');
                     break;
                  case 'r':
                     out.append('\r');
                     break;
                  case 't':
                     out.append('\t');
                     break;
                  case 'u':
                     out.append((char)Integer.parseInt(this.s.substring(this.i, this.i + 4), 16));
                     this.i += 4;
                     break;
                  default:
                     throw this.error("Invalid escape \\" + esc);
               }
            } else {
               out.append(c);
            }
         }
      }

      private Boolean parseBoolean() {
         if (this.s.startsWith("true", this.i)) {
            this.i += 4;
            return Boolean.TRUE;
         } else if (this.s.startsWith("false", this.i)) {
            this.i += 5;
            return Boolean.FALSE;
         } else {
            throw this.error("Invalid literal");
         }
      }

      private Object parseNull() {
         if (this.s.startsWith("null", this.i)) {
            this.i += 4;
            return null;
         } else {
            throw this.error("Invalid literal");
         }
      }

      private Object parseNumber() {
         int start = this.i;

         while (this.i < this.s.length() && "-+.eE0123456789".indexOf(this.s.charAt(this.i)) >= 0) {
            this.i++;
         }

         String num = this.s.substring(start, this.i);
         return !num.contains(".") && !num.contains("e") && !num.contains("E") ? Long.parseLong(num) : Double.parseDouble(num);
      }

      private void skipWhitespace() {
         while (this.i < this.s.length() && Character.isWhitespace(this.s.charAt(this.i))) {
            this.i++;
         }
      }

      private char peek() {
         if (this.i >= this.s.length()) {
            throw this.error("Unexpected end of input");
         } else {
            return this.s.charAt(this.i);
         }
      }

      private char next() {
         if (this.i >= this.s.length()) {
            throw this.error("Unexpected end of input");
         } else {
            return this.s.charAt(this.i++);
         }
      }

      private void expect(char c) {
         char actual = this.next();
         if (actual != c) {
            throw this.error("Expected '" + c + "' but found '" + actual + "'");
         }
      }

      private IllegalArgumentException error(String message) {
         return new IllegalArgumentException("JSON parse error at " + this.i + ": " + message);
      }
   }

   public static final class Writer {
      private final StringBuilder sb = new StringBuilder("{");
      private boolean first = true;

      public Json.Writer put(String key, String value) {
         return this.raw(key, value == null ? "null" : Json.quote(value));
      }

      public Json.Writer put(String key, long value) {
         return this.raw(key, Long.toString(value));
      }

      public Json.Writer put(String key, boolean value) {
         return this.raw(key, Boolean.toString(value));
      }

      public Json.Writer putRaw(String key, String rawJson) {
         return this.raw(key, rawJson);
      }

      private Json.Writer raw(String key, String rawValue) {
         if (!this.first) {
            this.sb.append(',');
         }

         this.first = false;
         this.sb.append(Json.quote(key)).append(':').append(rawValue);
         return this;
      }

      public String build() {
         return this.sb.toString() + "}";
      }
   }
}
