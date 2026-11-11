package revivedevelopment.reviveclient;

import java.util.Map;
import java.util.Set;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class BaritoneNativeCalls {
   private static final Logger LOG = LoggerFactory.getLogger("Revive Client/Baritone");
   public static final String NATIVE_OWNER = "dev/babbaj/pathfinder/NetherPathfinder";
   private static final String REPLACEMENT_OWNER = "revivedevelopment/reviveclient/utils/BaritoneOverworldPathfinder";
   public static final Map<String, String> ENTRY_POINTS = Map.of(
      "pathFind", "(JIIIIIIZZIZ)Ldev/babbaj/pathfinder/PathSegment;", "isVisible", "(JIDDDDDD)Z", "isVisibleMulti", "(JII[D[DZ)I", "raytrace", "(JII[D[D[Z[D)V"
   );
   public static final Set<String> OPTIONAL_ENTRY_POINTS = Set.of("raytrace");
   public static final Set<String> TARGET_CLASSES = Set.of(
      "baritone.process.elytra.NetherPathfinderContext",
      "baritone.process.elytra.ElytraBehavior",
      "baritone.process.elytra.ElytraBehavior$PathManager",
      "baritone.process.ElytraProcess"
   );

   private BaritoneNativeCalls() {
   }

   public static boolean isTarget(String dottedClassName) {
      return TARGET_CLASSES.contains(dottedClassName);
   }

   public static int rewrite(ClassNode klass) {
      int rewritten = 0;

      for (MethodNode method : klass.methods) {
         if (method.instructions != null) {
            for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
               if (insn.getOpcode() == 184 && insn instanceof MethodInsnNode call && call.owner.equals("dev/babbaj/pathfinder/NetherPathfinder")) {
                  String desc = ENTRY_POINTS.get(call.name);
                  if (desc != null && desc.equals(call.desc)) {
                     call.owner = "revivedevelopment/reviveclient/utils/BaritoneOverworldPathfinder";
                     call.itf = false;
                     rewritten++;
                  }
               }
            }
         }
      }

      if (rewritten > 0) {
         LOG.info("Rerouted {} native pathfinder call(s) in {} for the Overworld elytra unlock", rewritten, klass.name);
      }

      return rewritten;
   }
}
