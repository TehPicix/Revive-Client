package revivedevelopment.reviveclient.pingbypass;

import com.google.gson.JsonObject;
import revivedevelopment.reviveclient.events.render.Render2DEvent;
import revivedevelopment.reviveclient.events.render.Render3DEvent;

public interface PbRenderable {
   JsonObject writeRemoteRender();

   void readRemoteRender(JsonObject var1, PbFx.Clock var2);

   default void drawRemote3D(Render3DEvent event) {
   }

   default void drawRemote2D(Render2DEvent event) {
   }

   void clearRemote();
}
