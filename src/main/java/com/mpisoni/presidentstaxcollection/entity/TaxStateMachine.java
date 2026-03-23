package com.mpisoni.presidentstaxcollection.entity;

import com.mpisoni.presidentstaxcollection.Config;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Gestiona la lógica de estados del cobro de impuestos de forma aislada.
 */
public class TaxStateMachine {
    private final TaxCollectorEntity mob;
    private TaxCollectorEntity.TaxState currentState = TaxCollectorEntity.TaxState.IDLE;
    private Player targetPlayer = null;
    private int paymentTimer = 0;
    private boolean demandMessageSent = false;

    public TaxStateMachine(TaxCollectorEntity mob) {
        this.mob = mob;
    }

    public void tick() {
        if (mob.level().isClientSide) return;

        switch (currentState) {
            case IDLE -> handleIdle();
            case APPROACH -> handleApproach();
            case DEMAND_PAYMENT -> handleDemandPayment();
            case WAITING -> handleWaiting();
            case HOSTILE -> handleHostile();
        }
    }

    private void handleIdle() {
        if (mob.tickCount % 20 == 0) {
            Player nearby = findNearestPlayer(12.0);
            if (nearby != null) {
                targetPlayer = nearby;
                transitionTo(TaxCollectorEntity.TaxState.APPROACH);
            }
        }
    }

    private void handleApproach() {
        if (targetPlayer == null || !targetPlayer.isAlive()) {
            transitionTo(TaxCollectorEntity.TaxState.IDLE);
            return;
        }

        if (mob.distanceTo(targetPlayer) < 3.0) {
            transitionTo(TaxCollectorEntity.TaxState.DEMAND_PAYMENT);
        }
    }

    private void handleDemandPayment() {
        if (!demandMessageSent) {
            sendDemandMessage();
            demandMessageSent = true;
        }
        paymentTimer = Config.taxPaymentTime.get();
        transitionTo(TaxCollectorEntity.TaxState.WAITING);
    }

    private void handleWaiting() {
        if (targetPlayer == null || !targetPlayer.isAlive()) {
            transitionTo(TaxCollectorEntity.TaxState.IDLE);
            return;
        }

        // Verificación de distancia
        if (mob.distanceTo(targetPlayer) > 16.0) {
            targetPlayer.sendSystemMessage(Component.literal("§c[El Presi] §f De la muerte y los impuetos no se salva nadie"));
            transitionTo(TaxCollectorEntity.TaxState.HOSTILE);
            return;
        }

        paymentTimer--;

        if (paymentTimer == Config.taxPaymentTime.get() / 2) {
            int secondsLeft = paymentTimer / 20;
            targetPlayer.sendSystemMessage(
                    Component.literal("§e[El Presi] §fTe quedan §c" + secondsLeft + " §fsegundos para pagar.")
            );
        }

        if (paymentTimer <= 0) {
            targetPlayer.sendSystemMessage(
                    Component.literal("§c[El Presi] §f AHH TE QUERES HACER EL PILLO VENI QUE TE ENSEÑO QUE LE HACEMOS A LOS PILLOS")
            );
            transitionTo(TaxCollectorEntity.TaxState.HOSTILE);
        }
    }

    private void handleHostile() {
        if (targetPlayer != null && targetPlayer.isAlive()) {
            mob.setTarget(targetPlayer);
        } else {
            mob.setTarget(null);
            transitionTo(TaxCollectorEntity.TaxState.IDLE);
        }
    }

    public void transitionTo(TaxCollectorEntity.TaxState newState) {
        this.currentState = newState;
        if (newState == TaxCollectorEntity.TaxState.IDLE || newState == TaxCollectorEntity.TaxState.APPROACH) {
            this.demandMessageSent = false;
        }
    }

    private void sendDemandMessage() {
        if (targetPlayer == null) return;

        int seconds = Config.taxPaymentTime.get() / 20;
        targetPlayer.sendSystemMessage(
                Component.literal("§6[El Presi] §fDebes pagar §e"
                        + Config.taxAmount.get() + " diamante(s)§f. Tienes §c"
                        + seconds + " segundos§f.")
        );

        targetPlayer.sendSystemMessage(
                Component.literal("§7Click derecho con diamantes para pagar.")
        );

        mob.level().playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.VILLAGER_TRADE, SoundSource.NEUTRAL, 1.0f, 1.0f);
    }

    private Player findNearestPlayer(double radius) {
        var box = mob.getBoundingBox().inflate(radius);
        var players = mob.level().getEntitiesOfClass(Player.class, box);

        Player closest = null;
        double minDist = Double.MAX_VALUE;

        for (Player p : players) {
            if (!p.isAlive() || p.isCreative() || p.isSpectator()) continue;
            double dist = mob.distanceTo(p);
            if (dist < minDist) {
                minDist = dist;
                closest = p;
            }
        }
        return closest;
    }

    public TaxCollectorEntity.TaxState getCurrentState() { return currentState; }
    public Player getTargetPlayer() { return targetPlayer; }
}