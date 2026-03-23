package com.example.presidentstaxcollection.entity;

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
import net.minecraft.world.phys.AABB;

import java.util.List;

public class TaxCollectorEntity extends PathfinderMob {

    // -------------------------------------------------------------------------
    // CONFIG HARDCODEADA (MVP)
    // -------------------------------------------------------------------------
    private static final int TAX_AMOUNT = 1;
    private static final int PAYMENT_TIME = 200;

    // -------------------------------------------------------------------------
    // Estados
    // -------------------------------------------------------------------------
    public enum TaxState {
        IDLE,
        APPROACH,
        DEMAND_PAYMENT,
        WAITING,
        HOSTILE
    }

    private TaxState currentState = TaxState.IDLE;
    private Player targetPlayer = null;
    private int paymentTimer = 0;
    private boolean demandMessageSent = false;

    // -------------------------------------------------------------------------
    public TaxCollectorEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    // -------------------------------------------------------------------------
    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 40.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.ATTACK_DAMAGE, 6.0)
                .add(Attributes.FOLLOW_RANGE, 16.0);
    }

    // -------------------------------------------------------------------------
    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new TaxCollectorBehaviorGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.2, true));
        this.goalSelector.addGoal(7, new RandomStrollGoal(this, 0.6));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0f));
        this.goalSelector.addGoal(9, new RandomLookAroundGoal(this));
    }

    // -------------------------------------------------------------------------
    @Override
    public void tick() {
        super.tick();

        if (this.level().isClientSide) return;

        switch (currentState) {

            case IDLE -> {
                if (this.tickCount % 20 == 0) {
                    Player nearby = findNearestPlayer(12.0);
                    if (nearby != null) {
                        targetPlayer = nearby;
                        transitionTo(TaxState.APPROACH);
                    }
                }
            }

            case APPROACH -> {
                if (targetPlayer == null || !targetPlayer.isAlive()) {
                    transitionTo(TaxState.IDLE);
                    return;
                }

                double dist = this.distanceTo(targetPlayer);
                if (dist < 3.0) {
                    transitionTo(TaxState.DEMAND_PAYMENT);
                }
            }

            case DEMAND_PAYMENT -> {
                if (!demandMessageSent) {
                    sendDemandMessage();
                    demandMessageSent = true;
                }

                paymentTimer = PAYMENT_TIME;
                transitionTo(TaxState.WAITING);
            }

            case WAITING -> {
                if (targetPlayer == null || !targetPlayer.isAlive()) {
                    transitionTo(TaxState.IDLE);
                    return;
                }

                paymentTimer--;

                if (paymentTimer == PAYMENT_TIME / 2) {
                    targetPlayer.sendSystemMessage(
                            Component.literal("§e[Recaudador] §fTe quedan §c" + paymentTimer + " §fticks para pagar.")
                    );
                }

                if (paymentTimer <= 0) {
                    targetPlayer.sendSystemMessage(
                            Component.literal("§c[Recaudador] §f¡Tiempo agotado! ¡Pagarás con tu vida!")
                    );
                    transitionTo(TaxState.HOSTILE);
                }
            }

            case HOSTILE -> {
                if (targetPlayer != null && targetPlayer.isAlive()) {
                    this.setTarget(targetPlayer);
                } else {
                    transitionTo(TaxState.IDLE);
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {


        if (this.level().isClientSide) return InteractionResult.SUCCESS;

        if (currentState != TaxState.WAITING) {
            player.sendSystemMessage(Component.literal("§7[Recaudador] §fNo estoy cobrando en este momento."));
            return InteractionResult.CONSUME;
        }

        if (!player.equals(targetPlayer)) {
            player.sendSystemMessage(Component.literal("§7[Recaudador] §fNo es contigo."));
            return InteractionResult.CONSUME;
        }

        var heldItem = player.getItemInHand(hand);
        int required = TAX_AMOUNT;

        if (heldItem.is(Items.DIAMOND) && heldItem.getCount() >= required) {
            heldItem.shrink(required);

            player.sendSystemMessage(
                    Component.literal("§a[Recaudador] §fPago recibido. Por ahora... hasta la próxima.")
            );

            this.remove(RemovalReason.DISCARDED);

        } else {
            player.sendSystemMessage(
                    Component.literal("§c[Recaudador] §fNecesitas §e" + required + " diamante(s)§f.")
            );
        }

        return InteractionResult.CONSUME;
    }

    // -------------------------------------------------------------------------
    private void transitionTo(TaxState newState) {
        this.currentState = newState;
        this.demandMessageSent = false;
    }

    private void sendDemandMessage() {
        if (targetPlayer == null) return;

        targetPlayer.sendSystemMessage(
                Component.literal("§6[Recaudador] §fDebes pagar §e"
                        + TAX_AMOUNT + " diamante(s)§f. Tienes §c"
                        + PAYMENT_TIME + " segundos§f.")
        );

        targetPlayer.sendSystemMessage(
                Component.literal("§7Click derecho con diamantes para pagar.")
        );
    }

    private Player findNearestPlayer(double radius) {
        AABB box = this.getBoundingBox().inflate(radius);
        List<Player> players = this.level().getEntitiesOfClass(Player.class, box);

        Player closest = null;
        double minDist = Double.MAX_VALUE;

        for (Player p : players) {
            if (!p.isAlive() || p.isCreative() || p.isSpectator()) continue;

            double dist = this.distanceTo(p);
            if (dist < minDist) {
                minDist = dist;
                closest = p;
            }
        }

        return closest;
    }

    // -------------------------------------------------------------------------
    private static class TaxCollectorBehaviorGoal extends Goal {

        private final TaxCollectorEntity mob;

        public TaxCollectorBehaviorGoal(TaxCollectorEntity mob) {
            this.mob = mob;
        }

        @Override
        public boolean canUse() {
            return mob.currentState == TaxState.APPROACH && mob.targetPlayer != null;
        }

        @Override
        public boolean canContinueToUse() {
            return canUse() && mob.targetPlayer.isAlive();
        }

        @Override
        public void tick() {
            Player target = mob.targetPlayer;
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