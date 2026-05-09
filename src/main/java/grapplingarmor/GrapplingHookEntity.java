package grapplingarmor;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class GrapplingHookEntity extends ThrowableItemProjectile {
	private static final int MAX_STUCK_TICKS = 24;

	private double maxRange = 24.0D;
	private Vec3 origin = Vec3.ZERO;
	private Vec3 anchor = Vec3.ZERO;
	private int stuckTicks;
	private boolean stuck;

	public GrapplingHookEntity(EntityType<? extends GrapplingHookEntity> entityType, Level level) {
		super(entityType, level);
	}

	public GrapplingHookEntity(Level level, LivingEntity owner, ItemStack stack, double maxRange) {
		super(GrapplingArmor.GRAPPLING_HOOK_ENTITY, owner, level, new ItemStack(GrapplingArmor.GRAPPLING_HOOK));
		setOwner(owner);
		this.maxRange = maxRange;
		this.origin = owner.getEyePosition();
		setItem(new ItemStack(GrapplingArmor.GRAPPLING_HOOK));
	}

	@Override
	public void tick() {
		super.tick();
		Entity owner = getOwner();
		if (owner instanceof Player player) {
			GrapplingArmor.spawnGrappleTrail(level(), player, player.getEyePosition(), stuck ? anchor : position(), stuck ? 0.26D : 0.34D);
		}

		if (stuck) {
			setDeltaMovement(Vec3.ZERO);
			setPos(anchor);
			stuckTicks++;
			if (stuckTicks > MAX_STUCK_TICKS) {
				discard();
			}
			return;
		}

		if (tickCount > 80 || origin.distanceTo(position()) > maxRange) {
			discard();
		}
	}

	@Override
	protected void onHit(HitResult hitResult) {
		if (level().isClientSide()) {
			return;
		}
		if (!(getOwner() instanceof Player player)) {
			discard();
			return;
		}
		anchor = hitResult.getLocation();
		stuck = true;
		setDeltaMovement(Vec3.ZERO);
		setPos(anchor);

		if (level() instanceof ServerLevel serverLevel) {
			serverLevel.sendParticles(ParticleTypes.END_ROD, anchor.x(), anchor.y(), anchor.z(), 10, 0.14D, 0.14D, 0.14D, 0.03D);
		}
		level().playSound(null, anchor.x(), anchor.y(), anchor.z(), GrapplingArmor.HOOK_HIT_SOUND, SoundSource.PLAYERS, 0.85F, 1.25F);

		if (hitResult instanceof EntityHitResult entityHit && level() instanceof ServerLevel serverLevel) {
			Entity target = entityHit.getEntity();
			target.hurtServer(serverLevel, player.damageSources().playerAttack(player), GrapplingArmor.HOOK_DAMAGE);
			target.addDeltaMovement(player.position().subtract(target.position()).normalize().scale(0.45D));
		}
		GrapplingArmor.pullPlayerTo(player, anchor);
	}

	@Override
	protected Item getDefaultItem() {
		return GrapplingArmor.GRAPPLING_HOOK;
	}
}
