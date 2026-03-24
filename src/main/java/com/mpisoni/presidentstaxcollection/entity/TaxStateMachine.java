package com.mpisoni.presidentstaxcollection.entity;

import com.mpisoni.presidentstaxcollection.Config;
import com.mpisoni.presidentstaxcollection.debt.DebtLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Gestiona la lógica de estados del cobro de impuestos.
 */
public class TaxStateMachine {

    private final TaxCollectorEntity mob;

    // Estado actual del mob
    private TaxCollectorEntity.TaxState currentState = TaxCollectorEntity.TaxState.IDLE;

    // Jugador objetivo
    private Player targetPlayer = null;

    // Tiempo restante para pagar (ticks)
    private int paymentTimer = 0;

    // Evita spam de mensajes
    private boolean demandMessageSent = false;

    // Indica si el jugador pagó en este ciclo
    private boolean playerPaid = false;

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

    /// Busca jugadores cercanos
    private void handleIdle() {
        if (mob.tickCount % 20 == 0) {
            Player nearby = findNearestPlayer(12.0);
            if (nearby != null) {
                targetPlayer = nearby;

                DebtLevel debt = getPlayerDebt(targetPlayer);

                if (debt.isCritical()) {
                    transitionTo(TaxCollectorEntity.TaxState.HOSTILE);
                } else {
                    transitionTo(TaxCollectorEntity.TaxState.APPROACH);
                }
            }
        }
    }

    /// Se acerca al jugador
    private void handleApproach() {
        if (targetPlayer == null || !targetPlayer.isAlive()) {
            transitionTo(TaxCollectorEntity.TaxState.IDLE);
            return;
        }

        if (mob.distanceTo(targetPlayer) < 3.0) {
            transitionTo(TaxCollectorEntity.TaxState.DEMAND_PAYMENT);
        }
    }

    /// Inicia cobro
    private void handleDemandPayment() {
        if (!demandMessageSent) {
            sendDemandMessage();
            demandMessageSent = true;
        }

        playerPaid = false; // reset
        paymentTimer = Config.taxPaymentTime.get();

        transitionTo(TaxCollectorEntity.TaxState.WAITING);
    }

    /// Espera pago
    private void handleWaiting() {
        if (targetPlayer == null || !targetPlayer.isAlive()) {
            transitionTo(TaxCollectorEntity.TaxState.IDLE);
            return;
        }

        // Se escapa → sube deuda
        if (mob.distanceTo(targetPlayer) > 16.0) {
            increaseDebt(targetPlayer);

            targetPlayer.sendSystemMessage(
                    Component.literal("§c[El Presi] §fDe la muerte y los impuestos no se salva nadie")
            );

            transitionTo(TaxCollectorEntity.TaxState.HOSTILE);
            return;
        }

        paymentTimer--;

        // Aviso a mitad de tiempo
        if (paymentTimer == Config.taxPaymentTime.get() / 2) {
            int secondsLeft = paymentTimer / 20;
            targetPlayer.sendSystemMessage(
                    Component.literal("§e[El Presi] §fTe quedan §c" + secondsLeft + " §fsegundos para pagar.")
            );
        }

        // Tiempo agotado
        if (paymentTimer <= 0) {
            if (!playerPaid) {
                DebtLevel newDebt = increaseDebt(targetPlayer);

                targetPlayer.sendSystemMessage(
                        Component.literal("§c[El Presi] §f" + newDebt.getRandomMessage(mob.getRandom()))
                );
            }

            transitionTo(TaxCollectorEntity.TaxState.HOSTILE);
        }
    }

    /// Ataca
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

        if (newState == TaxCollectorEntity.TaxState.IDLE ||
            newState == TaxCollectorEntity.TaxState.APPROACH) {
            this.demandMessageSent = false;
        }
    }

    /// Mensaje inicial
    private void sendDemandMessage() {
        if (targetPlayer == null) return;

        DebtLevel debt = getPlayerDebt(targetPlayer);

        int seconds = Config.taxPaymentTime.get() / 20;
        int required = debt.getDiamonds();

        //mensaje narrativo 
        if (debt != DebtLevel.NONE) {
            targetPlayer.sendSystemMessage(
                    Component.literal("§c[El Presi] §f" + debt.getRandomMessage(mob.getRandom()))
            );
        }

        // orden de pago (
        targetPlayer.sendSystemMessage(
                Component.literal("§6[El Presi] §fDebes pagar §e"
                        + required + " diamante(s)§f. Tienes §c"
                        + seconds + " segundos§f.")
        );

        // Instrucción final
        targetPlayer.sendSystemMessage(
                Component.literal("§7Click derecho con diamantes para pagar.")
        );

        mob.level().playSound(null, mob.getX(), mob.getY(), mob.getZ(),
                SoundEvents.VILLAGER_TRADE, SoundSource.NEUTRAL, 1.0f, 1.0f);
}

    /// Obtiene deuda segura
    private DebtLevel getPlayerDebt(Player player) {
        int level = player.getPersistentData().getInt("tax_debt_level");
        level = Math.min(level, DebtLevel.values().length - 1);
        return DebtLevel.values()[level];
    }

    /// Aumenta deuda
    private DebtLevel increaseDebt(Player player) {
        CompoundTag data = player.getPersistentData();

        int current = data.getInt("tax_debt_level");
        int next = Math.min(current + 1, DebtLevel.values().length - 1);

        data.putInt("tax_debt_level", next);

        return DebtLevel.values()[next];
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