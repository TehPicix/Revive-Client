package revivedevelopment.reviveclient.systems.managers.team;

import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;

public final class TeamInfo {
   public final String id;
   public final String name;
   public final String leader;
   public final List<TeamInfo.Member> members;

   public TeamInfo(String id, String name, String leader, List<TeamInfo.Member> members) {
      this.id = id;
      this.name = name;
      this.leader = leader;
      this.members = members;
   }

   public String id() {
      return this.id;
   }

   public String name() {
      return this.name;
   }

   public String leader() {
      return this.leader;
   }

   public List<TeamInfo.Member> members() {
      return this.members;
   }

   public static final class Invite {
      public final String from;
      @Nullable
      public final UUID fromUuid;
      public final String team;

      public Invite(String from, @Nullable UUID fromUuid, String team) {
         this.from = from;
         this.fromUuid = fromUuid;
         this.team = team;
      }

      public String from() {
         return this.from;
      }

      @Nullable
      public UUID fromUuid() {
         return this.fromUuid;
      }

      public String team() {
         return this.team;
      }
   }

   public static final class Member {
      @Nullable
      public final UUID uuid;
      public final String name;
      public final boolean online;
      @Nullable
      public final String revive;

      public Member(@Nullable UUID uuid, String name, boolean online, @Nullable String revive) {
         this.uuid = uuid;
         this.name = name;
         this.online = online;
         this.revive = revive;
      }

      @Nullable
      public UUID uuid() {
         return this.uuid;
      }

      public String name() {
         return this.name;
      }

      public boolean online() {
         return this.online;
      }

      @Nullable
      public String revive() {
         return this.revive;
      }
   }
}
