package revivedevelopment.reviveclient.systems.modules.player;

import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.component.ComponentMap;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.events.world.TickEvent;
import revivedevelopment.reviveclient.mixin.HandledScreenAccessor;
import revivedevelopment.reviveclient.settings.BoolSetting;
import revivedevelopment.reviveclient.settings.ColorSetting;
import revivedevelopment.reviveclient.settings.IntSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.systems.managers.PacketManager;
import revivedevelopment.reviveclient.systems.modules.Categories;
import revivedevelopment.reviveclient.systems.modules.Module;
import revivedevelopment.reviveclient.utils.Utils;
import revivedevelopment.reviveclient.utils.render.color.SettingColor;

public class InventorySearch extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Boolean> dimOthers = this.sgGeneral
      .add(new BoolSetting.Builder().name("dim-others").description("Darken the slots that don't match while a search is typed.").defaultValue(true).build());
   private final Setting<Boolean> searchContainers = this.sgGeneral
      .add(new BoolSetting.Builder().name("search-shulkers").description("Match shulker boxes and bundles by what they contain.").defaultValue(true).build());
   private final Setting<Boolean> autoFocus = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("auto-focus")
            .description("Start typing straight away when a container opens. Off: click the box or press Ctrl+F.")
            .defaultValue(false)
            .build()
      );
   private final Setting<Boolean> keepQuery = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("keep-search")
            .description("Remember the search between containers instead of clearing it when one closes.")
            .defaultValue(true)
            .build()
      );
   private final Setting<SettingColor> matchColor = this.sgGeneral
      .add(
         new ColorSetting.Builder()
            .name("match-color")
            .description("Highlight behind matching slots.")
            .defaultValue(new SettingColor(60, 220, 100, 110))
            .build()
      );
   private final Setting<Integer> dimOpacity = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("dim-opacity")
            .description("How dark the non-matching slots go.")
            .defaultValue(150)
            .range(0, 230)
            .sliderRange(0, 230)
            .visible(this.dimOthers::get)
            .build()
      );
   private final Setting<Boolean> moveButtons = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("take-dump-buttons")
            .description(
               "Show Take and Dump buttons next to the search box in chests, shulkers and other containers. With a search typed they only move matching items."
            )
            .defaultValue(true)
            .build()
      );
   private final Setting<Integer> moveDelay = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("move-delay")
            .description("Ticks between each stack Take or Dump moves. 0 moves one stack every tick (still kept within the click-packet budget).")
            .defaultValue(1)
            .range(0, 10)
            .sliderRange(0, 10)
            .visible(this.moveButtons::get)
            .build()
      );
   private static final int BOX_HEIGHT = 12;
   private static final int BTN_W = 34;
   private InventorySearch.Move move = InventorySearch.Move.NONE;
   private int moveTimer;
   private ScreenHandler moveHandler;
   private final Set<Integer> moveTried = new HashSet<>();
   private String query = "";
   private String queryLower = "";
   private boolean focused;
   private HandledScreen<?> lastScreen;
   private final Map<ItemStack, Boolean> frameCache = new IdentityHashMap<>();
   private long frameCacheFrame = -1L;
   private long frame;

   public InventorySearch() {
      super(Categories.Player, "inventory-search", "Search box on container screens that highlights matching items.");
   }

   @Override
   public void onDeactivate() {
      this.move = InventorySearch.Move.NONE;
      this.focused = false;
      this.lastScreen = null;
   }

   public boolean hasQuery() {
      return this.isActive() && !this.queryLower.isEmpty();
   }

   private int boxX(HandledScreen<?> screen) {
      return ((HandledScreenAccessor)screen).getX();
   }

   private int boxY(HandledScreen<?> screen) {
      return ((HandledScreenAccessor)screen).getY() - 12 - 2;
   }

   private int boxW(HandledScreen<?> screen) {
      int w = ((HandledScreenAccessor)screen).getBackgroundWidth();
      return this.showButtons(screen) ? w - 72 : w;
   }

   private boolean showButtons(HandledScreen<?> screen) {
      ScreenHandler h = screen.getScreenHandler();
      return this.moveButtons.get() && !(h instanceof PlayerScreenHandler) && this.hasContainerSlots(h);
   }

   private boolean hasContainerSlots(ScreenHandler h) {
      if (this.mc.player == null) {
         return false;
      } else {
         PlayerInventory inv = this.mc.player.getInventory();

         for (Slot s : h.slots) {
            if (s.inventory != inv) {
               return true;
            }
         }

         return false;
      }
   }

   private int takeX(HandledScreen<?> screen) {
      return this.boxX(screen) + this.boxW(screen) + 2;
   }

   private int dumpX(HandledScreen<?> screen) {
      return this.takeX(screen) + 34 + 2;
   }

   private boolean inRect(double mx, double my, int x, int y, int w, int h) {
      return mx >= x && mx < x + w && my >= y && my < y + h;
   }

   private boolean inBox(HandledScreen<?> screen, double mx, double my) {
      int x = this.boxX(screen);
      int y = this.boxY(screen);
      return mx >= x && mx < x + this.boxW(screen) && my >= y && my < y + 12;
   }

   public void onRender(HandledScreen<?> screen, DrawContext context, int mouseX, int mouseY) {
      this.frame++;
      if (screen != this.lastScreen) {
         this.lastScreen = screen;
         this.focused = this.autoFocus.get();
         if (!this.keepQuery.get()) {
            this.setQuery("");
         }
      }

      int x = this.boxX(screen);
      int y = this.boxY(screen);
      int w = this.boxW(screen);
      if (y < 0) {
         y = 0;
      }

      boolean hover = this.inBox(screen, mouseX, mouseY);
      context.fill(x, y, x + w, y + 12, -1073741824);
      int border = this.focused ? -1 : (hover ? -5592406 : -11184811);
      context.fill(x, y, x + w, y + 1, border);
      context.fill(x, y + 12 - 1, x + w, y + 12, border);
      context.fill(x, y, x + 1, y + 12, border);
      context.fill(x + w - 1, y, x + w, y + 12, border);
      TextRenderer tr = this.mc.textRenderer;
      int textY = y + (12 - 9) / 2 + 1;
      if (this.showButtons(screen)) {
         this.drawButton(context, tr, this.takeX(screen), y, "Take", mouseX, mouseY, this.move == InventorySearch.Move.TAKE);
         this.drawButton(context, tr, this.dumpX(screen), y, "Dump", mouseX, mouseY, this.move == InventorySearch.Move.DUMP);
      }

      if (this.query.isEmpty() && !this.focused) {
         context.drawText(tr, "Search...", x + 4, textY, -8947849, false);
      } else {
         String shown = this.query;
         int maxW = w - 8;

         while (!shown.isEmpty() && tr.getWidth(shown) > maxW) {
            shown = shown.substring(1);
         }

         context.drawText(tr, shown, x + 4, textY, -1, false);
         if (this.focused && System.currentTimeMillis() / 500L % 2L == 0L) {
            int cx = x + 4 + tr.getWidth(shown);
            context.fill(cx, textY - 1, cx + 1, textY + 9, -1);
         }
      }
   }

   private void drawButton(DrawContext context, TextRenderer tr, int x, int y, String label, int mx, int my, boolean running) {
      boolean hover = this.inRect(mx, my, x, y, 34, 12);
      context.fill(x, y, x + 34, y + 12, running ? -1071624160 : (hover ? -1070583760 : -1073741824));
      int border = running ? -10429088 : (hover ? -5592406 : -11184811);
      context.fill(x, y, x + 34, y + 1, border);
      context.fill(x, y + 12 - 1, x + 34, y + 12, border);
      context.fill(x, y, x + 1, y + 12, border);
      context.fill(x + 34 - 1, y, x + 34, y + 12, border);
      context.drawText(tr, label, x + (34 - tr.getWidth(label)) / 2, y + (12 - 9) / 2 + 1, -1, false);
   }

   public void onDrawSlotPre(DrawContext context, Slot slot) {
      if (this.hasQuery()) {
         ItemStack stack = slot.getStack();
         if (!stack.isEmpty() && this.matches(stack)) {
            SettingColor c = this.matchColor.get();
            int argb = (c.a & 0xFF) << 24 | (c.r & 0xFF) << 16 | (c.g & 0xFF) << 8 | c.b & 0xFF;
            context.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, argb);
         }
      }
   }

   public void onDrawSlotPost(DrawContext context, Slot slot) {
      if (this.hasQuery() && this.dimOthers.get()) {
         ItemStack stack = slot.getStack();
         if (stack.isEmpty() || !this.matches(stack)) {
            context.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, (this.dimOpacity.get() & 0xFF) << 24);
         }
      }
   }

   public boolean onMouseClicked(HandledScreen<?> screen, double mx, double my, int button) {
      if (this.showButtons(screen)) {
         int y = Math.max(0, this.boxY(screen));
         if (this.inRect(mx, my, this.takeX(screen), y, 34, 12)) {
            this.startMove(screen, InventorySearch.Move.TAKE);
            return true;
         }

         if (this.inRect(mx, my, this.dumpX(screen), y, 34, 12)) {
            this.startMove(screen, InventorySearch.Move.DUMP);
            return true;
         }
      }

      if (this.inBox(screen, mx, my)) {
         this.focused = true;
         if (button == 1) {
            this.setQuery("");
         }

         return true;
      } else {
         this.focused = false;
         return false;
      }
   }

   public boolean onKeyPressed(int key, int modifiers) {
      boolean ctrl = (modifiers & 2) != 0 || (modifiers & 8) != 0;
      if (!this.focused) {
         if (ctrl && key == 70) {
            this.focused = true;
            return true;
         } else {
            return false;
         }
      } else {
         switch (key) {
            case 65:
               if (ctrl) {
                  return true;
               }
               break;
            case 86:
               if (ctrl) {
                  String clip = this.mc.keyboard.getClipboard();
                  if (clip != null) {
                     this.setQuery(this.query + clip.replace('\n', ' '));
                  }

                  return true;
               }
               break;
            case 256:
               if (!this.query.isEmpty()) {
                  this.setQuery("");
               } else {
                  this.focused = false;
               }

               return true;
            case 257:
            case 258:
            case 335:
               this.focused = false;
               return true;
            case 259:
               if (!this.query.isEmpty()) {
                  if (ctrl) {
                     this.setQuery(this.query.replaceFirst("\\s*\\S+\\s*$", ""));
                  } else {
                     this.setQuery(this.query.substring(0, this.query.length() - 1));
                  }
               }

               return true;
         }

         return true;
      }
   }

   public boolean onCharTyped(char c) {
      if (!this.focused) {
         return false;
      } else if (c >= ' ' && c != 127) {
         if (this.query.length() < 64) {
            this.setQuery(this.query + c);
         }

         return true;
      } else {
         return true;
      }
   }

   private void startMove(HandledScreen<?> screen, InventorySearch.Move m) {
      if (this.move == m && this.moveHandler == screen.getScreenHandler()) {
         this.move = InventorySearch.Move.NONE;
      } else {
         this.move = m;
         this.moveHandler = screen.getScreenHandler();
         this.moveTried.clear();
         this.moveTimer = 0;
         this.focused = false;
      }
   }

   @EventHandler
   private void onTick(TickEvent.Pre event) {
      if (this.move != InventorySearch.Move.NONE) {
         if (this.mc.player == null
            || this.mc.interactionManager == null
            || this.mc.player.currentScreenHandler != this.moveHandler
            || !(this.mc.currentScreen instanceof HandledScreen)) {
            this.move = InventorySearch.Move.NONE;
         } else if (this.moveTimer > 0) {
            this.moveTimer--;
         } else if (ReviveClient.PACKET.canSend(PacketManager.Category.CLICK_SLOT)) {
            if (this.mc.player.currentScreenHandler.getCursorStack().isEmpty()) {
               PlayerInventory inv = this.mc.player.getInventory();
               boolean fromContainer = this.move == InventorySearch.Move.TAKE;

               for (Slot slot : this.moveHandler.slots) {
                  if (slot.inventory != inv == fromContainer
                     && slot.hasStack()
                     && !this.moveTried.contains(slot.id)
                     && (!this.hasQuery() || this.matches(slot.getStack()))) {
                     this.moveTried.add(slot.id);
                     this.mc.interactionManager.clickSlot(this.moveHandler.syncId, slot.id, 0, SlotActionType.QUICK_MOVE, this.mc.player);
                     this.moveTimer = this.moveDelay.get();
                     return;
                  }
               }

               this.move = InventorySearch.Move.NONE;
            }
         }
      }
   }

   private void setQuery(String q) {
      this.query = q;
      this.queryLower = q.trim().toLowerCase();
      this.frameCacheFrame = -1L;
   }

   private boolean matches(ItemStack stack) {
      if (this.frameCacheFrame != this.frame) {
         this.frameCache.clear();
         this.frameCacheFrame = this.frame;
      }

      Boolean cached = this.frameCache.get(stack);
      if (cached != null) {
         return cached;
      } else {
         boolean r = this.matchesUncached(stack, 0);
         this.frameCache.put(stack, r);
         return r;
      }
   }

   private boolean matchesUncached(ItemStack stack, int depth) {
      if (this.matchesDirect(stack)) {
         return true;
      } else {
         if (depth < 2 && this.searchContainers.get() && Utils.hasItems(stack)) {
            ItemStack[] inner = new ItemStack[27];
            Utils.getItemsInContainerItem(stack, inner);

            for (ItemStack s : inner) {
               if (s != null && !s.isEmpty() && this.matchesUncached(s, depth + 1)) {
                  return true;
               }
            }
         }

         return false;
      }
   }

   private boolean matchesDirect(ItemStack stack) {
      String q = this.queryLower;
      if (stack.getName().getString().toLowerCase().contains(q)) {
         return true;
      } else if (stack.getItem().getName().getString().toLowerCase().contains(q)) {
         return true;
      } else if (Registries.ITEM.getId(stack.getItem()).getPath().replace('_', ' ').contains(q)) {
         return true;
      } else {
         ComponentMap components = stack.getComponents();
         return components != null && !components.isEmpty() && components.toString().toLowerCase().contains(q);
      }
   }

   private static enum Move {
      NONE,
      TAKE,
      DUMP;
   }
}
