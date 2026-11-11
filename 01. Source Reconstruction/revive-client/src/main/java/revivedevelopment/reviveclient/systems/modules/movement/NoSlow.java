package revivedevelopment.reviveclient.systems.modules.movement;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket.Action;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.Full;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.events.packets.PacketEvent;
import revivedevelopment.reviveclient.events.world.TickEvent;
import revivedevelopment.reviveclient.pingbypass.client.PbPredict;
import revivedevelopment.reviveclient.settings.BoolSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.systems.modules.Categories;
import revivedevelopment.reviveclient.systems.modules.Module;
import revivedevelopment.reviveclient.systems.modules.Modules;
import revivedevelopment.reviveclient.utils.world.TickRate;

public class NoSlow extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   public final Setting<Boolean> eat = this.sgGeneral
      .add(new BoolSetting.Builder().name("eat").description("No slowdown while eating or drinking (main hand).").defaultValue(true).build());
   public final Setting<Boolean> crawl = this.sgGeneral
      .add(new BoolSetting.Builder().name("crawl").description("No slowdown while crawling.").defaultValue(true).build());
   public final Setting<Boolean> webs = this.sgGeneral
      .add(new BoolSetting.Builder().name("webs").description("No cobweb slowdown.").defaultValue(false).build());
   private static final int SPOOF_PACKETS = 37;
   private int movesUntilSpoof;
   private int spoofsLeft;
   private boolean useSession;
   private boolean skipNextMove;
   private boolean wasConsuming;
   private boolean sendingOwn;

   public NoSlow() {
      super(Categories.Movement, "no-slow", "Removes the slowdown from eating, crawling and cobwebs.");
   }

   public static NoSlow get() {
      Modules m = Modules.get();
      return m == null ? null : m.get(NoSlow.class);
   }

   @Override
   public void onActivate() {
      this.reset();
      this.wasConsuming = false;
   }

   @Override
   public void onDeactivate() {
      this.reset();
   }

   public static boolean cancelUseSlow() {
      NoSlow m = get();
      return m != null && m.isActive() && m.spoofActive();
   }

   public static boolean cancelCrawlSlow() {
      NoSlow m = get();
      return m != null && m.isActive() && m.crawl.get();
   }

   public static boolean cancelWebSlow(BlockState state) {
      NoSlow m = get();
      return m != null && m.isActive() && m.webs.get() && state.isOf(Blocks.COBWEB);
   }

   @EventHandler
   private void onTick(TickEvent.Pre event) {
      if (this.mc.player != null && this.mc.world != null) {
         this.sendWebBreaks();
         boolean consuming = this.consumingMainHand();
         if (this.wasConsuming && !consuming) {
            this.reset();
         }

         this.wasConsuming = consuming;
         if (!consuming && !this.holdingUseOnConsumable()) {
            this.reset();
         }
      }
   }

   private void sendWebBreaks() {
      if (this.webs.get() && this.mc.getNetworkHandler() != null && !PbPredict.serverPushesOnly()) {
         Box box = this.mc.player.getBoundingBox();

         for (BlockPos pos : BlockPos.iterate(
            MathHelper.floor(box.minX),
            MathHelper.floor(box.minY),
            MathHelper.floor(box.minZ),
            MathHelper.floor(box.maxX),
            MathHelper.floor(box.maxY),
            MathHelper.floor(box.maxZ)
         )) {
            if (this.mc.world.getBlockState(pos).isOf(Blocks.COBWEB)) {
               this.mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(Action.STOP_DESTROY_BLOCK, pos.toImmutable(), Direction.DOWN));
            }
         }
      }
   }

   @EventHandler
   private void onPacketSend(PacketEvent.Send event) {
      if (!this.sendingOwn && this.eatEnabled() && this.mc.player != null && this.mc.getNetworkHandler() != null) {
         if (event.packet instanceof PlayerInteractItemC2SPacket use && use.getHand() == Hand.MAIN_HAND) {
            this.useSession = true;
            this.movesUntilSpoof = 1;
            this.spoofsLeft = 37;
            this.skipNextMove = true;
         } else if (!PbPredict.serverPushesOnly()) {
            if (event.packet instanceof PlayerMoveC2SPacket move && this.spoofActive()) {
               if (move.changesPosition() && !move.changesLook()) {
                  event.cancel();
                  this.send(
                     new Full(
                        this.mc.player.getX(),
                        this.mc.player.getY(),
                        this.mc.player.getZ(),
                        ReviveClient.ROTATION.getServerYaw(),
                        ReviveClient.ROTATION.getServerPitch(),
                        move.isOnGround(),
                        this.mc.player.horizontalCollision
                     )
                  );
                  this.afterMove();
               } else {
                  if (move.changesPosition() && move.changesLook()) {
                     this.afterMove();
                  }
               }
            }
         }
      }
   }

   private void afterMove() {
      if (this.skipNextMove) {
         this.skipNextMove = false;
      } else if (this.movesUntilSpoof > 0) {
         this.movesUntilSpoof--;
      } else {
         if (this.spoofsLeft > 0) {
            int slot = this.mc.player.getInventory().getSelectedSlot();
            if (slot >= 0 && slot <= 8) {
               this.send(new UpdateSelectedSlotC2SPacket(slot));
               this.spoofsLeft--;
            }
         }
      }
   }

   private void send(Packet<?> packet) {
      this.sendingOwn = true;

      try {
         this.mc.getNetworkHandler().sendPacket(packet);
      } finally {
         this.sendingOwn = false;
      }
   }

   private boolean eatEnabled() {
      return this.isActive() && this.eat.get() && TickRate.INSTANCE.getTickRate() > 15.0F;
   }

   private boolean spoofActive() {
      return this.eatEnabled() && (this.consumingMainHand() || this.movesUntilSpoof > 0 || this.spoofsLeft > 0 || this.useSession);
   }

   private void reset() {
      this.movesUntilSpoof = 0;
      this.spoofsLeft = 0;
      this.useSession = false;
      this.skipNextMove = false;
   }

   private boolean consumingMainHand() {
      return this.mc.player != null
         && this.mc.player.isUsingItem()
         && this.mc.player.getActiveHand() == Hand.MAIN_HAND
         && consumable(this.mc.player.getActiveItem());
   }

   private boolean holdingUseOnConsumable() {
      return this.mc.player != null && this.mc.options.useKey.isPressed() && consumable(this.mc.player.getMainHandStack());
   }

   private static boolean consumable(ItemStack stack) {
      return !stack.isEmpty() && stack.contains(DataComponentTypes.CONSUMABLE);
   }
}
