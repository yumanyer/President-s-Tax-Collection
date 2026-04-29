package com.mpisoni.presidentstaxcollection.entity;

import com.mpisoni.presidentstaxcollection.Config;
import com.mpisoni.presidentstaxcollection.debt.DebtLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.world.BossEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class TaxStateMachine {

    private final TaxCollectorEntity mob;

    private TaxCollectorEntity.TaxState currentState = TaxCollectorEntity.TaxState.IDLE;
    private Player targetPlayer = null;

    private int paymentTimer = 0;
    private boolean playerPaid = false;

    // 🔴 BossBar
    private final ServerBossEvent bossBar;

    public TaxStateMachine(TaxCollectorEntity mob) {
        this.mob = mob;

        

        this.bossBar = new ServerBossEvent(
                Component.literal("Pago de impuestos"),
                BossEvent.BossBarColor.RED,
                BossEvent.BossBarOverlay.PROGRESS

        );
        
    }

    public void tick() {

        if (mob.isRemoved() || !mob.isAlive()) {
        removeBossBar();
        return;
    }
        if (mob.level().isClientSide) return;


        switch (currentState) {
            case IDLE -> handleIdle();
            case APPROACH -> handleApproach();
            case WAITING -> handleWaiting();
            case HOSTILE -> handleHostile();
        }
    }



    // =========================
    // TRANSITIONS
    // =========================

    public void transitionTo(TaxCollectorEntity.TaxState newState) {
        if (this.currentState == newState) return;

        this.currentState = newState;
        onEnter(newState);
    }

    private void onEnter(TaxCollectorEntity.TaxState state) {
        switch (state) {

            case WAITING -> {
                startPaymentFlow();

                if (targetPlayer instanceof ServerPlayer sp) {
                    bossBar.addPlayer(sp);
                }
            }

            case IDLE -> {
                removeBossBar();
                targetPlayer = null;
                mob.setTarget(null);
            }

            case HOSTILE -> {
                removeBossBar();
            }
        }
    }

    // =========================
    // ESTADOS
    // =========================

    private void handleIdle() {
        if (mob.tickCount % 20 != 0) return;

        Player nearby = findNearestPlayer(16.0);
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
        if (!isValidTarget()) {
            transitionTo(TaxCollectorEntity.TaxState.IDLE);
            return;
        }

        DebtLevel debt = getPlayerDebt(targetPlayer);
        if (debt == DebtLevel.NONE) {
            transitionTo(TaxCollectorEntity.TaxState.IDLE);
            return;
        }

        if (mob.distanceTo(targetPlayer) < 3.0) {
            transitionTo(TaxCollectorEntity.TaxState.WAITING);
        }
    }

private void handleWaiting() {
    System.out.println("TIEMPO: " + Config.taxPaymentTime.get());


    // ✔ PAGO
    if (playerPaid) {
        removeBossBar();

        sendMessage(targetPlayer,
                "chat.presidentstaxcollection.payment_success");

        playerPaid = false;
        transitionTo(TaxCollectorEntity.TaxState.IDLE);
        return;
    }

    // escapó
    if (mob.distanceTo(targetPlayer) > 16.0) {
        increaseDebt(targetPlayer);
        sendMessage(targetPlayer, "chat.presidentstaxcollection.escape_consequence");

        removeBossBar();
        transitionTo(TaxCollectorEntity.TaxState.HOSTILE);
        return;
    }

    // bossbar
    float progress = Math.max(0f,
            (float) paymentTimer / Config.taxPaymentTime.get());
    bossBar.setProgress(progress);

    bossBar.setName(
            Component.literal("Tiempo restante: " + (paymentTimer / 20) + "s")
    );

    // mitad del tiempo
    if (paymentTimer == Config.taxPaymentTime.get() / 2) {
        sendMessage(targetPlayer,
                "chat.presidentstaxcollection.time_remaining",
                paymentTimer / 20
        );
    }

    if (paymentTimer > 0) {
        paymentTimer--;
    }

    // timeout
    if (paymentTimer <= 0) {
        if (!playerPaid) {
            increaseDebt(targetPlayer);
            sendMessage(targetPlayer,
                    "chat.presidentstaxcollection.no_more_money");
        }

        removeBossBar();
        transitionTo(TaxCollectorEntity.TaxState.HOSTILE);
    }
}

    private void handleHostile() {
        if (isValidTarget()) {
            mob.setTarget(targetPlayer);
        } else {
            mob.setTarget(null);
            transitionTo(TaxCollectorEntity.TaxState.IDLE);
        }
    }

    // =========================
    // FLOW DE PAGO
    // =========================

    private void startPaymentFlow() {
        if (targetPlayer == null) return;

        playerPaid = false;
        paymentTimer = Config.taxPaymentTime.get();

        sendDemandMessage();
    }

    private void removeBossBar() {
        bossBar.removeAllPlayers();
    }

    // =========================
    // VALIDACIONES
    // =========================

    private boolean isValidTarget() {
        return targetPlayer != null && targetPlayer.isAlive();
    }

    // =========================
    // MENSAJES
    // =========================

    private void sendDemandMessage() {
        DebtLevel debt = getPlayerDebt(targetPlayer);

        int seconds = Config.taxPaymentTime.get() / 20;
        int required = debt.getDiamonds();

        sendDebtNarrative(targetPlayer, debt);

        sendMessage(targetPlayer,
                "chat.presidentstaxcollection.demand_payment",
                required,
                seconds
        );

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

        player.sendSystemMessage(Component.translatable(key));
    }

    private void sendMessage(Player player, String key, Object... args) {
        player.sendSystemMessage(Component.translatable(key, args));
    }

    // =========================
    // DEUDA (PENDIENTE → CAPABILITY)
    // =========================

    private void tryUpgradeDebtFromDiamonds(Player player) {
        int current = player.getPersistentData().getInt("tax_debt_level");
        if (current != 0) return;

        boolean hasDiamond = player.getInventory().contains(new ItemStack(Items.DIAMOND));

        if (hasDiamond) {
            player.getPersistentData().putInt("tax_debt_level", 1);
            sendMessage(player, "chat.presidentstaxcollection.income_detected");
        }
    }

    private DebtLevel getPlayerDebt(Player player) {
        int level = player.getPersistentData().getInt("tax_debt_level");
        level = Math.min(level, DebtLevel.values().length - 1);
        return DebtLevel.values()[level];
    }

    private DebtLevel increaseDebt(Player player) {
        int current = player.getPersistentData().getInt("tax_debt_level");

        int next = (current < 2)
                ? current + 1
                : Math.min(current + 2, DebtLevel.values().length - 1);

        player.getPersistentData().putInt("tax_debt_level", next);

        return DebtLevel.values()[next];
    }

    // =========================
    // UTILS
    // =========================

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