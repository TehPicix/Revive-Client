package revivedevelopment.reviveclient;

import java.util.List;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public class MixinPlugin implements IMixinConfigPlugin {
   private static final String mixinPackage = "revivedevelopment.reviveclient.mixin";
   private static boolean loaded;
   private static boolean isOriginsPresent;
   private static boolean isIndigoPresent;
   public static boolean isSodiumPresent;
   private static boolean isLithiumPresent;
   public static boolean isIrisPresent;
   private static boolean isVFPPresent;
   private static boolean isBaritonePresent;

   public void onLoad(String mixinPackage) {
      if (!loaded) {
         isIndigoPresent = FabricLoader.getInstance().isModLoaded("fabric-renderer-indigo");
         isOriginsPresent = FabricLoader.getInstance().isModLoaded("origins");
         isSodiumPresent = FabricLoader.getInstance().isModLoaded("sodium");
         isLithiumPresent = FabricLoader.getInstance().isModLoaded("lithium");
         isIrisPresent = FabricLoader.getInstance().isModLoaded("iris");
         isVFPPresent = FabricLoader.getInstance().isModLoaded("viafabricplus");
         isBaritonePresent = FabricLoader.getInstance().isModLoaded("baritone");
         loaded = true;
      }
   }

   public String getRefMapperConfig() {
      return null;
   }

   public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
      if (!mixinClassName.startsWith("revivedevelopment.reviveclient.mixin")) {
         throw new RuntimeException("Mixin " + mixinClassName + " is not in the mixin package");
      } else if (mixinClassName.endsWith("PlayerEntityRendererMixin")) {
         return !isOriginsPresent;
      } else if (mixinClassName.startsWith("revivedevelopment.reviveclient.mixin.sodium")) {
         return isSodiumPresent;
      } else if (mixinClassName.startsWith("revivedevelopment.reviveclient.mixin.indigo")) {
         return isIndigoPresent;
      } else if (mixinClassName.startsWith("revivedevelopment.reviveclient.mixin.lithium")) {
         return isLithiumPresent;
      } else if (mixinClassName.startsWith("revivedevelopment.reviveclient.mixin.viafabricplus")) {
         return isVFPPresent;
      } else {
         return !mixinClassName.startsWith("revivedevelopment.reviveclient.mixin.baritone")
            ? true
            : isBaritonePresent && BaritoneMixinGuard.shouldApply(mixinClassName);
      }
   }

   public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
   }

   public List<String> getMixins() {
      return null;
   }

   public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
   }

   public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
      if (mixinClassName.startsWith("revivedevelopment.reviveclient.mixin.baritone") && BaritoneNativeCalls.isTarget(targetClassName)) {
         BaritoneNativeCalls.rewrite(targetClass);
      }
   }
}
