package com.mpisoni.presidentstaxcollection.entity;

import com.mpisoni.presidentstaxcollection.Config;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.server.level.ServerLevel;

/**
 * Entidad que se encarga de recaudar impuestos de los jugadores.
 * Implementa una máquina de estados para gestionar el proceso de cobro.
 */
public class TaxCollectorEntity extends PathfinderMob {

    public enum TaxState {
        IDLE,
        APPROACH,
        DEMAND_PAYMENT,
        WAITING,
        HOSTILE
    }

    private final TaxStateMachine stateMachine;

    public TaxCollectorEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.stateMachine = new TaxStateMachine(this);
    }

    /**
     * Definp los atributos base de la entidad.
     */
    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 40.0) //VIDA
                .add(Attributes.MOVEMENT_SPEED, 0.30) //RAPIDEZ
                .add(Attributes.ATTACK_DAMAGE, 7.0) //ATAQUE
                .add(Attributes.FOLLOW_RANGE, 16.0); //RANGO DE SEGUIMINETO
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new TaxCollectorBehaviorGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.2, true) {
            @Override
            public boolean canUse() {
                return getCurrentState() == TaxState.HOSTILE && super.canUse();
            }
        });
        this.goalSelector.addGoal(7, new RandomStrollGoal(this, 0.6));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0f));
        this.goalSelector.addGoal(9, new RandomLookAroundGoal(this));
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide) {
            stateMachine.tick();
        }
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (this.level().isClientSide) return InteractionResult.SUCCESS;

        if (getCurrentState() != TaxState.WAITING) {
            //se activa cuando el jugador intenta pagar despues del tiempo 
            player.sendSystemMessage(Component.translatable("chat.presidentstaxcollection.no_more_money"));
            return InteractionResult.CONSUME;
        }

        if (!player.equals(getTargetPlayer())) {
            player.sendSystemMessage(Component.translatable("chat.presidentstaxcollection.not_your_your_debt"));
            return InteractionResult.CONSUME;
        }

        var heldItem = player.getItemInHand(hand);
        int required = Config.taxAmount.get();

        if (heldItem.is(Items.DIAMOND) && heldItem.getCount() >= required) {
            heldItem.shrink(required);

            player.sendSystemMessage(
                    Component.translatable("chat.presidentstaxcollection.payment_received")
            );

            // Feedback visual y auditivo
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1.0f, 1.0f);
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.NEUTRAL, 0.5f, 1.2f);

            if (this.level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER, this.getX(), this.getY() + 1.5, this.getZ(), 10, 0.5, 0.5, 0.5, 0.05);
            }

            this.remove(RemovalReason.DISCARDED);
        } else {
            player.sendSystemMessage(
                Component.translatable(
                    "chat.presidentstaxcollection.need_diamonds",
                    required
                )
            );
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 1.0f, 1.0f);
        }

        return InteractionResult.CONSUME;
    }

    public TaxState getCurrentState() {
        return stateMachine.getCurrentState();
    }

    public Player getTargetPlayer() {
        return stateMachine.getTargetPlayer();
    }

    /**
     * Goal personalizado para que el recaudador siga al jugador durante la aproximación.
     */
    private static class TaxCollectorBehaviorGoal extends Goal {
        private final TaxCollectorEntity mob;

        public TaxCollectorBehaviorGoal(TaxCollectorEntity mob) {
            this.mob = mob;
        }

        @Override
        public boolean canUse() {
            return mob.getCurrentState() == TaxState.APPROACH && mob.getTargetPlayer() != null;
        }

        @Override
        public boolean canContinueToUse() {
            return canUse() && mob.getTargetPlayer().isAlive();
        }

        @Override
        public void tick() {
            Player target = mob.getTargetPlayer();
            if (target == null) return;

            mob.getNavigation().moveTo(target, 1.0);
            mob.getLookControl().setLookAt(target, 30f, 30f);
        }

        @Override
        public void stop() {
            mob.getNavigation().stop();
        }
    }
}