package revivedevelopment.reviveclient.utils.world;

import net.minecraft.util.math.Direction;

public class Dir {
   public static final byte UP = 2;
   public static final byte DOWN = 4;
   public static final byte NORTH = 8;
   public static final byte SOUTH = 16;
   public static final byte WEST = 32;
   public static final byte EAST = 64;

   private Dir() {
   }

   public static byte get(Direction dir) {
      return switch (dir) {
         case UP -> 2;
         case DOWN -> 4;
         case NORTH -> 8;
         case SOUTH -> 16;
         case WEST -> 32;
         case EAST -> 64;
         default -> throw new MatchException(null, null);
      };
   }

   public static boolean is(int dir, byte idk) {
      return (dir & idk) == idk;
   }

   public static boolean isNot(int dir, byte idk) {
      return (dir & idk) != idk;
   }
}
