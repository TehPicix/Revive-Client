package revivedevelopment.reviveclient.events.game;

import revivedevelopment.reviveclient.events.entity.EntityAddedEvent;
import revivedevelopment.reviveclient.events.entity.EntityRemovedEvent;
import revivedevelopment.reviveclient.events.entity.PlayerDeathEvent;
import revivedevelopment.reviveclient.events.entity.player.AttackEntityEvent;
import revivedevelopment.reviveclient.events.entity.player.PostAttackEntityEvent;

public class GameLeftEvent {
   private static final GameLeftEvent INSTANCE = new GameLeftEvent();

   public static GameLeftEvent get() {
      EntityAddedEvent.release();
      EntityRemovedEvent.release();
      AttackEntityEvent.release();
      PostAttackEntityEvent.release();
      PlayerDeathEvent.release();
      return INSTANCE;
   }
}
