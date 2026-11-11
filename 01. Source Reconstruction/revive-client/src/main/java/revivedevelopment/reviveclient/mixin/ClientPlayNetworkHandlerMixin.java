package revivedevelopment.reviveclient.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientCommonNetworkHandler;
import net.minecraft.client.network.ClientConnectionState;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.message.MessageBody;
import net.minecraft.network.message.MessageSignatureData;
import net.minecraft.network.message.MessageChain.Packer;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.network.packet.s2c.play.DamageTiltS2CPacket;
import net.minecraft.network.packet.s2c.play.EnterReconfigurationS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityAttributesS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityTrackerUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import net.minecraft.network.packet.s2c.play.GameJoinS2CPacket;
import net.minecraft.network.packet.s2c.play.InventoryS2CPacket;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.SetPlayerInventoryS2CPacket;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.commands.Commands;
import revivedevelopment.reviveclient.events.game.GameJoinedEvent;
import revivedevelopment.reviveclient.events.game.GameLeftEvent;
import revivedevelopment.reviveclient.events.game.SendMessageEvent;
import revivedevelopment.reviveclient.events.packets.ContainerSlotUpdateEvent;
import revivedevelopment.reviveclient.events.packets.EntityAttributesEvent;
import revivedevelopment.reviveclient.events.packets.InventoryEvent;
import revivedevelopment.reviveclient.events.packets.PlaySoundPacketEvent;
import revivedevelopment.reviveclient.events.packets.SetPlayerInventoryEvent;
import revivedevelopment.reviveclient.events.world.ChunkDataEvent;
import revivedevelopment.reviveclient.pingbypass.client.PbOldServerChat;
import revivedevelopment.reviveclient.pingbypass.client.PbPredict;
import revivedevelopment.reviveclient.systems.config.Config;
import revivedevelopment.reviveclient.systems.managers.ElytraDurabilitySaver;
import revivedevelopment.reviveclient.systems.modules.Modules;
import revivedevelopment.reviveclient.systems.modules.combat.Velocity;
import revivedevelopment.reviveclient.systems.modules.player.NoRotate;
import revivedevelopment.reviveclient.utils.BaritoneBridge;

@Mixin({ClientPlayNetworkHandler.class})
public abstract class ClientPlayNetworkHandlerMixin extends ClientCommonNetworkHandler {
   @Shadow
   private ClientWorld field_3699;
   @Unique
   private boolean ignoreChatMessage;
   @Unique
   private boolean worldNotNull;
   @Unique
   private Vec3d revive$preKnockbackVel;
   @Unique
   private Vec3d revive$preExplosionVel;
   @Unique
   private Vec3d revive$preMovePlayerVel;
   @Unique
   private Vec3d revive$preSaverVel;
   @Unique
   private Vec3d revive$preSaverPos;
   @Unique
   private static final int revive$SILENT_SETBACK_WINDOW = 10;
   @Unique
   private boolean revive$keepRotation;
   @Unique
   private float revive$keepYaw;
   @Unique
   private float revive$keepPitch;

   @Shadow
   public abstract void method_45729(String var1);

   protected ClientPlayNetworkHandlerMixin(MinecraftClient client, ClientConnection connection, ClientConnectionState connectionState) {
      super(client, connection, connectionState);
   }

   @Inject(
      method = {"method_11120"},
      at = {@At("HEAD")}
   )
   private void onGameJoinHead(GameJoinS2CPacket packet, CallbackInfo info) {
      this.worldNotNull = this.field_3699 != null;
   }

   @Inject(
      method = {"method_11120"},
      at = {@At("TAIL")}
   )
   private void onGameJoinTail(GameJoinS2CPacket packet, CallbackInfo info) {
      if (this.worldNotNull) {
         ReviveClient.EVENT_BUS.post(GameLeftEvent.get());
      }

      ReviveClient.EVENT_BUS.post(GameJoinedEvent.get());
   }

   @Inject(
      method = {"method_52798"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/class_2600;method_11074(Lnet/minecraft/class_2596;Lnet/minecraft/class_2547;Lnet/minecraft/class_11980;)V",
         shift = Shift.AFTER
      )}
   )
   private void onEnterReconfiguration(EnterReconfigurationS2CPacket packet, CallbackInfo info) {
      ReviveClient.EVENT_BUS.post(GameLeftEvent.get());
   }

   @Inject(
      method = {"method_11146"},
      at = {@At("HEAD")}
   )
   private void onPlaySound(PlaySoundS2CPacket packet, CallbackInfo info) {
      ReviveClient.EVENT_BUS.post(PlaySoundPacketEvent.get(packet));
   }

   @Inject(
      method = {"method_11128"},
      at = {@At("TAIL")}
   )
   private void onChunkData(ChunkDataS2CPacket packet, CallbackInfo info) {
      WorldChunk chunk = this.client.world.getChunk(packet.getChunkX(), packet.getChunkZ());
      ReviveClient.EVENT_BUS.post(new ChunkDataEvent(chunk));
   }

   @Inject(
      method = {"method_11109"},
      at = {@At("TAIL")}
   )
   private void onContainerSlotUpdate(ScreenHandlerSlotUpdateS2CPacket packet, CallbackInfo info) {
      ReviveClient.EVENT_BUS.post(ContainerSlotUpdateEvent.get(packet));
   }

   @Inject(
      method = {"method_11153"},
      at = {@At("TAIL")}
   )
   private void onInventory(InventoryS2CPacket packet, CallbackInfo info) {
      ReviveClient.EVENT_BUS.post(InventoryEvent.get(packet));
   }

   @Inject(
      method = {"method_61188"},
      at = {@At("TAIL")},
      require = 0
   )
   private void onSetPlayerInventory(SetPlayerInventoryS2CPacket packet, CallbackInfo info) {
      ReviveClient.EVENT_BUS.post(SetPlayerInventoryEvent.get(packet));
   }

   @Inject(
      method = {"method_11093"},
      at = {@At("HEAD")},
      require = 0
   )
   private void revive$saverTrackerHead(EntityTrackerUpdateS2CPacket packet, CallbackInfo info) {
      if (this.client.player != null && packet.id() == this.client.player.getId()) {
         ElytraDurabilitySaver.get().serverTrackerApply(true);
      }
   }

   @Inject(
      method = {"method_11093"},
      at = {@At("RETURN")},
      require = 0
   )
   private void revive$saverTrackerReturn(EntityTrackerUpdateS2CPacket packet, CallbackInfo info) {
      ElytraDurabilitySaver.get().serverTrackerApply(false);
   }

   @Inject(
      method = {"method_11149"},
      at = {@At("TAIL")},
      require = 0
   )
   private void onEntityAttributes(EntityAttributesS2CPacket packet, CallbackInfo info) {
      ReviveClient.EVENT_BUS.post(EntityAttributesEvent.get(packet));
   }

   @Inject(
      method = {"method_45729"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onSendChatMessage(String message, CallbackInfo ci) {
      if (!this.ignoreChatMessage) {
         String prefix = Config.get().prefix.get();
         if (!prefix.isEmpty() && message.startsWith(prefix)) {
            Commands.get().dispatch(message.substring(prefix.length()));
            ci.cancel();
         } else {
            if (BaritoneBridge.isPrefixControlEnabled()) {
               String bPrefix = BaritoneBridge.getPrefix();
               if (!bPrefix.isEmpty() && message.startsWith(bPrefix) && message.length() > bPrefix.length()) {
                  BaritoneBridge.execute(message.substring(bPrefix.length()));
                  ci.cancel();
                  return;
               }
            }

            SendMessageEvent event = (SendMessageEvent)ReviveClient.EVENT_BUS.post(SendMessageEvent.get(message));
            if (!event.isCancelled()) {
               this.ignoreChatMessage = true;
               this.method_45729(event.message);
               this.ignoreChatMessage = false;
               Config config = Config.get();
               if (config != null && config.showSentMessages.get() && this.client.player != null) {
                  String name = this.client.player.getGameProfile().name();
                  MinecraftClient.getInstance().inGameHud.getChatHud().addMessage(Text.literal("<" + name + "> " + event.message));
               }
            }

            ci.cancel();
         }
      }
   }

   @Redirect(
      method = {"method_45729"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/class_7610$class_7612;pack(Lnet/minecraft/class_7608;)Lnet/minecraft/class_7469;"
      ),
      require = 0
   )
   private MessageSignatureData revive$unsignChat(Packer packer, MessageBody body) {
      Config config = Config.get();
      return config == null || !config.unsignedChat.get() || this instanceof PbOldServerChat chat && chat.revive$keepsChatSigned() ? packer.pack(body) : null;
   }

   @Unique
   private Velocity revive$velocity() {
      return Modules.get().get(Velocity.class);
   }

   @Inject(
      method = {"method_11132"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void revive$onVelocityHead(EntityVelocityUpdateS2CPacket packet, CallbackInfo ci) {
      this.revive$preKnockbackVel = null;
      Velocity v = this.revive$velocity();
      if (v != null && v.isActive() && this.client.player != null && packet.getEntityId() == this.client.player.getId()) {
         ElytraDurabilitySaver saver = ElytraDurabilitySaver.get();
         if (!saver.isFlightVelocity(packet)) {
            if (!saver.passesVelocityAsSent(packet)) {
               Velocity.VelocityMode m = v.getMode();
               if (m == Velocity.VelocityMode.Good || v.shouldCancelKnockback()) {
                  if (m == Velocity.VelocityMode.Good) {
                     if (v.isKnockbackEnabled()) {
                        if (v.isKnockbackPhaseOnly()) {
                           if (v.isKnockbackPhaseInAir() && !this.client.player.isOnGround()) {
                              return;
                           }

                           if (!v.isPlayerPhased()) {
                              return;
                           }

                           if (v.isCancelPhasedKnockback()) {
                              ci.cancel();
                              this.client.player.setVelocity(0.0, 0.0, 0.0);
                              return;
                           }
                        }

                        Vec3d cur = this.client.player.getVelocity();
                        if (this.client.player.isCrawling()) {
                           ((EntityVelocityUpdateS2CPacketAccessor)packet).revive$setVelocity(cur);
                        } else {
                           Vec3d orig = packet.getVelocity();
                           double hMul = v.getHorizontalMultiplier();
                           double vMul = v.getVerticalMultiplier();
                           ((EntityVelocityUpdateS2CPacketAccessor)packet)
                              .revive$setVelocity(new Vec3d((orig.x - cur.x) * hMul + cur.x, (orig.y - cur.y) * vMul + cur.y, (orig.z - cur.z) * hMul + cur.z));
                        }
                     }
                  } else {
                     if (m == Velocity.VelocityMode.Cancel) {
                        if (v.isFullCancel()) {
                           ci.cancel();
                        } else {
                           Vec3d orig = packet.getVelocity();
                           double hMul = v.getHorizontalMultiplier();
                           double vMul = v.getVerticalMultiplier();
                           ((EntityVelocityUpdateS2CPacketAccessor)packet).revive$setVelocity(new Vec3d(orig.x * hMul, orig.y * vMul, orig.z * hMul));
                        }
                     } else if (m == Velocity.VelocityMode.Grim) {
                        if (v.isGrimCooldownReady()) {
                           this.revive$preKnockbackVel = this.client.player.getVelocity();
                           v.stampGrimCancel();
                        }
                     } else if (m == Velocity.VelocityMode.GrimV3) {
                        if (v.isPlayerPhased() || v.isCollidingWithEntities()) {
                           ci.cancel();
                        }
                     } else {
                        ci.cancel();
                        v.flagNcpPending();
                     }
                  }
               }
            }
         }
      }
   }

   @Inject(
      method = {"method_11132"},
      at = {@At("TAIL")},
      require = 0
   )
   private void revive$onVelocityTail(EntityVelocityUpdateS2CPacket packet, CallbackInfo ci) {
      Vec3d pre = this.revive$preKnockbackVel;
      this.revive$preKnockbackVel = null;
      if (pre != null && this.client.player != null) {
         Velocity v = this.revive$velocity();
         if (v != null) {
            Vec3d post = this.client.player.getVelocity();
            double hMul = v.getHorizontalMultiplier();
            double vMul = v.getVerticalMultiplier();
            this.client.player.setVelocity(pre.x + (post.x - pre.x) * hMul, pre.y + (post.y - pre.y) * vMul, pre.z + (post.z - pre.z) * hMul);
         }
      }
   }

   @Inject(
      method = {"method_11124"},
      at = {@At("HEAD")},
      require = 0
   )
   private void revive$onExplosionHead(ExplosionS2CPacket packet, CallbackInfo ci) {
      Velocity v = this.revive$velocity();
      this.revive$preExplosionVel = v != null && v.isActive() && v.shouldCancelExplosions() && this.client.player != null
         ? this.client.player.getVelocity()
         : null;
   }

   @Inject(
      method = {"method_11124"},
      at = {@At("TAIL")},
      require = 0
   )
   private void revive$onExplosionTail(ExplosionS2CPacket packet, CallbackInfo ci) {
      Vec3d pre = this.revive$preExplosionVel;
      this.revive$preExplosionVel = null;
      if (pre != null && this.client.player != null) {
         Velocity v = this.revive$velocity();
         Vec3d post = this.client.player.getVelocity();
         double hMul = v != null ? v.getHorizontalMultiplier() : 0.0;
         double vMul = v != null ? v.getVerticalMultiplier() : 0.0;
         this.client.player.setVelocity(pre.x + (post.x - pre.x) * hMul, pre.y + (post.y - pre.y) * vMul, pre.z + (post.z - pre.z) * hMul);
         if (v != null && v.isActive() && v.getMode() == Velocity.VelocityMode.NCP && !PbPredict.serverPushesOnly()) {
            v.flagNcpPending();
         }
      }
   }

   @Inject(
      method = {"method_11157"},
      at = {@At("HEAD")},
      require = 0
   )
   private void revive$onPosHead(PlayerPositionLookS2CPacket packet, CallbackInfo ci) {
      Velocity v = this.revive$velocity();
      this.revive$preMovePlayerVel = v != null && v.isActive() && v.shouldCancelKnockback() && this.client.player != null
         ? this.client.player.getVelocity()
         : null;
   }

   @Inject(
      method = {"method_11157"},
      at = {@At("TAIL")},
      require = 0
   )
   private void revive$onPosTail(PlayerPositionLookS2CPacket packet, CallbackInfo ci) {
      Vec3d pre = this.revive$preMovePlayerVel;
      this.revive$preMovePlayerVel = null;
      if (pre != null && this.client.player != null) {
         Velocity v = this.revive$velocity();
         if (v != null) {
            Vec3d post = this.client.player.getVelocity();
            double hMul = v.getHorizontalMultiplier();
            double vMul = v.getVerticalMultiplier();
            this.client.player.setVelocity(pre.x + (post.x - pre.x) * hMul, pre.y + (post.y - pre.y) * vMul, pre.z + (post.z - pre.z) * hMul);
         }
      }
   }

   @Inject(
      method = {"method_11157"},
      at = {@At("HEAD")},
      require = 0
   )
   private void revive$saverPosHead(PlayerPositionLookS2CPacket packet, CallbackInfo ci) {
      this.revive$preSaverVel = null;
      this.revive$preSaverPos = null;
      if (this.client.player != null) {
         ElytraDurabilitySaver saver = ElytraDurabilitySaver.get();
         saver.onServerPositionApplying();
         if (saver.keepsVelocityThroughSetbacks()) {
            this.revive$preSaverVel = this.client.player.getVelocity();
            if (saver.keepsPositionThroughSetbacks()) {
               this.revive$preSaverPos = this.client.player.getEntityPos();
            }
         }
      }
   }

   @Inject(
      method = {"method_11157"},
      at = {@At("TAIL")},
      require = 0
   )
   private void revive$saverPosTail(PlayerPositionLookS2CPacket packet, CallbackInfo ci) {
      Vec3d pre = this.revive$preSaverVel;
      Vec3d prePos = this.revive$preSaverPos;
      this.revive$preSaverVel = null;
      this.revive$preSaverPos = null;
      if (pre != null && this.client.player != null) {
         this.client.player.setVelocity(pre);
         ElytraDurabilitySaver saver = ElytraDurabilitySaver.get();
         saver.noteVelocityKeptByHook();
         if (prePos != null) {
            if (!(this.client.player.getEntityPos().distanceTo(prePos) >= ElytraDurabilitySaver.rejectionDistance())) {
               this.client.player.setPosition(prePos);
               this.client.player.resetPosition();
               saver.notePositionKeptByHook();
            }
         }
      }
   }

   @Inject(
      method = {"method_11157"},
      at = {@At("HEAD")},
      require = 0
   )
   private void revive$keepRotationHead(PlayerPositionLookS2CPacket packet, CallbackInfo ci) {
      this.revive$keepRotation = false;
      if (this.client.player != null) {
         boolean absYaw = !packet.relatives().contains(PositionFlag.Y_ROT);
         boolean absPitch = !packet.relatives().contains(PositionFlag.X_ROT);
         if (absYaw || absPitch) {
            if (NoRotate.on() || ReviveClient.ROTATION.silentRotationActiveWithin(10)) {
               float inYaw = packet.change().yaw();
               float inPitch = packet.change().pitch();
               float realYaw = this.client.player.getYaw();
               float realPitch = this.client.player.getPitch();
               if (!(Math.abs(MathHelper.wrapDegrees(inYaw - realYaw)) < 1.0F) || !(Math.abs(inPitch - realPitch) < 1.0F)) {
                  this.revive$keepRotation = true;
                  this.revive$keepYaw = realYaw;
                  this.revive$keepPitch = realPitch;
               }
            }
         }
      }
   }

   @Inject(
      method = {"method_11157"},
      at = {@At("TAIL")},
      require = 0
   )
   private void revive$keepRotationTail(PlayerPositionLookS2CPacket packet, CallbackInfo ci) {
      if (this.revive$keepRotation && this.client.player != null) {
         this.revive$keepRotation = false;
         this.client.player.setYaw(this.revive$keepYaw);
         this.client.player.setPitch(this.revive$keepPitch);
         this.client.player.lastYaw = this.revive$keepYaw;
         this.client.player.lastPitch = this.revive$keepPitch;
         ReviveClient.ROTATION.lastYaw = this.revive$keepYaw;
         ReviveClient.ROTATION.lastPitch = this.revive$keepPitch;
      }
   }

   @Inject(
      method = {"method_48295"},
      at = {@At("HEAD")},
      require = 0
   )
   private void revive$onHurtAnimation(DamageTiltS2CPacket packet, CallbackInfo ci) {
      if (this.client.player != null && packet.id() == this.client.player.getId()) {
         Velocity v = this.revive$velocity();
         if (v != null && v.isActive() && !PbPredict.serverPushesOnly()) {
            v.onHurtAnimation();
         }
      }
   }
}
