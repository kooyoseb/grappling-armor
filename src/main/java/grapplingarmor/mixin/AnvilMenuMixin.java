package grapplingarmor.mixin;

import grapplingarmor.GrapplingArmor;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AnvilMenu.class)
public abstract class AnvilMenuMixin {
	@Shadow
	@Final
	private DataSlot cost;

	@Shadow
	private int repairItemCountCost;

	@Inject(method = "createResult", at = @At("HEAD"), cancellable = true)
	private void grapplingArmor$createRangeUpgrade(CallbackInfo info) {
		ItemCombinerMenuAccessor combiner = (ItemCombinerMenuAccessor) this;
		ItemStack base = combiner.grapplingArmor$getInputSlots().getItem(0);
		ItemStack addition = combiner.grapplingArmor$getInputSlots().getItem(1);
		if (!GrapplingArmor.canUpgradeRange(base) || !addition.is(GrapplingArmor.GRAPPLING_PART)) {
			return;
		}
		int currentLevel = GrapplingArmor.getRangeLevel(base);
		if (currentLevel >= GrapplingArmor.MAX_RANGE_LEVEL) {
			combiner.grapplingArmor$getResultSlots().setItem(0, ItemStack.EMPTY);
			cost.set(0);
			((AnvilMenu) (Object) this).broadcastChanges();
			info.cancel();
			return;
		}

		ItemStack result = GrapplingArmor.upgradedRangeCopy(base);
		combiner.grapplingArmor$getResultSlots().setItem(0, result);
		repairItemCountCost = 1;
		cost.set(4 + currentLevel * 2);
		((AnvilMenu) (Object) this).broadcastChanges();
		info.cancel();
	}
}
