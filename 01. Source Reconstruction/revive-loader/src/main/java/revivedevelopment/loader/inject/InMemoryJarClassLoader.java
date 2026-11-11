package revivedevelopment.loader.inject;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

public final class InMemoryJarClassLoader extends ClassLoader {
   private final Map<String, byte[]> classBytes = new HashMap<>();
   private final Map<String, byte[]> resources = new HashMap<>();

   public InMemoryJarClassLoader(byte[] jarBytes, ClassLoader parent) {
      super("revive-client", parent);
      this.index(jarBytes);
   }

   private void index(byte[] jarBytes) {
      try {
         JarEntry entry;
         try (JarInputStream jar = new JarInputStream(new ByteArrayInputStream(jarBytes))) {
            while ((entry = jar.getNextJarEntry()) != null) {
               if (!entry.isDirectory()) {
                  byte[] data = jar.readAllBytes();
                  String name = entry.getName();
                  if (name.endsWith(".class")) {
                     String className = name.substring(0, name.length() - ".class".length()).replace('/', '.');
                     this.classBytes.put(className, data);
                  } else {
                     this.resources.put(name, data);
                  }
               }
            }
         }
      } catch (IOException exception) {
         throw new IllegalStateException("Failed to read client jar from memory", exception);
      }
   }

   @Override
   protected Class<?> findClass(String name) throws ClassNotFoundException {
      byte[] data = this.classBytes.remove(name);
      if (data == null) {
         throw new ClassNotFoundException(name);
      } else {
         Class defined;
         try {
            defined = this.defineClass(name, data, 0, data.length);
         } finally {
            Arrays.fill(data, (byte)0);
         }

         return defined;
      }
   }

   @Override
   public InputStream getResourceAsStream(String name) {
      byte[] data = this.resources.get(name);
      return (InputStream)(data != null ? new ByteArrayInputStream(data) : super.getResourceAsStream(name));
   }

   public int classCount() {
      return this.classBytes.size();
   }

   static {
      registerAsParallelCapable();
   }
}
