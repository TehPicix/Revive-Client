package revivedevelopment.reviveclient;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

public final class BaritoneMixinGuard {
   private static final String MIXIN_PACKAGE = "revivedevelopment.reviveclient.mixin.baritone.";
   private static final String BLOCK_POS = "net/minecraft/class_2338";
   private static final String WORLD = "net/minecraft/class_1937";
   private static final String REGISTRY_KEY = "net/minecraft/class_5321";
   private static final String WORLD_GET_REGISTRY_KEY = "method_27983";
   private static final String WORLD_CHUNK = "net/minecraft/class_2818";
   private static final String CHUNK_SECTION = "net/minecraft/class_2826";
   private static final String CHUNK_GET_SECTION_ARRAY = "method_12006";
   private static final String BLOCK = "net/minecraft/class_2248";
   private static final String BLOCKS = "net/minecraft/class_2246";
   private static final String BLOCKS_NETHERRACK = "field_10515";
   private static final Map<String, List<BaritoneMixinGuard.Requirement>> REQUIREMENTS = new LinkedHashMap<>();
   private static final Map<String, String> FAILURES = new LinkedHashMap<>();
   private static final Map<String, Boolean> RESULTS = new LinkedHashMap<>();
   private static final String SETTINGS_CLASS = "baritone.api.Settings";
   private static final List<String> NATIVE_OVERWORLD_SETTINGS = List.of("elytraOverworldFastMode", "elytraStandingTakeoff");
   private static Boolean nativeOverworld;
   private static final Map<String, ClassNode> CLASS_CACHE = new LinkedHashMap<>();

   private BaritoneMixinGuard() {
   }

   public static boolean shouldApply(String mixinClassName) {
      if (hasNativeOverworldElytra()) {
         return false;
      } else {
         String simple = mixinClassName.startsWith("revivedevelopment.reviveclient.mixin.baritone.")
            ? mixinClassName.substring("revivedevelopment.reviveclient.mixin.baritone.".length())
            : mixinClassName;
         return check(simple);
      }
   }

   public static synchronized boolean hasNativeOverworldElytra() {
      if (nativeOverworld != null) {
         return nativeOverworld;
      } else {
         boolean found = false;
         ClassNode settings = readClass("baritone.api.Settings");
         if (settings != null) {
            found = true;

            for (String name : NATIVE_OVERWORLD_SETTINGS) {
               boolean present = false;

               for (FieldNode field : settings.fields) {
                  if (field.name.equals(name)) {
                     present = true;
                     break;
                  }
               }

               if (!present) {
                  found = false;
                  break;
               }
            }
         }

         nativeOverworld = found;
         return found;
      }
   }

   public static boolean allHooksApplicable() {
      return failures().isEmpty();
   }

   public static synchronized List<String> failures() {
      List<String> out = new ArrayList<>();

      for (String mixin : REQUIREMENTS.keySet()) {
         if (!check(mixin)) {
            out.add(mixin + ": " + FAILURES.get(mixin));
         }
      }

      return Collections.unmodifiableList(out);
   }

   private static synchronized boolean check(String mixinSimpleName) {
      Boolean cached = RESULTS.get(mixinSimpleName);
      if (cached != null) {
         return cached;
      } else {
         List<BaritoneMixinGuard.Requirement> requirements = REQUIREMENTS.get(mixinSimpleName);
         boolean ok;
         if (requirements == null) {
            FAILURES.put(mixinSimpleName, "no requirements registered for this mixin");
            ok = false;
         } else {
            String failure = null;

            for (BaritoneMixinGuard.Requirement requirement : requirements) {
               failure = requirement.verify();
               if (failure != null) {
                  break;
               }
            }

            ok = failure == null;
            if (!ok) {
               FAILURES.put(mixinSimpleName, failure);
            }
         }

         RESULTS.put(mixinSimpleName, ok);
         return ok;
      }
   }

   private static synchronized ClassNode readClass(String className) {
      if (CLASS_CACHE.containsKey(className)) {
         return CLASS_CACHE.get(className);
      } else {
         ClassNode node = null;
         String resource = className.replace('.', '/') + ".class";

         try (InputStream in = BaritoneMixinGuard.class.getClassLoader().getResourceAsStream(resource)) {
            if (in != null) {
               node = new ClassNode();
               new ClassReader(in).accept(node, 6);
            }
         } catch (Throwable var8) {
            node = null;
         }

         CLASS_CACHE.put(className, node);
         return node;
      }
   }

   static {
      REQUIREMENTS.put(
         "ElytraProcessMixin",
         List.of(
            BaritoneMixinGuard.Requirement.invoke(
               "baritone.process.ElytraProcess",
               "a",
               "(Lnet/minecraft/class_2338;Z)V",
               "net/minecraft/class_1937",
               "method_27983",
               "()Lnet/minecraft/class_5321;"
            ),
            BaritoneMixinGuard.Requirement.intConstant("baritone.process.ElytraProcess", "pathTo", "(Lbaritone/api/pathing/goals/Goal;)V", 64),
            BaritoneMixinGuard.Requirement.getStatic(
               "baritone.process.ElytraProcess", "a", "(Lnet/minecraft/class_2248;)Z", "net/minecraft/class_2246", "field_10515"
            ),
            BaritoneMixinGuard.Requirement.intConstantBetween(
               "baritone.process.ElytraProcess",
               "onTick",
               "(ZZ)Lbaritone/api/process/PathingCommand;",
               "Above the landing spot, landing...",
               "bad landing spot, trying again...",
               15
            ),
            BaritoneMixinGuard.Requirement.intConstantAfter(
               "baritone.process.ElytraProcess",
               "onTick",
               "(ZZ)Lbaritone/api/process/PathingCommand;",
               "Not taking off, because elytra durability or fireworks are so low that I would immediately emergency land anyway.",
               31
            )
         )
      );
      REQUIREMENTS.put(
         "ElytraPathManagerMixin",
         List.of(
            BaritoneMixinGuard.Requirement.invoke(
               "baritone.process.elytra.ElytraBehavior$PathManager",
               "a",
               "(Ljava/util/List;J)V",
               "baritone/process/elytra/NetherPath",
               "a",
               "(I)Lbaritone/api/utils/BetterBlockPos;"
            )
         )
      );
      REQUIREMENTS.put(
         "ElytraCommandMixin",
         List.of(
            BaritoneMixinGuard.Requirement.invoke(
               "baritone.command.defaults.ElytraCommand",
               "execute",
               "(Ljava/lang/String;Lbaritone/api/command/argument/IArgConsumer;)V",
               "net/minecraft/class_1937",
               "method_27983",
               "()Lnet/minecraft/class_5321;"
            )
         )
      );
      List<BaritoneMixinGuard.Requirement> pathfinderContext = new ArrayList<>(
         List.of(
            BaritoneMixinGuard.Requirement.invoke(
               "baritone.process.elytra.NetherPathfinderContext",
               "a",
               "(Ljava/lang/ref/SoftReference;)V",
               "net/minecraft/class_2818",
               "method_12006",
               "()[Lnet/minecraft/class_2826;"
            ),
            BaritoneMixinGuard.Requirement.field("baritone.process.elytra.NetherPathfinderContext", "a", "J"),
            BaritoneMixinGuard.Requirement.invoke(
               "baritone.process.elytra.NetherPathfinderContext", "a", "(JLbaritone/api/utils/Pair;)V", "dev/babbaj/pathfinder/Octree", "setBlock", "(JIIIZ)V"
            )
         )
      );
      List<String> nativeCallers = new ArrayList<>(BaritoneNativeCalls.TARGET_CLASSES);
      BaritoneNativeCalls.ENTRY_POINTS.forEach((name, desc) -> {
         if (!BaritoneNativeCalls.OPTIONAL_ENTRY_POINTS.contains(name)) {
            pathfinderContext.add(BaritoneMixinGuard.Requirement.invokeAnywhere(nativeCallers, "dev/babbaj/pathfinder/NetherPathfinder", name, desc));
         }
      });
      REQUIREMENTS.put("NetherPathfinderContextMixin", List.copyOf(pathfinderContext));
      REQUIREMENTS.put("ElytraBehaviorMixin", List.of(BaritoneMixinGuard.Requirement.classPresent("baritone.process.elytra.ElytraBehavior")));
   }

   private record Requirement(
      String owner,
      BaritoneMixinGuard.Requirement.Kind kind,
      String name,
      String desc,
      String invokeOwner,
      String invokeName,
      String invokeDesc,
      int constant,
      String fromString,
      String untilString,
      List<String> owners
   ) {
      static BaritoneMixinGuard.Requirement invoke(String owner, String name, String desc, String invokeOwner, String invokeName, String invokeDesc) {
         return new BaritoneMixinGuard.Requirement(
            owner, BaritoneMixinGuard.Requirement.Kind.METHOD_INVOKES, name, desc, invokeOwner, invokeName, invokeDesc, 0, null, null, null
         );
      }

      static BaritoneMixinGuard.Requirement intConstant(String owner, String name, String desc, int constant) {
         return new BaritoneMixinGuard.Requirement(
            owner, BaritoneMixinGuard.Requirement.Kind.METHOD_INT_CONSTANT, name, desc, null, null, null, constant, null, null, null
         );
      }

      static BaritoneMixinGuard.Requirement intConstantBetween(String owner, String name, String desc, String fromString, String untilString, int constant) {
         return new BaritoneMixinGuard.Requirement(
            owner, BaritoneMixinGuard.Requirement.Kind.METHOD_INT_CONSTANT_BETWEEN, name, desc, null, null, null, constant, fromString, untilString, null
         );
      }

      static BaritoneMixinGuard.Requirement intConstantAfter(String owner, String name, String desc, String fromString, int constant) {
         return new BaritoneMixinGuard.Requirement(
            owner, BaritoneMixinGuard.Requirement.Kind.METHOD_INT_CONSTANT_BETWEEN, name, desc, null, null, null, constant, fromString, null, null
         );
      }

      static BaritoneMixinGuard.Requirement getStatic(String owner, String name, String desc, String fieldOwner, String fieldName) {
         return new BaritoneMixinGuard.Requirement(
            owner, BaritoneMixinGuard.Requirement.Kind.METHOD_GETSTATIC, name, desc, fieldOwner, fieldName, null, 0, null, null, null
         );
      }

      static BaritoneMixinGuard.Requirement field(String owner, String name, String desc) {
         return new BaritoneMixinGuard.Requirement(owner, BaritoneMixinGuard.Requirement.Kind.FIELD, name, desc, null, null, null, 0, null, null, null);
      }

      static BaritoneMixinGuard.Requirement classPresent(String owner) {
         return new BaritoneMixinGuard.Requirement(owner, BaritoneMixinGuard.Requirement.Kind.CLASS_PRESENT, null, null, null, null, null, 0, null, null, null);
      }

      static BaritoneMixinGuard.Requirement invokeAnywhere(List<String> owners, String invokeOwner, String invokeName, String invokeDesc) {
         return new BaritoneMixinGuard.Requirement(
            owners.getFirst(),
            BaritoneMixinGuard.Requirement.Kind.ANY_METHOD_INVOKES,
            null,
            null,
            invokeOwner,
            invokeName,
            invokeDesc,
            0,
            null,
            null,
            List.copyOf(owners)
         );
      }

      private boolean invokes(MethodNode method) {
         for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn instanceof MethodInsnNode call
               && call.owner.equals(this.invokeOwner)
               && call.name.equals(this.invokeName)
               && call.desc.equals(this.invokeDesc)) {
               return true;
            }
         }

         return false;
      }

      String verify() {
         if (this.kind == BaritoneMixinGuard.Requirement.Kind.ANY_METHOD_INVOKES) {
            boolean anyClass = false;

            for (String candidate : this.owners) {
               ClassNode node = BaritoneMixinGuard.readClass(candidate);
               if (node != null) {
                  anyClass = true;

                  for (MethodNode method : node.methods) {
                     if (method.instructions != null && this.invokes(method)) {
                        return null;
                     }
                  }
               }
            }

            return !anyClass
               ? "none of " + String.join(", ", this.owners) + " found in the installed Baritone"
               : "call " + this.invokeOwner + "." + this.invokeName + this.invokeDesc + " not found in any of " + String.join(", ", this.owners);
         } else {
            ClassNode node = BaritoneMixinGuard.readClass(this.owner);
            if (node == null) {
               return "class " + this.owner + " not found in the installed Baritone";
            } else if (this.kind == BaritoneMixinGuard.Requirement.Kind.CLASS_PRESENT) {
               return null;
            } else if (this.kind == BaritoneMixinGuard.Requirement.Kind.FIELD) {
               for (FieldNode field : node.fields) {
                  if (field.name.equals(this.name) && field.desc.equals(this.desc)) {
                     return null;
                  }
               }

               return "field " + this.name + " " + this.desc + " missing in " + this.owner;
            } else {
               MethodNode methodx = null;

               for (MethodNode candidatex : node.methods) {
                  if (candidatex.name.equals(this.name) && candidatex.desc.equals(this.desc)) {
                     methodx = candidatex;
                     break;
                  }
               }

               if (methodx == null) {
                  return "method " + this.name + this.desc + " missing in " + this.owner;
               } else {
                  boolean inSlice = this.kind != BaritoneMixinGuard.Requirement.Kind.METHOD_INT_CONSTANT_BETWEEN;
                  boolean sawFrom = false;

                  for (AbstractInsnNode insn = methodx.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                     if (this.kind == BaritoneMixinGuard.Requirement.Kind.METHOD_INT_CONSTANT_BETWEEN) {
                        if (insn instanceof LdcInsnNode ldc && ldc.cst instanceof String text) {
                           if (text.equals(this.fromString)) {
                              inSlice = true;
                              sawFrom = true;
                           } else if (this.untilString != null && text.equals(this.untilString) && sawFrom) {
                              break;
                           }
                        } else if (inSlice && insn instanceof IntInsnNode push && push.operand == this.constant) {
                           return null;
                        }
                     } else if (this.kind == BaritoneMixinGuard.Requirement.Kind.METHOD_INVOKES) {
                        if (insn instanceof MethodInsnNode call
                           && call.owner.equals(this.invokeOwner)
                           && call.name.equals(this.invokeName)
                           && call.desc.equals(this.invokeDesc)) {
                           return null;
                        }
                     } else if (this.kind == BaritoneMixinGuard.Requirement.Kind.METHOD_GETSTATIC) {
                        if (insn instanceof FieldInsnNode access
                           && access.getOpcode() == 178
                           && access.owner.equals(this.invokeOwner)
                           && access.name.equals(this.invokeName)) {
                           return null;
                        }
                     } else if (this.kind == BaritoneMixinGuard.Requirement.Kind.METHOD_INT_CONSTANT) {
                        if (insn instanceof IntInsnNode push && (push.getOpcode() == 16 || push.getOpcode() == 17) && push.operand == this.constant) {
                           return null;
                        }

                        if (insn instanceof LdcInsnNode ldcx && ldcx.cst instanceof Integer value && value == this.constant) {
                           return null;
                        }
                     }
                  }
                  return switch (this.kind) {
                     case METHOD_INVOKES -> "call "
                        + this.invokeOwner
                        + "."
                        + this.invokeName
                        + this.invokeDesc
                        + " not found in "
                        + this.owner
                        + "."
                        + this.name
                        + this.desc;
                     default -> "constant " + this.constant + " not found in " + this.owner + "." + this.name + this.desc;
                     case METHOD_INT_CONSTANT_BETWEEN -> this.untilString == null
                        ? "constant " + this.constant + " not found after \"" + this.fromString + "\" in " + this.owner + "." + this.name + this.desc
                        : "constant "
                           + this.constant
                           + " not found between \""
                           + this.fromString
                           + "\" and \""
                           + this.untilString
                           + "\" in "
                           + this.owner
                           + "."
                           + this.name
                           + this.desc;
                     case METHOD_GETSTATIC -> "field read "
                        + this.invokeOwner
                        + "."
                        + this.invokeName
                        + " not found in "
                        + this.owner
                        + "."
                        + this.name
                        + this.desc;
                  };
               }
            }
         }
      }

      static enum Kind {
         METHOD_INVOKES,
         METHOD_INT_CONSTANT,
         METHOD_INT_CONSTANT_BETWEEN,
         METHOD_GETSTATIC,
         FIELD,
         CLASS_PRESENT,
         ANY_METHOD_INVOKES;
      }
   }
}
