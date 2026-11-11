package revivedevelopment.reviveclient.systems.modules.hunting;

import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.UnknownServiceException;
import javax.net.ssl.HttpsURLConnection;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket.Mode;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import revivedevelopment.reviveclient.systems.managers.ElytraDurabilitySaver;
import revivedevelopment.reviveclient.utils.misc.input.Input;
import revivedevelopment.reviveclient.utils.player.FindItemResult;
import revivedevelopment.reviveclient.utils.player.InvUtils;

public class HuntingUtils {
   public static final int FIREWORK_WAIT_FOR_GLIDE = -2;

   public static int firework(MinecraftClient mc, boolean elytraRequired) {
      if (mc.player != null && mc.player.isGliding() && !ElytraDurabilitySaver.get().serverGlideSteady()) {
         ElytraDurabilitySaver.get().holdForRocket();
         return -2;
      } else {
         int elytraSwapSlot = -1;
         if (elytraRequired && !mc.player.getInventory().getStack(38).isOf(Items.ELYTRA)) {
            FindItemResult itemResult = InvUtils.findInHotbar(Items.ELYTRA);
            if (!itemResult.found()) {
               return -1;
            }

            elytraSwapSlot = itemResult.slot();
            InvUtils.swap(itemResult.slot(), true);
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            InvUtils.swapBack();
            mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, Mode.START_FALL_FLYING));
         }

         FindItemResult itemResult = InvUtils.findInHotbar(Items.FIREWORK_ROCKET);
         if (!itemResult.found()) {
            return -1;
         } else {
            if (itemResult.isOffhand()) {
               mc.interactionManager.interactItem(mc.player, Hand.OFF_HAND);
               mc.player.swingHand(Hand.OFF_HAND);
            } else {
               InvUtils.swap(itemResult.slot(), true);
               mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
               mc.player.swingHand(Hand.MAIN_HAND);
               InvUtils.swapBack();
            }

            ElytraDurabilitySaver.get().rocketUsed();
            return elytraSwapSlot != -1 ? elytraSwapSlot : 200;
         }
      }
   }

   public static void setPressed(KeyBinding key, boolean pressed) {
      key.setPressed(pressed);
      Input.setKeyState(key, pressed);
   }

   public static Vec3d positionInDirection(Vec3d pos, double yaw, double distance) {
      Vec3d offset = yawToDirection(yaw).multiply(distance);
      return pos.add(offset);
   }

   public static Vec3d yawToDirection(double yaw) {
      yaw = yaw * Math.PI / 180.0;
      double x = -Math.sin(yaw);
      double z = Math.cos(yaw);
      return new Vec3d(x, 0.0, z);
   }

   public static double distancePointToDirection(Vec3d point, Vec3d direction, @Nullable Vec3d start) {
      if (start == null) {
         start = Vec3d.ZERO;
      }

      point = point.multiply(new Vec3d(1.0, 0.0, 1.0));
      start = start.multiply(new Vec3d(1.0, 0.0, 1.0));
      direction = direction.multiply(new Vec3d(1.0, 0.0, 1.0));
      Vec3d directionVec = point.subtract(start);
      double projectionLength = directionVec.dotProduct(direction) / direction.lengthSquared();
      Vec3d projection = direction.multiply(projectionLength);
      Vec3d perp = directionVec.subtract(projection);
      return perp.length();
   }

   public static double angleOnAxis(double yaw) {
      if (yaw < 0.0) {
         yaw += 360.0;
      }

      return Math.round(yaw / 45.0) * 45L;
   }

   public static float smoothRotation(double current, double target, double rotationScaling) {
      double difference = angleDifference(target, current);
      return (float)(current + difference * rotationScaling);
   }

   public static double angleDifference(double target, double current) {
      double diff = (target - current + 180.0) % 360.0 - 180.0;
      return diff < -180.0 ? diff + 360.0 : diff;
   }

   public static void sendWebhook(String webhookURL, String title, String message, String pingID, String playerName) {
      String json = "";
      json = json
         + "{\"embeds\": [{\"title\": \""
         + title
         + "\",\"description\": \""
         + message
         + "\",\"color\": 15258703,\"footer\": {\"text\": \"From: "
         + playerName
         + "\"}}]}";
      sendRequest(webhookURL, json);
      if (pingID != null) {
         json = "{\"content\": \"<@" + pingID + ">\"}";
         sendRequest(webhookURL, json);
      }
   }

   public static void sendWebhook(String webhookURL, String jsonObject, String pingID) {
      sendRequest(webhookURL, jsonObject);
      if (pingID != null) {
         jsonObject = "{\"content\": \"<@" + pingID + ">\"}";
         sendRequest(webhookURL, jsonObject);
      }
   }

   private static void sendRequest(String webhookURL, String json) {
      try {
         URL url = URI.create(webhookURL).toURL();
         HttpsURLConnection con = (HttpsURLConnection)url.openConnection();
         con.addRequestProperty("Content-Type", "application/json");
         con.addRequestProperty("User-Agent", "Mozilla");
         con.setDoOutput(true);
         con.setRequestMethod("POST");
         OutputStream stream = con.getOutputStream();
         stream.write(json.getBytes());
         stream.flush();
         stream.close();
         int code = con.getResponseCode();
         if (code >= 400) {
            System.err.println("[Hunting] webhook rejected with HTTP " + code + (code == 429 ? " (rate limited - raise the message delay)" : ""));
         } else {
            con.getInputStream().close();
         }

         con.disconnect();
      } catch (UnknownServiceException | MalformedURLException var6) {
      } catch (Exception var7) {
         var7.printStackTrace();
      }
   }
}
