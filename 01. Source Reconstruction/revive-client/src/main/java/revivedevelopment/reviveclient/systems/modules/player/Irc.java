package revivedevelopment.reviveclient.systems.modules.player;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentLinkedQueue;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import revivedevelopment.reviveclient.events.game.SendMessageEvent;
import revivedevelopment.reviveclient.events.world.TickEvent;
import revivedevelopment.reviveclient.settings.BoolSetting;
import revivedevelopment.reviveclient.settings.IntSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.settings.StringSetting;
import revivedevelopment.reviveclient.systems.modules.Categories;
import revivedevelopment.reviveclient.systems.modules.Module;

public class Irc extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgServer = this.settings.createGroup("Server");
   private final Setting<String> trigger = this.sgGeneral
      .add(
         new StringSetting.Builder()
            .name("trigger")
            .description("Chat prefix that sends the rest of the line to IRC (e.g. \"!! hello\").")
            .defaultValue("!!")
            .build()
      );
   private final Setting<Boolean> showJoinLeave = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("show-join-leave")
            .description("Print IRC join / part / quit notices in your Minecraft chat.")
            .defaultValue(false)
            .build()
      );
   private final Setting<String> address = this.sgServer
      .add(new StringSetting.Builder().name("ip").description("IP / hostname of your IRC server.").defaultValue("127.0.0.1").build());
   private final Setting<Integer> port = this.sgServer
      .add(new IntSetting.Builder().name("port").description("Port your IRC server listens on.").defaultValue(6667).range(1, 65535).noSlider().build());
   private final Setting<String> channel = this.sgServer
      .add(new StringSetting.Builder().name("channel").description("Channel to join (must start with #).").defaultValue("#minecraft").build());
   private volatile Irc.Connection connection;
   private final ConcurrentLinkedQueue<Text> inbound = new ConcurrentLinkedQueue<>();

   public Irc() {
      super(Categories.Player, "irc", "Relays Minecraft chat to/from a custom IRC server with a \"!!\" prefix.");
   }

   @Override
   public void onActivate() {
      this.inbound.clear();
      this.startConnection();
   }

   @Override
   public void onDeactivate() {
      Irc.Connection c = this.connection;
      this.connection = null;
      if (c != null) {
         c.close();
      }

      this.inbound.clear();
   }

   private void startConnection() {
      String chan = this.channel.get().startsWith("#") ? this.channel.get() : "#" + this.channel.get();
      String resolvedNick = this.mc.getSession() != null ? this.mc.getSession().getUsername() : "Player";
      Irc.Connection c = new Irc.Connection(this.address.get(), this.port.get(), resolvedNick, chan);
      this.connection = c;
      c.start();
   }

   @EventHandler
   private void onTick(TickEvent.Post event) {
      Text msg;
      while ((msg = this.inbound.poll()) != null) {
         this.info(msg);
      }
   }

   @EventHandler
   private void onSendMessage(SendMessageEvent event) {
      String message = event.message;
      String pfx = this.trigger.get();
      if (!pfx.isEmpty() && message.startsWith(pfx)) {
         String text = message.substring(pfx.length());
         if (text.startsWith(" ")) {
            text = text.substring(1);
         }

         text = text.trim();
         event.setCancelled(true);
         if (!text.isEmpty()) {
            Irc.Connection c = this.connection;
            if (c != null && c.isReady()) {
               c.sendMessage(text);
               this.queueChat(c.nick, text, true);
            } else {
               this.error("Not connected to IRC yet.", new Object[0]);
            }
         }
      }
   }

   private void queueChat(String from, String text, boolean self) {
      Formatting nameColor = self ? Formatting.AQUA : Formatting.GREEN;
      Text line = Text.literal("<")
         .formatted(Formatting.GRAY)
         .append(Text.literal(from).formatted(nameColor))
         .append(Text.literal("> ").formatted(Formatting.GRAY))
         .append(Text.literal(text).formatted(Formatting.WHITE));
      this.inbound.add(line);
   }

   private void queueNotice(String text, Formatting color) {
      this.inbound.add(Text.literal(text).formatted(color));
   }

   @Override
   public String getInfoString() {
      Irc.Connection c = this.connection;
      return c != null && c.isReady() ? c.channel : "connecting";
   }

   private class Connection extends Thread {
      private final String host;
      private final int port;
      final String channel;
      volatile String nick;
      private volatile Socket socket;
      private volatile Writer writer;
      private volatile boolean ready;
      private volatile boolean closed;
      private int nickTries;

      Connection(String host, int port, String nick, String channel) {
         super("Revive-IRC");
         this.host = host;
         this.port = port;
         this.nick = nick;
         this.channel = channel;
         this.setDaemon(true);
      }

      boolean isReady() {
         return this.ready && !this.closed;
      }

      @Override
      public void run() {
         try {
            Socket s = new Socket();
            s.connect(new InetSocketAddress(this.host, this.port), 8000);
            this.socket = s;
            this.writer = new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8);
            this.send("NICK " + this.nick);
            this.send("USER " + this.nick + " 0 * :" + this.nick);
            BufferedReader reader = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));

            String line;
            while (!this.closed && (line = reader.readLine()) != null) {
               this.handleLine(line);
            }
         } catch (IOException var8) {
            if (!this.closed) {
               Irc.this.queueNotice("[IRC] Connection failed: " + var8.getMessage(), Formatting.RED);
            }
         } finally {
            boolean wasReady = this.ready;
            this.ready = false;
            this.closeSocket();
            if (!this.closed && wasReady) {
               Irc.this.queueNotice("[IRC] Disconnected.", Formatting.RED);
            }
         }
      }

      private void handleLine(String line) {
         if (line.startsWith("PING")) {
            String token = line.contains(":") ? line.substring(line.indexOf(58) + 1) : "";
            this.send("PONG :" + token);
         } else {
            String prefix = "";
            String rest = line;
            if (line.startsWith(":")) {
               int sp = line.indexOf(32);
               if (sp < 0) {
                  return;
               }

               prefix = line.substring(1, sp);
               rest = line.substring(sp + 1);
            }

            int sp = rest.indexOf(32);
            String command = sp < 0 ? rest : rest.substring(0, sp);
            String params = sp < 0 ? "" : rest.substring(sp + 1);
            switch (command) {
               case "001":
                  this.send("JOIN " + this.channel);
                  this.ready = true;
                  Irc.this.queueNotice("[IRC] Connected to " + this.host + ":" + this.port + " as " + this.nick + ".", Formatting.GREEN);
                  break;
               case "433":
               case "436":
                  if (!this.ready && this.nickTries < 5) {
                     this.nickTries++;
                     this.nick = this.nick + "_";
                     this.send("NICK " + this.nick);
                  }
                  break;
               case "PRIVMSG":
                  int colon = params.indexOf(58);
                  String target = (colon < 0 ? params : params.substring(0, colon)).trim();
                  String text = colon < 0 ? "" : params.substring(colon + 1);
                  if (text.isEmpty()) {
                     return;
                  }

                  String sender = this.senderOf(prefix);
                  if (target.equalsIgnoreCase(this.channel) && !sender.equalsIgnoreCase(this.nick)) {
                     Irc.this.queueChat(sender, text, false);
                  }
                  break;
               case "JOIN":
                  if (Irc.this.showJoinLeave.get()) {
                     sender = this.senderOf(prefix);
                     if (!sender.equalsIgnoreCase(this.nick)) {
                        Irc.this.queueNotice("[IRC] " + sender + " joined.", Formatting.DARK_GREEN);
                     }
                  }
                  break;
               case "PART":
               case "QUIT":
                  if (Irc.this.showJoinLeave.get()) {
                     sender = this.senderOf(prefix);
                     if (!sender.equalsIgnoreCase(this.nick)) {
                        Irc.this.queueNotice("[IRC] " + sender + " left.", Formatting.GRAY);
                     }
                  }
            }
         }
      }

      private String senderOf(String prefix) {
         if (prefix.isEmpty()) {
            return "server";
         } else {
            int bang = prefix.indexOf(33);
            return bang < 0 ? prefix : prefix.substring(0, bang);
         }
      }

      void sendMessage(String text) {
         this.send("PRIVMSG " + this.channel + " :" + text);
      }

      private synchronized void send(String line) {
         Writer w = this.writer;
         if (w != null) {
            try {
               w.write(line + "\r\n");
               w.flush();
            } catch (IOException var4) {
               if (!this.closed) {
                  Irc.this.queueNotice("[IRC] Send failed: " + var4.getMessage(), Formatting.RED);
               }
            }
         }
      }

      void close() {
         this.closed = true;
         this.closeSocket();
      }

      private void closeSocket() {
         Socket s = this.socket;
         if (s != null) {
            try {
               s.close();
            } catch (IOException var3) {
            }
         }
      }
   }
}
