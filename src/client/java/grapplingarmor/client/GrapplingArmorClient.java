package grapplingarmor.client;

import grapplingarmor.GrapplingArmor;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class GrapplingArmorClient implements ClientModInitializer {
	private static int dashComboTicks;

	@Override
	public void onInitializeClient() {
		EntityRenderers.register(GrapplingArmor.GRAPPLING_HOOK_ENTITY, ThrownItemRenderer::new);
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (dashComboTicks > 0) {
				dashComboTicks--;
			}
			while (client.options.keySprint.consumeClick()) {
				activateSwing(client);
			}
			while (client.options.keyShift.consumeClick()) {
				activateSuitMovement(client, 2.25D, 0.05D, "message.grappling-armor.dash");
				dashComboTicks = 20;
			}
			if (dashComboTicks > 0 && client.options.keyAttack.consumeClick() && client.player != null
					&& client.player.getMainHandItem().is(GrapplingArmor.GRAPPLING_TWIN_BLADE)) {
				activateSuitMovement(client, 1.15D, 0.08D, "message.grappling-armor.dash_combo");
			}
		});
	}

	private static void activateSwing(Minecraft client) {
		if (client.player == null || !GrapplingArmor.hasSuit(client.player)) {
			return;
		}
		double range = 32.0D;
		HitResult hit = client.player.pick(range, 0.0F, false);
		if (hit.getType() == HitResult.Type.MISS) {
			activateSuitMovement(client, 1.25D, 0.15D, "message.grappling-armor.swing");
			return;
		}
		Vec3 toAnchor = hit.getLocation().subtract(client.player.position());
		Vec3 current = client.player.getDeltaMovement();
		Vec3 tangent = current.subtract(toAnchor.normalize().scale(current.dot(toAnchor.normalize())));
		if (tangent.lengthSqr() < 0.04D) {
			tangent = client.player.getLookAngle().cross(new Vec3(0.0D, 1.0D, 0.0D));
		}
		Vec3 swing = tangent.normalize().scale(1.7D).add(toAnchor.normalize().scale(0.45D)).add(0.0D, 0.18D, 0.0D);
		client.player.setDeltaMovement(swing);
		client.player.resetFallDistance();
		client.player.sendOverlayMessage(Component.translatable("message.grappling-armor.swing"));
	}

	private static void activateSuitMovement(Minecraft client, double strength, double updraft, String messageKey) {
		if (client.player == null || !GrapplingArmor.hasSuit(client.player)) {
			return;
		}
		GrapplingArmor.launch(client.player, strength, updraft);
		client.player.sendOverlayMessage(Component.translatable(messageKey));
	}
}
