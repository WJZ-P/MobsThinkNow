package com.wjz.mobsthinknow.mixin.client;

import com.wjz.mobsthinknow.MobsThinkNow;
import com.wjz.mobsthinknow.ai.zombie.ZombieBodyLanguage;
import com.wjz.mobsthinknow.ai.zombie.ZombieProfession;
import com.wjz.mobsthinknow.ai.zombie.ZombieProfessionProfile;
import com.wjz.mobsthinknow.client.render.ZombieBodyActionRenderStateAccess;
import com.wjz.mobsthinknow.client.render.ZombieProfessionRenderStateAccess;
import com.wjz.mobsthinknow.config.ConfigManager;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.AbstractZombieRenderer;
import net.minecraft.client.renderer.entity.state.ZombieRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemUseAnimation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 为普通僵尸选择同步职业皮肤，并让持盾和进食手臂进入与玩家一致的基础使用姿势。 */
@Mixin(AbstractZombieRenderer.class)
public abstract class AbstractZombieRendererMixin {
	@Unique
	private static final Map<ZombieProfession, Identifier> mobsthinknow$PROFESSION_TEXTURES =
		mobsthinknow$createProfessionTextures();

	@Inject(
		method = "extractRenderState(Lnet/minecraft/world/entity/monster/zombie/Zombie;Lnet/minecraft/client/renderer/entity/state/ZombieRenderState;F)V",
		at = @At("TAIL")
	)
	private void mobsthinknow$copyProfessionToRenderState(
		final Zombie zombie,
		final ZombieRenderState state,
		final float partialTick,
		final org.spongepowered.asm.mixin.injection.callback.CallbackInfo callbackInfo
	) {
		((ZombieProfessionRenderStateAccess)state).mobsthinknow$setZombieProfession(
			ZombieProfessionProfile.get(zombie)
		);
		ZombieBodyLanguage.Snapshot action = ZombieBodyLanguage.snapshot(zombie, partialTick);
		((ZombieBodyActionRenderStateAccess)state).mobsthinknow$setBodyActionState(
			action.action(),
			action.elapsedTicks()
		);
	}

	@Inject(
		method = "getTextureLocation(Lnet/minecraft/client/renderer/entity/state/ZombieRenderState;)Lnet/minecraft/resources/Identifier;",
		at = @At("HEAD"),
		cancellable = true
	)
	private void mobsthinknow$selectProfessionTexture(
		final ZombieRenderState state,
		final CallbackInfoReturnable<Identifier> callbackInfo
	) {
		if (!ConfigManager.get().zombieProfessionSkins) {
			return;
		}
		ZombieProfession profession = ((ZombieProfessionRenderStateAccess)state).mobsthinknow$getZombieProfession();
		Identifier texture = mobsthinknow$PROFESSION_TEXTURES.get(profession);
		if (texture != null) {
			callbackInfo.setReturnValue(texture);
		}
	}

	@Unique
	private static Map<ZombieProfession, Identifier> mobsthinknow$createProfessionTextures() {
		EnumMap<ZombieProfession, Identifier> textures = new EnumMap<>(ZombieProfession.class);
		for (ZombieProfession profession : ZombieProfession.values()) {
			String textureName = profession.textureName();
			if (textureName != null) {
				textures.put(profession, Identifier.fromNamespaceAndPath(
					MobsThinkNow.MOD_ID,
					"textures/entity/zombie/profession/" + textureName + ".png"
				));
			}
		}
		return Map.copyOf(textures);
	}

	@Inject(method = "getArmPose", at = @At("HEAD"), cancellable = true)
	private void mobsthinknow$selectShieldBlockingPose(
		final Zombie zombie,
		final HumanoidArm arm,
		final CallbackInfoReturnable<HumanoidModel.ArmPose> callbackInfo
	) {
		if (zombie.getType() != EntityType.ZOMBIE || !zombie.isUsingItem()) {
			return;
		}

		HumanoidArm usingArm = zombie.getUsedItemHand() == InteractionHand.MAIN_HAND
			? zombie.getMainArm()
			: zombie.getMainArm().getOpposite();
		if (arm != usingArm) {
			return;
		}
		if (zombie.getUseItem().has(DataComponents.BLOCKS_ATTACKS)) {
			callbackInfo.setReturnValue(HumanoidModel.ArmPose.BLOCK);
			return;
		}
		ItemUseAnimation animation = zombie.getUseItem().getUseAnimation();
		if (zombie.getUseItem().has(DataComponents.FOOD)
			&& (animation == ItemUseAnimation.EAT || animation == ItemUseAnimation.DRINK)) {
			callbackInfo.setReturnValue(HumanoidModel.ArmPose.ITEM);
		}
	}
}
