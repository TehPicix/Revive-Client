package revivedevelopment.reviveclient.mixin.pingbypass;

import net.minecraft.client.gui.screen.ingame.RecipeBookScreen;
import net.minecraft.client.gui.screen.recipebook.RecipeBookWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({RecipeBookScreen.class})
public interface RecipeBookScreenAccessor {
   @Accessor("field_54474")
   RecipeBookWidget<?> revive$getRecipeBook();
}
