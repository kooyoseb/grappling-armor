package grapplingarmor;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorMaterials;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Consumer;

public class GrapplingArmor implements ModInitializer {
	public static final String MOD_ID = "grappling-armor";
	public static final String RANGE_LEVEL_KEY = "GrapplingArmorRangeLevel";
	private static final int GUIDEBOOK_LINES = 18;
	public static final int MAX_RANGE_LEVEL = 5;
	private static final double DEVICE_BASE_RANGE = 24.0D;
	private static final double SUIT_BASE_RANGE = 32.0D;
	private static final int GRAPPLE_ACTIVE_TICKS = 90;
	private static final double GRAPPLE_MIN_DISTANCE = 1.7D;
	public static final float HOOK_DAMAGE = 20.0F;
	private static final Map<UUID, GrappleState> ACTIVE_GRAPPLES = new HashMap<>();
	private static final ResourceKey<EquipmentAsset> GRAPPLING_EQUIPMENT_ASSET =
			ResourceKey.create(EquipmentAssets.ROOT_ID, id("grappling_suit"));

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	private static final ResourceKey<LootTable> VILLAGE_WEAPONSMITH = vanillaLoot("chests/village/village_weaponsmith");
	private static final ResourceKey<LootTable> SHIPWRECK_TREASURE = vanillaLoot("chests/shipwreck_treasure");
	private static final ResourceKey<LootTable> BURIED_TREASURE = vanillaLoot("chests/buried_treasure");
	private static final ResourceKey<LootTable> WOODLAND_MANSION = vanillaLoot("chests/woodland_mansion");

	public static final SoundEvent HOOK_SHOOT_SOUND = registerSound("hook_shoot");
	public static final SoundEvent HOOK_HIT_SOUND = registerSound("hook_hit");
	public static final SoundEvent SUIT_BOOST_SOUND = registerSound("suit_boost");

	public static final EntityType<GrapplingHookEntity> GRAPPLING_HOOK_ENTITY = Registry.register(
			BuiltInRegistries.ENTITY_TYPE,
			ResourceKey.create(Registries.ENTITY_TYPE, id("grappling_hook")),
			FabricEntityTypeBuilder.<GrapplingHookEntity>create(MobCategory.MISC, GrapplingHookEntity::new)
					.dimensions(EntityDimensions.fixed(0.35F, 0.35F))
					.trackable(64, 1, true)
					.disableSaving()
					.disableSummon()
					.build(ResourceKey.create(Registries.ENTITY_TYPE, id("grappling_hook")))
	);

	public static final ArmorMaterial GRAPPLING_ARMOR_MATERIAL = new ArmorMaterial(
			ArmorMaterials.NETHERITE.durability() * 20,
			ArmorMaterials.makeDefense(60, 120, 160, 60, 0),
			ArmorMaterials.NETHERITE.enchantmentValue(),
			ArmorMaterials.NETHERITE.equipSound(),
			ArmorMaterials.NETHERITE.toughness() * 20.0F,
			ArmorMaterials.NETHERITE.knockbackResistance(),
			ItemTags.REPAIRS_NETHERITE_ARMOR,
			GRAPPLING_EQUIPMENT_ASSET
	);

	public static final Item GRAPPLING_HOOK = register("grappling_hook",
			key -> new Item(new Item.Properties().setId(key).durability(256)));
	public static final Item GRAPPLING_HANDLE = register("grappling_handle",
			key -> new Item(new Item.Properties().setId(key)));
	public static final Item GRAPPLING_CONTROL_DEVICE = register("grappling_control_device",
			key -> new Item(new Item.Properties().setId(key).stacksTo(16)));
	public static final Item GRAPPLING_DEVICE = register("grappling_device",
			key -> new GrapplingDeviceItem(new Item.Properties().setId(key).durability(1024)));
	public static final Item GRAPPLING_CONTROL_CORE = register("grappling_control_core",
			key -> new Item(new Item.Properties().setId(key).fireResistant().stacksTo(1)));
	public static final Item GRAPPLING_CONTROL_ARMOR = register("grappling_control_armor",
			key -> new Item(new Item.Properties().setId(key).fireResistant().humanoidArmor(GRAPPLING_ARMOR_MATERIAL, ArmorType.CHESTPLATE)));
	public static final Item GRAPPLING_SUIT = register("grappling_suit",
			key -> new GrapplingSuitItem(new Item.Properties().setId(key).fireResistant().humanoidArmor(GRAPPLING_ARMOR_MATERIAL, ArmorType.CHESTPLATE)));
	public static final Item GRAPPLING_PART = register("grappling_part",
			key -> new Item(new Item.Properties().setId(key).stacksTo(16)));
	public static final Item GRAPPLING_TWIN_BLADE = register("grappling_twin_blade",
			key -> new GrapplingTwinBladeItem(new Item.Properties().setId(key).fireResistant().sword(ToolMaterial.NETHERITE, 37.0F, -2.0F)));
	public static final Item GRAPPLING_GUIDEBOOK = register("grappling_guidebook",
			key -> new GrapplingGuidebookItem(new Item.Properties().setId(key).stacksTo(1)));
	public static final CreativeModeTab GRAPPLING_TAB = Registry.register(
			BuiltInRegistries.CREATIVE_MODE_TAB,
			ResourceKey.create(Registries.CREATIVE_MODE_TAB, id("grappling_armor")),
			FabricCreativeModeTab.builder()
					.title(Component.translatable("itemGroup.grappling-armor.grappling_armor"))
					.icon(() -> new ItemStack(GRAPPLING_SUIT))
					.displayItems((parameters, output) -> {
						output.accept(GRAPPLING_GUIDEBOOK);
						output.accept(GRAPPLING_HOOK);
						output.accept(GRAPPLING_HANDLE);
						output.accept(GRAPPLING_CONTROL_DEVICE);
						output.accept(GRAPPLING_DEVICE);
						output.accept(GRAPPLING_CONTROL_CORE);
						output.accept(GRAPPLING_CONTROL_ARMOR);
						output.accept(GRAPPLING_SUIT);
						output.accept(GRAPPLING_PART);
						output.accept(GRAPPLING_TWIN_BLADE);
					})
					.build()
	);

	@Override
	public void onInitialize() {
		registerLoot();
		registerGuidebookGift();
		registerGrapplePhysics();
		registerSuitUseControls();
		LOGGER.info("Grappling Armor loaded");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	private static Item register(String path, Function<ResourceKey<Item>, Item> factory) {
		Identifier id = id(path);
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id);
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(key));
	}

	private static SoundEvent registerSound(String path) {
		Identifier id = id(path);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	private static ResourceKey<LootTable> vanillaLoot(String path) {
		return ResourceKey.create(Registries.LOOT_TABLE, Identifier.withDefaultNamespace(path));
	}

	private static void registerLoot() {
		LootTableEvents.MODIFY.register((key, tableBuilder, source, registries) -> {
			if (!source.isBuiltin()) {
				return;
			}
			if (VILLAGE_WEAPONSMITH.equals(key)) {
				addChance(tableBuilder, GRAPPLING_HOOK, 0.40F);
				addChance(tableBuilder, GRAPPLING_HANDLE, 0.50F);
				addChance(tableBuilder, GRAPPLING_CONTROL_DEVICE, 0.03F);
			}
			if (SHIPWRECK_TREASURE.equals(key)) {
				addChance(tableBuilder, GRAPPLING_CONTROL_DEVICE, 0.06F);
			}
			if (BURIED_TREASURE.equals(key)) {
				addChance(tableBuilder, GRAPPLING_CONTROL_DEVICE, 0.10F);
			}
			if (WOODLAND_MANSION.equals(key)) {
				addChance(tableBuilder, GRAPPLING_HOOK, 0.20F);
			}
		});
	}

	private static void registerGuidebookGift() {
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayer player = handler.getPlayer();
			if (!player.getInventory().contains(new ItemStack(GRAPPLING_GUIDEBOOK))) {
				player.addItem(new ItemStack(GRAPPLING_GUIDEBOOK));
			}
		});
	}

	private static void registerGrapplePhysics() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			Iterator<Map.Entry<UUID, GrappleState>> iterator = ACTIVE_GRAPPLES.entrySet().iterator();
			while (iterator.hasNext()) {
				Map.Entry<UUID, GrappleState> entry = iterator.next();
				ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
				GrappleState state = entry.getValue();
				if (player == null || player.isRemoved() || player.isSpectator() || state.ticksLeft <= 0) {
					iterator.remove();
					continue;
				}
				if (!tickGrapple(player, state)) {
					iterator.remove();
					continue;
				}
				entry.setValue(state.tickDown());
			}
		});
	}

	private static void registerSuitUseControls() {
		UseItemCallback.EVENT.register((player, level, hand) -> {
			if (!hasSuit(player)) {
				return InteractionResult.PASS;
			}
			ItemStack held = player.getItemInHand(hand);
			if (!held.isEmpty() && !held.is(GRAPPLING_TWIN_BLADE)) {
				return InteractionResult.PASS;
			}
			if (!level.isClientSide()) {
				ItemStack suit = player.getItemBySlot(EquipmentSlot.CHEST);
				fireGrapple(level, player, suit, SUIT_BASE_RANGE, 1.85D);
				player.getCooldowns().addCooldown(suit, 14);
			}
			return InteractionResult.SUCCESS;
		});
	}

	private static void addChance(LootTable.Builder tableBuilder, Item item, float chance) {
		tableBuilder.withPool(LootPool.lootPool()
				.setRolls(ConstantValue.exactly(1.0F))
				.when(LootItemRandomChanceCondition.randomChance(chance))
				.add(LootItem.lootTableItem(item)));
	}

	public static boolean hasSuit(Player player) {
		return player.getItemBySlot(EquipmentSlot.CHEST).is(GRAPPLING_SUIT);
	}

	public static void launch(Player player, double strength, double updraft) {
		Vec3 look = player.getLookAngle().normalize();
		player.setDeltaMovement(look.scale(strength).add(0.0D, updraft, 0.0D));
		player.resetFallDistance();
	}

	public static void fireGrapple(Level level, Player player, ItemStack stack, double baseRange, double fallbackStrength) {
		if (level instanceof ServerLevel serverLevel) {
			double range = baseRange * getRangeMultiplier(stack);
			GrapplingHookEntity hook = new GrapplingHookEntity(level, player, stack, range);
			Vec3 start = getWristOrigin(player, player.getEyePosition());
			Vec3 look = player.getLookAngle().normalize();
			hook.setPos(start);
			hook.shoot(look.x(), look.y(), look.z(), 3.4F, 0.05F);
			serverLevel.addFreshEntity(hook);
			spawnGrappleTrail(level, player, player.getEyePosition(), start.add(look.scale(2.0D)), 0.32D);
			level.playSound(null, player.blockPosition(), HOOK_SHOOT_SOUND, SoundSource.PLAYERS, 0.85F, 1.65F);
			return;
		}

		double range = baseRange * getRangeMultiplier(stack);
		Vec3 start = player.getEyePosition();
		Vec3 look = player.getLookAngle().normalize();
		Vec3 end = start.add(look.scale(range));
		HitResult blockHit = player.pick(range, 0.0F, false);
		EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
				level,
				player,
				start,
				end,
				player.getBoundingBox().expandTowards(look.scale(range)).inflate(1.0D),
				entity -> entity != player && entity.isAlive() && entity.isPickable() && !entity.isSpectator(),
				0.4F
		);

		HitResult hit = blockHit;
		if (entityHit != null && (blockHit.getType() == HitResult.Type.MISS
				|| start.distanceTo(entityHit.getLocation()) < start.distanceTo(blockHit.getLocation()))) {
			hit = entityHit;
		}

		if (hit.getType() == HitResult.Type.MISS) {
			spawnGrappleTrail(level, player, start, end);
			launch(player, fallbackStrength * getRangeMultiplier(stack), 0.28D);
			level.playSound(null, player.blockPosition(), HOOK_SHOOT_SOUND, SoundSource.PLAYERS, 0.65F, 1.45F);
			player.sendOverlayMessage(Component.translatable("message.grappling-armor.miss"));
			return;
		}

		spawnGrappleTrail(level, player, start, hit.getLocation());
		level.playSound(null, player.blockPosition(), HOOK_SHOOT_SOUND, SoundSource.PLAYERS, 0.85F, 1.65F);
		level.playSound(null, hit.getLocation().x(), hit.getLocation().y(), hit.getLocation().z(), HOOK_HIT_SOUND, SoundSource.PLAYERS, 0.8F, 1.25F);
		if (hit instanceof EntityHitResult targetHit && level instanceof ServerLevel serverLevel) {
			Entity target = targetHit.getEntity();
			target.hurtServer(serverLevel, player.damageSources().playerAttack(player), HOOK_DAMAGE);
			target.addDeltaMovement(player.position().subtract(target.position()).normalize().scale(0.45D));
		}

		Vec3 pull = hit.getLocation().subtract(player.position());
		double distance = Math.max(1.0D, pull.length());
		double strength = Math.min(3.4D, 1.15D + distance * 0.075D);
		player.setDeltaMovement(pull.normalize().scale(strength).add(0.0D, 0.25D, 0.0D));
		player.resetFallDistance();
		player.sendOverlayMessage(Component.translatable("message.grappling-armor.connected"));
	}

	public static void pullPlayerTo(Player player, Vec3 anchor) {
		beginGrapple(player, anchor);
		Vec3 pull = anchor.subtract(player.position());
		double distance = Math.max(1.0D, pull.length());
		double strength = Math.min(hasSuit(player) ? 2.15D : 1.75D, 0.65D + distance * 0.045D);
		player.setDeltaMovement(pull.normalize().scale(strength).add(0.0D, 0.25D, 0.0D));
		player.resetFallDistance();
		player.sendOverlayMessage(Component.translatable("message.grappling-armor.connected"));
	}

	private static void beginGrapple(Player player, Vec3 anchor) {
		double range = hasSuit(player) ? SUIT_BASE_RANGE : DEVICE_BASE_RANGE;
		if (player.getMainHandItem().is(GRAPPLING_DEVICE) || player.getMainHandItem().is(GRAPPLING_SUIT)) {
			range *= getRangeMultiplier(player.getMainHandItem());
		} else if (player.getOffhandItem().is(GRAPPLING_DEVICE) || player.getOffhandItem().is(GRAPPLING_SUIT)) {
			range *= getRangeMultiplier(player.getOffhandItem());
		}
		ACTIVE_GRAPPLES.put(player.getUUID(), new GrappleState(anchor, GRAPPLE_ACTIVE_TICKS, range + 8.0D, hasSuit(player)));
	}

	private static boolean tickGrapple(ServerPlayer player, GrappleState state) {
		Vec3 toAnchor = state.anchor.subtract(player.getEyePosition());
		double distance = toAnchor.length();
		if (distance < GRAPPLE_MIN_DISTANCE || distance > state.maxDistance) {
			return false;
		}

		Vec3 direction = toAnchor.normalize();
		Vec3 velocity = player.getDeltaMovement();
		Vec3 tangent = velocity.subtract(direction.scale(velocity.dot(direction)));
		Vec3 look = player.getLookAngle().normalize();
		Vec3 lookTangent = look.subtract(direction.scale(look.dot(direction)));
		double pullStrength = Math.min(state.suit ? 0.58D : 0.48D, 0.10D + distance * 0.026D);
		double tangentBoost = Math.min(state.suit ? 0.12D : 0.08D, tangent.length() * 0.045D);
		Vec3 nextVelocity = velocity.scale(0.94D)
				.add(direction.scale(pullStrength))
				.add(tangent.lengthSqr() > 0.001D ? tangent.normalize().scale(tangentBoost) : Vec3.ZERO)
				.add(0.0D, 0.035D, 0.0D);
		if (player.isSprinting() && lookTangent.lengthSqr() > 0.001D) {
			nextVelocity = nextVelocity.add(lookTangent.normalize().scale(state.suit ? 0.28D : 0.18D));
		}
		if (player.isShiftKeyDown()) {
			nextVelocity = nextVelocity.add(look.scale(state.suit ? 0.34D : 0.22D));
		}

		double maxSpeed = state.suit ? 3.45D : 2.85D;
		if (nextVelocity.length() > maxSpeed) {
			nextVelocity = nextVelocity.normalize().scale(maxSpeed);
		}

		player.setDeltaMovement(nextVelocity);
		player.resetFallDistance();
		player.hurtMarked = true;
		if (player.level() instanceof ServerLevel serverLevel && state.ticksLeft % 2 == 0) {
			spawnGrappleTrail(serverLevel, player, player.getEyePosition(), state.anchor, 0.28D);
		}
		return true;
	}

	private static void spawnGrappleTrail(Level level, Player player, Vec3 eyeStart, Vec3 target) {
		spawnGrappleTrail(level, player, eyeStart, target, 0.45D);
	}

	public static void spawnGrappleTrail(Level level, Player player, Vec3 eyeStart, Vec3 target, double spacing) {
		if (!(level instanceof ServerLevel serverLevel)) {
			return;
		}
		Vec3 origin = getWristOrigin(player, eyeStart);
		Vec3 segment = target.subtract(origin);
		double length = segment.length();
		if (length < 0.01D) {
			return;
		}
		ItemParticleOption chainLink = new ItemParticleOption(ParticleTypes.ITEM, Items.IRON_CHAIN);
		ItemParticleOption hookTip = new ItemParticleOption(ParticleTypes.ITEM, GRAPPLING_HOOK);
		Vec3 direction = segment.normalize();
		Vec3 side = direction.cross(new Vec3(0.0D, 1.0D, 0.0D));
		if (side.lengthSqr() < 0.001D) {
			side = Vec3.X_AXIS;
		} else {
			side = side.normalize();
		}
		double linkSpacing = Math.max(0.18D, spacing);
		int count = Math.max(1, (int) Math.ceil(length / linkSpacing));
		for (int i = 0; i <= count; i++) {
			double distance = Math.min(length, i * linkSpacing);
			double offset = (i % 2 == 0 ? 0.035D : -0.035D);
			Vec3 point = origin.add(direction.scale(distance)).add(side.scale(offset));
			serverLevel.sendParticles(chainLink, point.x(), point.y(), point.z(), 1, 0.0D, 0.0D, 0.0D, 0.0D);
			if (i % 6 == 0) {
				serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK, point.x(), point.y(), point.z(), 1, 0.01D, 0.01D, 0.01D, 0.0D);
			}
		}
		serverLevel.sendParticles(hookTip, target.x(), target.y(), target.z(), 4, 0.035D, 0.035D, 0.035D, 0.0D);
		serverLevel.sendParticles(ParticleTypes.END_ROD, target.x(), target.y(), target.z(), 2, 0.08D, 0.08D, 0.08D, 0.01D);
	}

	public static Vec3 getWristOrigin(Player player, Vec3 eyeStart) {
		Vec3 look = player.getLookAngle().normalize();
		Vec3 right = new Vec3(-look.z, 0.0D, look.x);
		if (right.lengthSqr() < 0.001D) {
			right = Vec3.X_AXIS;
		} else {
			right = right.normalize();
		}
		return eyeStart.add(right.scale(0.38D)).add(0.0D, -0.42D, 0.0D).add(look.scale(0.25D));
	}

	public static boolean canUpgradeRange(ItemStack stack) {
		return stack.is(GRAPPLING_DEVICE) || stack.is(GRAPPLING_SUIT);
	}

	public static int getRangeLevel(ItemStack stack) {
		CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
		return Math.max(0, Math.min(MAX_RANGE_LEVEL, data.copyTag().getIntOr(RANGE_LEVEL_KEY, 0)));
	}

	public static double getRangeMultiplier(ItemStack stack) {
		return 1.0D + getRangeLevel(stack) * 0.18D;
	}

	public static ItemStack upgradedRangeCopy(ItemStack stack) {
		ItemStack result = stack.copy();
		int level = Math.min(MAX_RANGE_LEVEL, getRangeLevel(result) + 1);
		CustomData.update(DataComponents.CUSTOM_DATA, result, tag -> tag.putInt(RANGE_LEVEL_KEY, level));
		return result;
	}

	private static void appendRangeTooltip(ItemStack stack, Consumer<Component> tooltip) {
		int level = getRangeLevel(stack);
		tooltip.accept(Component.translatable("tooltip.grappling-armor.range_level", level, MAX_RANGE_LEVEL));
	}

	private static void showGuidebook(Player player) {
		player.sendSystemMessage(Component.translatable("guide.grappling-armor.title").withStyle(ChatFormatting.GOLD));
		for (int i = 1; i <= GUIDEBOOK_LINES; i++) {
			player.sendSystemMessage(Component.translatable("guide.grappling-armor.line" + i).withStyle(ChatFormatting.GRAY));
		}
	}

	public static class GrapplingGuidebookItem extends Item {
		public GrapplingGuidebookItem(Properties properties) {
			super(properties);
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			if (!level.isClientSide()) {
				showGuidebook(player);
			}
			return InteractionResult.SUCCESS;
		}

		@Override
		public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
			tooltip.accept(Component.translatable("tooltip.grappling-armor.guidebook").withStyle(ChatFormatting.GRAY));
		}
	}

	public static class GrapplingDeviceItem extends Item {
		public GrapplingDeviceItem(Properties properties) {
			super(properties);
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			if (!level.isClientSide()) {
				ItemStack stack = player.getItemInHand(hand);
				fireGrapple(level, player, stack, DEVICE_BASE_RANGE, 1.55D);
				player.getCooldowns().addCooldown(player.getItemInHand(hand), 20);
			}
			return InteractionResult.SUCCESS;
		}

		@Override
		public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
			appendRangeTooltip(stack, tooltip);
		}
	}

	public static class GrapplingTwinBladeItem extends Item {
		public GrapplingTwinBladeItem(Properties properties) {
			super(properties);
		}

		@Override
		public void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
			if (attacker instanceof Player player && target.level() instanceof ServerLevel serverLevel) {
				double speed = player.getDeltaMovement().horizontalDistance();
				if (speed > 0.72D) {
					target.hurtServer(serverLevel, player.damageSources().playerAttack(player), 18.0F);
					target.addDeltaMovement(player.getLookAngle().normalize().scale(0.85D).add(0.0D, 0.25D, 0.0D));
					serverLevel.sendParticles(ParticleTypes.SWEEP_ATTACK, target.getX(), target.getY(0.5D), target.getZ(), 8, 0.35D, 0.25D, 0.35D, 0.02D);
					serverLevel.playSound(null, target.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.9F, 1.45F);
				}
			}
			super.postHurtEnemy(stack, target, attacker);
		}
	}

	public static class GrapplingSuitItem extends Item {
		public GrapplingSuitItem(Properties properties) {
			super(properties);
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			if (!level.isClientSide()) {
				ItemStack stack = player.getItemInHand(hand);
				fireGrapple(level, player, stack, SUIT_BASE_RANGE, 1.85D);
				player.getCooldowns().addCooldown(player.getItemInHand(hand), 14);
			}
			return InteractionResult.SUCCESS;
		}

		@Override
		public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
			appendRangeTooltip(stack, tooltip);
		}
	}

	private record GrappleState(Vec3 anchor, int ticksLeft, double maxDistance, boolean suit) {
		private GrappleState tickDown() {
			return new GrappleState(anchor, ticksLeft - 1, maxDistance, suit);
		}
	}
}
