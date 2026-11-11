package revivedevelopment.loader.inject;

import java.lang.reflect.Method;

public final class ClientInjector {
   private static final String DEFAULT_BOOTSTRAP = "revivedevelopment.client.ReviveBootstrap#init";

   private ClientInjector() {
   }

   public static int inject(byte[] clientJar) {
      InMemoryJarClassLoader loader = new InMemoryJarClassLoader(clientJar, ClientInjector.class.getClassLoader());
      String bootstrap = System.getProperty("revive.loader.bootstrap", "revivedevelopment.client.ReviveBootstrap#init");
      int hash = bootstrap.indexOf(35);
      String className = hash >= 0 ? bootstrap.substring(0, hash) : bootstrap;
      String methodName = hash >= 0 ? bootstrap.substring(hash + 1) : "init";

      try {
         Class<?> entry = Class.forName(className, true, loader);
         Method method = entry.getDeclaredMethod(methodName);
         method.setAccessible(true);
         method.invoke(null);
         return loader.classCount();
      } catch (ClassNotFoundException exception) {
         throw new IllegalStateException(
            "Client bootstrap class not found: " + className + " (set -Drevive.loader.bootstrap=Class#method to match your client)", exception
         );
      } catch (NoSuchMethodException exception) {
         throw new IllegalStateException("Client bootstrap method not found: " + className + "#" + methodName + "()", exception);
      } catch (ReflectiveOperationException exception) {
         throw new IllegalStateException("Client bootstrap invocation failed", exception);
      }
   }
}
