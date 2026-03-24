package com.mpisoni.presidentstaxcollection.debt;

import net.minecraft.util.RandomSource;
import java.util.List;

/// Cada valor define:
/// - cuántos diamantes cuesta
/// - qué velocidad de ataque tiene
/// - un conjunto de mensajes posibles (para evitar repetición)
public enum DebtLevel {

    NONE(0, 0f, List.of(
            "Qué lindo verte sin deudas. Me da hasta pena venir.",
            "Todo en orden… por ahora.",
            "Hoy estás limpio. Disfrutalo.",
            "No me debés nada… eso me pone incómodo.",
            "Pocas veces veo a alguien como vos… sin cuentas pendientes."
    )),
    LOW(1, 0.2f, List.of(
            "Es poco… pero no gratis. Pagá y seguimos en paz.",
            "Todavía es leve. No lo conviertas en problema.",
            "Una deuda chica igual se cobra.",
            "No me hagas escalar esto por un diamante.",
            "Podemos resolverlo fácil… o hacerlo complicado."
    )),
    MEDIUM(3, 0.3f, List.of(
            "Ya me estás debiendo… y no me gusta repetir las cosas.",
            "Se está acumulando… y yo también.",
            "No te hagas el distraído, sé exactamente cuánto me debés.",
            "Esto ya no es tan chico como pensás.",
            "Me estás empezando a preocupar… y eso no es bueno para vos."
    )),
    HIGH(6, 0.5f, List.of(
            "Me parece que alguien va a perder más que diamantes.",
            "Esto ya no es joda. Se terminó la paciencia.",
            "Tu casita es linda… sería una pena.",
            "Ya no estamos negociando.",
            "Me estás obligando a tomar medidas."
    )),
    CRITICAL(10, 0.8f, List.of(
            "Ya no sos un deudor… sos un problema.",
            "Ahora me pertenecés. Y voy a cobrar.",
            "Te di oportunidades. Ahora te doy consecuencias.",
            "No hay trato. Solo ejecución.",
            "Corré si querés… me divierte."
    ));

    private final int diamonds;
    private final float attackSpeed;
    private final List<String> messages;

    DebtLevel(int diamonds, float attackSpeed, List<String> messages) {
        this.diamonds = diamonds;
        this.attackSpeed = attackSpeed;
        this.messages = messages;
    }

    public int getDiamonds() {
        return diamonds;
    }

    public float getAttackSpeed() {
        return attackSpeed;
    }

    /// Devuelve un mensaje aleatorio según el nivel de deuda
    public String getRandomMessage(RandomSource random) {
        return messages.get(random.nextInt(messages.size()));
    }

    public boolean isCritical() {
        return this == CRITICAL;
    }

    /// Devuelve el siguiente nivel de deuda (progresión)
    public DebtLevel next() {
        return switch (this) {
            case NONE -> LOW;
            case LOW -> MEDIUM;
            case MEDIUM -> HIGH;
            case HIGH -> CRITICAL;
            case CRITICAL -> CRITICAL;
        };
    }
}