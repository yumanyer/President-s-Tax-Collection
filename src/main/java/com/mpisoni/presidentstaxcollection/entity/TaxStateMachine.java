package com.mpisoni.presidentstaxcollection.entity;

import com.mpisoni.presidentstaxcollection.Config;
import com.mpisoni.presidentstaxcollection.debt.DebtLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class TaxStateMachine {

    private final TaxCollectorEntity mob;

    private TaxCollectorEntity.TaxState currentState = TaxCollectorEntity.TaxState.IDLE;
    private Player targetPlayer = null;

    private int paymentTimer = 0;
    private boolean demandMessageSent = false;
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

    /// =========================
    /// ESTADOS
    /// =========================

    private void handleIdle() {
        if (mob.tickCount % 20 != 0) return;

        Player nearby = findNearestPlayer(12.0);
        if (nearby == null) return;

        targetPlayer = nearby;

        tryUpgradeDebtFromDiamonds(targetPlayer);

        DebtLevel debt = getPlayerDebt(targetPlayer);

        if (debt == DebtLevel.NONE) return;

        if (debt.isCritical()) {
            transitionTo(TaxCollectorEntity.TaxState.HOSTILE);
        } else {
            transitionTo(TaxCollectorEntity.TaxState.APPROACH);
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

        playerPaid = false;
        paymentTimer = Config.taxPaymentTime.get();

        transitionTo(TaxCollectorEntity.TaxState.WAITING);
    }

    private void handleWaiting() {
        if (targetPlayer == null || !targetPlayer.isAlive()) {
            transitionTo(TaxCollectorEntity.TaxState.IDLE);
            return;
        }

        // Escapó
        if (mob.distanceTo(targetPlayer) > 16.0) {
            increaseDebt(targetPlayer);

            sendMessage(targetPlayer,
                    "chat.presidentstaxcollection.escape_consequence"
            );

            transitionTo(TaxCollectorEntity.TaxState.HOSTILE);
            return;
        }

        paymentTimer--;

        // Mitad del tiempo
        if (paymentTimer == Config.taxPaymentTime.get() / 2) {
            sendMessage(targetPlayer,
                    "chat.presidentstaxcollection.time_remaining",
                    paymentTimer / 20
            );
        }

        // Timeout
        if (paymentTimer <= 0) {
            if (!playerPaid) {
                increaseDebt(targetPlayer);

                sendMessage(targetPlayer,
                        "chat.presidentstaxcollection.no_more_money"
                );
            }

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

        if (newState == TaxCollectorEntity.TaxState.IDLE
                || newState == TaxCollectorEntity.TaxState.APPROACH) {
            this.demandMessageSent = false;
        }
    }

    /// =========================
    /// MENSAJES
    /// =========================

    private void sendDemandMessage() {
        if (targetPlayer == null) return;

        DebtLevel debt = getPlayerDebt(targetPlayer);

        int seconds = Config.taxPaymentTime.get() / 20;
        int required = debt.getDiamonds();

        // narrativa dinámica (desde lang)
        sendDebtNarrative(targetPlayer, debt);

        // orden de pago
        sendMessage(targetPlayer,
                "chat.presidentstaxcollection.demand_payment",
                required,
                seconds
        );

        // instrucción
        sendMessage(targetPlayer,
                "chat.presidentstaxcollection.payment_instruction"
        );

        mob.level().playSound(null,
                mob.getX(), mob.getY(), mob.getZ(),
                SoundEvents.VILLAGER_TRADE,
                SoundSource.NEUTRAL,
                1.0f, 1.0f
        );
    }

    private void sendDebtNarrative(Player player, DebtLevel debt) {
        if (debt == DebtLevel.NONE) return;

        String key = debt.getRandomMessageKey(mob.getRandom());

        player.sendSystemMessage(
                Component.translatable(key)
        );
    }

    private void sendMessage(Player player, String key, Object... args) {
        player.sendSystemMessage(
                Component.translatable(key, args)
        );
    }

    /// =========================
    /// LÓGICA DE DEUDA
    /// =========================

    private void tryUpgradeDebtFromDiamonds(Player player) {
        CompoundTag data = player.getPersistentData();

        int current = data.getInt("tax_debt_level");
        if (current != 0) return;

        boolean hasDiamond = player.getInventory().contains(new ItemStack(Items.DIAMOND));

        if (hasDiamond) {
            data.putInt("tax_debt_level", 1);

            sendMessage(player,
                    "chat.presidentstaxcollection.income_detected"
            );
        }
    }

    private DebtLevel getPlayerDebt(Player player) {
        int level = player.getPersistentData().getInt("tax_debt_level");
        level = Math.min(level, DebtLevel.values().length - 1);
        return DebtLevel.values()[level];
    }

    private DebtLevel increaseDebt(Player player) {
        CompoundTag data = player.getPersistentData();

        int current = data.getInt("tax_debt_level");

        int next = (current < 2)
                ? current + 1
                : Math.min(current + 2, DebtLevel.values().length - 1);

        data.putInt("tax_debt_level", next);

        return DebtLevel.values()[next];
    }

    /// =========================
    /// UTILS
    /// =========================

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

    public TaxCollectorEntity.TaxState getCurrentState() {
        return currentState;
    }

    public Player getTargetPlayer() {
        return targetPlayer;
    }
}