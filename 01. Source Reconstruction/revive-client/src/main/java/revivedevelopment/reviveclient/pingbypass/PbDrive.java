package revivedevelopment.reviveclient.pingbypass;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import java.nio.charset.StandardCharsets;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.UnknownCustomPayload;
import net.minecraft.network.packet.CustomPayload.Id;
import net.minecraft.network.packet.c2s.common.CommonPongC2SPacket;
import net.minecraft.network.packet.c2s.common.CustomPayloadC2SPacket;
import net.minecraft.network.packet.c2s.common.KeepAliveC2SPacket;
import net.minecraft.network.packet.c2s.common.ResourcePackStatusC2SPacket;
import net.minecraft.network.packet.c2s.play.AcknowledgeChunksC2SPacket;
import net.minecraft.network.packet.c2s.play.BoatPaddleStateC2SPacket;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.ClientTickEndC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInputC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerLoadedC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.TeleportConfirmC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdatePlayerAbilitiesC2SPacket;
import net.minecraft.network.packet.c2s.play.VehicleMoveC2SPacket;
import net.minecraft.network.packet.s2c.common.CustomPayloadS2CPacket;
import net.minecraft.network.state.NetworkState;
import net.minecraft.util.Identifier;

public final class PbDrive {
   public static final Identifier INPUT = Identifier.of("revive", "pb_in");
   public static final Identifier STATE = Identifier.of("revive", "pb_st");
   private static final int VERSION = 4;
   private static final int MAX_BODY = 512;
   public static final int FORWARD = 1;
   public static final int BACK = 2;
   public static final int LEFT = 4;
   public static final int RIGHT = 8;
   public static final int JUMP = 16;
   public static final int SNEAK = 32;
   public static final int SPRINT = 64;
   public static final int MOVEMENT = 15;
   public static final int USE = 128;
   public static final int SENT = 128;
   public static final int SCREEN_NONE = 0;
   public static final int SCREEN_INVENTORY = 1;
   public static final int SCREEN_HANDLED = 2;
   public static final int SCREEN_OTHER = 3;
   public static final int ON_GROUND = 1;
   public static final int SPRINTING = 2;
   public static final int SNEAKING = 4;
   public static final int GLIDING = 8;
   public static final int USING = 16;
   public static final int FRESH = 32;
   private static final Identifier OWN_MOVE = PbProtocol.OWN_MOVE;

   private PbDrive() {
   }

   public static boolean isMadeByVps(Packet<?> packet) {
      if (packet instanceof ClientCommandC2SPacket command) {
         return switch (command.getMode()) {
            case STOP_SLEEPING, OPEN_INVENTORY -> false;
            default -> true;
         };
      } else {
         return packet instanceof PlayerMoveC2SPacket
            || packet instanceof PlayerInputC2SPacket
            || packet instanceof ClientTickEndC2SPacket
            || packet instanceof TeleportConfirmC2SPacket
            || packet instanceof KeepAliveC2SPacket
            || packet instanceof CommonPongC2SPacket
            || packet instanceof AcknowledgeChunksC2SPacket
            || packet instanceof PlayerLoadedC2SPacket
            || packet instanceof VehicleMoveC2SPacket
            || packet instanceof BoatPaddleStateC2SPacket
            || packet instanceof UpdatePlayerAbilitiesC2SPacket;
      }
   }

   public static boolean isPlayed(Packet<?> packet) {
      if (packet instanceof CustomPayloadC2SPacket custom) {
         Identifier channel = custom.payload().getId().id();
         return !channel.equals(INPUT) && !channel.equals(OWN_MOVE);
      } else {
         return !(packet instanceof ResourcePackStatusC2SPacket) && !isMadeByVps(packet);
      }
   }

   public static ByteBuf inputFrame(NetworkState<?> state, PbDrive.Input input) {
      ByteBuf buf = header(state, new CustomPayloadC2SPacket(new UnknownCustomPayload(INPUT)));
      input.write(buf);
      return buf;
   }

   public static ByteBuf stateFrame(NetworkState<?> state, PbDrive.State s) {
      ByteBuf buf = header(state, new CustomPayloadS2CPacket(new UnknownCustomPayload(STATE)));
      s.write(buf);
      return buf;
   }

   public static PbDrive.Input readInput(ByteBuf frame) {
      return PbDrive.Input.read(body(frame.duplicate(), INPUT));
   }

   public static PbDrive.State readState(ByteBuf frame) {
      return PbDrive.State.read(body(frame.duplicate(), STATE));
   }

   public static boolean isFrame(ByteBuf frame, int customPayloadId, Identifier channel) {
      ByteBuf buf = frame.duplicate();

      try {
         if (readVarInt(buf) != customPayloadId) {
            return false;
         } else {
            byte[] want = channel.toString().getBytes(StandardCharsets.UTF_8);
            int len = readVarInt(buf);
            if (len == want.length && buf.readableBytes() >= len) {
               for (byte b : want) {
                  if (buf.readByte() != b) {
                     return false;
                  }
               }

               return true;
            } else {
               return false;
            }
         }
      } catch (RuntimeException var10) {
         return false;
      }
   }

   public static int packetId(NetworkState<?> state, Packet<?> sample) {
      ByteBuf tmp = Unpooled.buffer();

      byte var4;
      try {
         codec(state).encode(tmp, sample);
         return readVarInt(tmp);
      } catch (RuntimeException var8) {
         var4 = -1;
      } finally {
         tmp.release();
      }

      return var4;
   }

   private static ByteBuf header(NetworkState<?> state, Packet<?> sample) {
      ByteBuf buf = Unpooled.buffer(128);

      try {
         codec(state).encode(buf, sample);
         return buf;
      } catch (RuntimeException var4) {
         buf.release();
         throw var4;
      }
   }

   private static ByteBuf body(ByteBuf buf, Identifier channel) {
      readVarInt(buf);
      int len = readVarInt(buf);
      if (len >= 0 && len <= buf.readableBytes()) {
         String name = buf.toString(buf.readerIndex(), len, StandardCharsets.UTF_8);
         buf.skipBytes(len);
         if (!channel.toString().equals(name)) {
            throw new DecoderException("Not a " + channel + " frame: " + name);
         } else if (buf.readableBytes() > 512) {
            throw new DecoderException("PingBypass frame too long");
         } else {
            return buf;
         }
      } else {
         throw new DecoderException("Bad channel length " + len);
      }
   }

   private static PacketCodec<ByteBuf, Packet<?>> codec(NetworkState state) {
      return state.codec();
   }

   static int readVarInt(ByteBuf buf) {
      int value = 0;
      int position = 0;

      while (buf.isReadable()) {
         byte b = buf.readByte();
         value |= (b & 127) << position;
         if ((b & 128) == 0) {
            return value;
         }

         position += 7;
         if (position >= 32) {
            throw new DecoderException("VarInt too big");
         }
      }

      throw new DecoderException("VarInt too short");
   }

   static void writeVarInt(ByteBuf buf, int value) {
      while ((value & -128) != 0) {
         buf.writeByte(value & 127 | 128);
         value >>>= 7;
      }

      buf.writeByte(value);
   }

   public record Input(
      int seq,
      int world,
      int rot,
      int keys,
      int sentKeys,
      float yaw,
      float pitch,
      float aimYaw,
      float aimPitch,
      boolean aimMoveFix,
      int split,
      int slots,
      int screen,
      double x,
      double y,
      double z,
      double vx,
      double vy,
      double vz
   ) {
      public boolean has(int key) {
         return (this.keys & key) != 0;
      }

      public boolean hasAim() {
         return !Float.isNaN(this.aimYaw);
      }

      void write(ByteBuf buf) {
         buf.writeByte(4);
         PbDrive.writeVarInt(buf, this.seq);
         PbDrive.writeVarInt(buf, this.world);
         PbDrive.writeVarInt(buf, this.rot);
         buf.writeByte(this.keys);
         buf.writeByte(this.sentKeys);
         buf.writeFloat(this.yaw);
         buf.writeFloat(this.pitch);
         buf.writeFloat(this.aimYaw);
         buf.writeFloat(this.aimPitch);
         buf.writeBoolean(this.aimMoveFix);
         PbDrive.writeVarInt(buf, this.split);
         PbDrive.writeVarInt(buf, this.slots);
         buf.writeByte(this.screen);
         buf.writeDouble(this.x);
         buf.writeDouble(this.y);
         buf.writeDouble(this.z);
         buf.writeDouble(this.vx);
         buf.writeDouble(this.vy);
         buf.writeDouble(this.vz);
      }

      static PbDrive.Input read(ByteBuf buf) {
         if (buf.readUnsignedByte() != 4) {
            throw new DecoderException("PingBypass input frame of another version");
         } else {
            int seq = PbDrive.readVarInt(buf);
            int world = PbDrive.readVarInt(buf);
            int rot = PbDrive.readVarInt(buf);
            int keys = buf.readUnsignedByte();
            int sentKeys = buf.readUnsignedByte();
            float yaw = buf.readFloat();
            float pitch = buf.readFloat();
            float aimYaw = buf.readFloat();
            float aimPitch = buf.readFloat();
            boolean aimMoveFix = buf.readBoolean();
            int split = PbDrive.readVarInt(buf);
            int slots = PbDrive.readVarInt(buf);
            int screen = buf.readUnsignedByte();
            double x = buf.readDouble();
            double y = buf.readDouble();
            double z = buf.readDouble();
            double vx = buf.readDouble();
            double vy = buf.readDouble();
            double vz = buf.readDouble();
            if (Float.isFinite(yaw) && Float.isFinite(pitch)) {
               return new PbDrive.Input(seq, world, rot, keys, sentKeys, yaw, pitch, aimYaw, aimPitch, aimMoveFix, split, slots, screen, x, y, z, vx, vy, vz);
            } else {
               throw new DecoderException("PingBypass input frame without a look");
            }
         }
      }
   }

   public record State(int seq, int world, int teleports, double x, double y, double z, double vx, double vy, double vz, int flags, int jumpCooldown) {
      public boolean has(int flag) {
         return (this.flags & flag) != 0;
      }

      void write(ByteBuf buf) {
         buf.writeByte(4);
         PbDrive.writeVarInt(buf, this.seq + 1);
         PbDrive.writeVarInt(buf, this.world);
         PbDrive.writeVarInt(buf, this.teleports);
         buf.writeDouble(this.x);
         buf.writeDouble(this.y);
         buf.writeDouble(this.z);
         buf.writeDouble(this.vx);
         buf.writeDouble(this.vy);
         buf.writeDouble(this.vz);
         buf.writeByte(this.flags);
         buf.writeByte(Math.max(0, Math.min(255, this.jumpCooldown)));
      }

      static PbDrive.State read(ByteBuf buf) {
         if (buf.readUnsignedByte() != 4) {
            throw new DecoderException("PingBypass state frame of another version");
         } else {
            int seq = PbDrive.readVarInt(buf) - 1;
            int world = PbDrive.readVarInt(buf);
            int teleports = PbDrive.readVarInt(buf);
            double x = buf.readDouble();
            double y = buf.readDouble();
            double z = buf.readDouble();
            double vx = buf.readDouble();
            double vy = buf.readDouble();
            double vz = buf.readDouble();
            int flags = buf.readUnsignedByte();
            return new PbDrive.State(seq, world, teleports, x, y, z, vx, vy, vz, flags, buf.readUnsignedByte());
         }
      }
   }

   public record StatePayload(PbDrive.State state) implements CustomPayload {
      public static final Id<PbDrive.StatePayload> ID = new Id(PbDrive.STATE);

      public Id<? extends CustomPayload> getId() {
         return ID;
      }
   }
}
