package com.mpisoni.presidentstaxcollection.entity;

import com.mpisoni.presidentstaxcollection.Config;
import com.mpisoni.presidentstaxcollection.debt.DebtLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Gestiona la lógica de estados del cobro de impuestos de forma aislada.
 */
public class TaxStateMachine {

    // Referencia al mob que usa esta máquina de estados
    private final TaxCollectorEntity mob;
    // Estado actual del mob (empieza en IDLE)
    private TaxCollectorEntity.TaxState currentState = TaxCollectorEntity.TaxState.IDLE;
    // Jugador objetivo al que el mob está persiguiendo/cobrando
    private Player targetPlayer = null;
    // Tiempo restante (en ticks) que el jugador tiene para pagar
    private int paymentTimer = 0;
    // Flag para evitar enviar el mensaje de cobro múltiples veces
    private boolean demandMessageSent = false;

    //Indico si el jugador pago
    private boolean playerPaid = false;
    private DebtLevel debtLevel = DebtLevel.NONE;

    /// Constructor: inicializa la máquina de estados con la referencia al mob
    public TaxStateMachine(TaxCollectorEntity mob) {
        this.mob = mob;
    }

    /// Método principal que se ejecuta cada tick (20 veces por segundo)
    /// Decide qué lógica correr según el estado actual
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
    /// Estado IDLE: busca jugadores cercanos cada cierto tiempo
    /// Si encuentra uno, empieza a acercarse
    private void handleIdle() {
        if (mob.tickCount % 20 == 0) {
            Player nearby = findNearestPlayer(12.0);
            if (nearby != null) {
                targetPlayer = nearby;
                transitionTo(TaxCollectorEntity.TaxState.APPROACH);
            }
        }
    }
    /// Estado APPROACH: el mob se acerca al jugador objetivo
    /// Si está lo suficientemente cerca, pasa a exigir pago
    private void handleApproach() {
        if (targetPlayer == null || !targetPlayer.isAlive()) {
            transitionTo(TaxCollectorEntity.TaxState.IDLE);
            return;
        }

        if (mob.distanceTo(targetPlayer) < 3.0) {
            transitionTo(TaxCollectorEntity.TaxState.DEMAND_PAYMENT);
        }
    }
    /// Estado DEMAND_PAYMENT: envía el mensaje inicial de cobro
    /// Inicializa el timer y pasa a WAITING
    private void handleDemandPayment() {
        if (!demandMessageSent) {
            sendDemandMessage();
            demandMessageSent = true;
        }
        paymentTimer = Config.taxPaymentTime.get();
        transitionTo(TaxCollectorEntity.TaxState.WAITING);
    }
    /// Estado WAITING: espera que el jugador pague dentro de un tiempo límite
    /// Si se aleja o no paga, se vuelve hostil
    /// Estado WAITING: cuenta regresiva para pagar.
    /// Si no paga a tiempo → sube deuda y pasa a HOSTILE
    private void handleWaiting() {
        // Si el target ya no es válido → reset
        if (targetPlayer == null || !targetPlayer.isAlive()) {
            transitionTo(TaxCollectorEntity.TaxState.IDLE);
            return;
        }

        // Si se aleja demasiado → hostil directo
        if (mob.distanceTo(targetPlayer) > 16.0) {
            targetPlayer.sendSystemMessage(
                    //se activa despus de que quedan 5 segundos 
                    Component.literal("§c[El Presi] §fDe la muerte y los impuestos no se salva nadie")
            );
            transitionTo(TaxCollectorEntity.TaxState.HOSTILE);
            return;
        }

        // Tick del timer
        paymentTimer--;

        // Aviso a mitad de tiempo
        if (paymentTimer == Config.taxPaymentTime.get() / 2) {
            int secondsLeft = paymentTimer / 20;
            targetPlayer.sendSystemMessage(
                    Component.literal("§e[El Presi] §fTe quedan §c" + secondsLeft + " §fsegundos para pagar.")
            );
        }

        // Si se terminó el tiempo
        if (paymentTimer <= 0) {

            // Si NO pagó → escalar deuda y mensaje
            if (!playerPaid) {
                debtLevel = debtLevel.next(); // sube nivel
                targetPlayer.sendSystemMessage(
                        Component.literal(debtLevel.getMessage())
                );
            }

            // Siempre termina en estado HOSTILE
            transitionTo(TaxCollectorEntity.TaxState.HOSTILE);
        }
    }

    /// Estado HOSTILE: el mob ataca al jugador objetivo
    /// Si lo pierde, vuelve a IDLE
    private void handleHostile() {
        if (targetPlayer != null && targetPlayer.isAlive()) {
            mob.setTarget(targetPlayer);
        } else {
            mob.setTarget(null);
            transitionTo(TaxCollectorEntity.TaxState.IDLE);
        }
    }
    /// Maneja el cambio de estado del mob
    /// También resetea flags si vuelve a estados iniciales
    public void transitionTo(TaxCollectorEntity.TaxState newState) {
        this.currentState = newState;
        if (newState == TaxCollectorEntity.TaxState.IDLE || newState == TaxCollectorEntity.TaxState.APPROACH) {
            this.demandMessageSent = false;
        }
    }
    /// Envía los mensajes iniciales de cobro al jugador
    /// También reproduce un sonido
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
    /// Busca el jugador más cercano dentro de un radio dado
    /// Ignora jugadores en creativo o espectador
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