package revivedevelopment.reviveclient.utils.misc.text;

public class RunnableClickEvent extends ReviveClickEvent {
   public final Runnable runnable;

   public RunnableClickEvent(Runnable runnable) {
      super(null);
      this.runnable = runnable;
   }
}
