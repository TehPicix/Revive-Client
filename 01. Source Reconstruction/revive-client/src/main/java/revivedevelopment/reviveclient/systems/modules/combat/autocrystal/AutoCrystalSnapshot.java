package revivedevelopment.reviveclient.systems.modules.combat.autocrystal;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import revivedevelopment.reviveclient.utils.entity.DamageUtil;

public record AutoCrystalSnapshot(
   long tickId, AutoCrystalSnapshot.Context ctx, BlockPos[] candidates, BlockPos[] approachCandidates, Set<BlockPos> preferredPos, Set<BlockPos> backedOffSpots
) {
   public record Context(
      AutoCrystalSnapshot.TargetData self,
      float selfHealth,
      List<AutoCrystalSnapshot.TargetData> enemies,
      List<Vec3d> chainCrystals,
      Set<BlockPos> ignored,
      DamageUtil.BlockRaycastProvider world,
      double minPlace,
      double maxPlace,
      double minBreak,
      double maxBreak,
      boolean noSelfPop
   ) {
      public static final double FACE_PLACE_MIN_DAMAGE = 1.0;
      private static final double CHAIN_RADIUS_SQ = 36.0;

      public float selfDamage(Vec3d crystalPos, DamageUtil.BlockRaycastProvider provider) {
         float total = DamageUtil.crystalDamage(this.self.profile(), this.self.pos(), this.self.box(), crystalPos, provider, null);

         for (Vec3d other : this.chainCrystals) {
            double d2 = other.squaredDistanceTo(crystalPos);
            if (!(d2 > 36.0) && !(d2 < 1.0E-4)) {
               total += DamageUtil.crystalDamage(this.self.profile(), this.self.pos(), this.self.box(), other, provider, null);
            }
         }

         return total;
      }

      public boolean rejectsSelf(float selfDmg, boolean breaking) {
         return selfDmg > (breaking ? this.maxBreak : this.maxPlace) ? true : this.noSelfPop && selfDmg + 1.5F >= this.selfHealth;
      }

      public float evaluate(Vec3d crystalPos, boolean breaking, DamageUtil.BlockRaycastProvider provider, AutoCrystalSnapshot.DebugInfo dbg) {
         if (this.rejectsSelf(this.selfDamage(crystalPos, provider), breaking)) {
            if (dbg != null) {
               dbg.dmgRejectedSelf++;
            }

            return -1.0F;
         } else {
            float total = 0.0F;
            boolean any = false;
            double min = breaking ? this.minBreak : this.minPlace;

            for (AutoCrystalSnapshot.TargetData t : this.enemies) {
               float dmg = DamageUtil.crystalDamage(t.profile(), t.pos(), t.box(), crystalPos, provider, this.ignored);
               if (!(dmg < (t.facePlace() ? Math.min(min, 1.0) : min))) {
                  total += dmg;
                  any = true;
               }
            }

            if (!any && dbg != null) {
               dbg.dmgRejectedMin++;
            }

            return any ? total : -1.0F;
         }
      }

      public float maxEnemyDamage(Vec3d crystalPos, DamageUtil.BlockRaycastProvider provider) {
         float best = 0.0F;

         for (AutoCrystalSnapshot.TargetData t : this.enemies) {
            best = Math.max(best, DamageUtil.crystalDamage(t.profile(), t.pos(), t.box(), crystalPos, provider, this.ignored));
         }

         return best;
      }

      public AutoCrystalSnapshot.Context withIgnored(Set<BlockPos> otherIgnored) {
         return new AutoCrystalSnapshot.Context(
            this.self,
            this.selfHealth,
            this.enemies,
            this.chainCrystals,
            otherIgnored,
            this.world,
            this.minPlace,
            this.maxPlace,
            this.minBreak,
            this.maxBreak,
            this.noSelfPop
         );
      }
   }

   public static final class DebugInfo {
      final long tickId;
      final long startNs = System.nanoTime();
      int targets;
      int candidatesTotal;
      int candidatesOutBreakRange;
      int dmgRejectedMin;
      int dmgRejectedSelf;
      int bestFound;

      public DebugInfo(long tickId) {
         this.tickId = tickId;
      }

      public String buildMessage(float totalMs) {
         return String.format(
            Locale.US,
            "AsyncCalc tick=%d time=%.3fms | enemies=%d | cand=%d (outside break reach=%d) | rej(min=%d self=%d) | best=%d",
            this.tickId,
            totalMs,
            this.targets,
            this.candidatesTotal,
            this.candidatesOutBreakRange,
            this.dmgRejectedMin,
            this.dmgRejectedSelf,
            this.bestFound
         );
      }

      public String summary() {
         return this.targets
            + "|"
            + this.candidatesTotal
            + "|"
            + this.candidatesOutBreakRange
            + "|"
            + this.dmgRejectedMin
            + "|"
            + this.dmgRejectedSelf
            + "|"
            + this.bestFound;
      }
   }

   public record TargetData(int id, Vec3d pos, Box box, DamageUtil.DamageProfile profile, boolean facePlace) {
   }
}
