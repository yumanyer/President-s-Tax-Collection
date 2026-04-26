package com.mpisoni.presidentstaxcollection.debt;

import net.minecraft.util.RandomSource;

/// Define:
/// - costo en diamantes
/// - velocidad de ataque
/// - comportamiento de mensajes (cooldown + probabilidad)
/// - generación de keys para traducciones
public enum DebtLevel {

    NONE(0, 0f, 200, 0.1f, 5),
    LOW(1, 0.2f, 140, 0.25f, 5),
    MEDIUM(3, 0.3f, 100, 0.4f, 5),
    HIGH(6, 0.5f, 60, 0.6f, 5),
    CRITICAL(10, 0.8f, 30, 0.85f, 5);

    private final int diamonds;
    private final float attackSpeed;

    /// ticks entre intentos de hablar
    private final int baseCooldown;

    /// probabilidad de que hable cuando el cooldown llega a 0
    private final float speakChance;

    /// cantidad de mensajes disponibles en lang json
    private final int messageCount;

    DebtLevel(int diamonds, float attackSpeed, int baseCooldown, float speakChance, int messageCount) {
        this.diamonds = diamonds;
        this.attackSpeed = attackSpeed;
        this.baseCooldown = baseCooldown;
        this.speakChance = speakChance;
        this.messageCount = messageCount;
    }

    public int getDiamonds() {
        return diamonds;
    }

    public float getAttackSpeed() {
        return attackSpeed;
    }

    public int getBaseCooldown() {
        return baseCooldown;
    }

    public float getSpeakChance() {
        return speakChance;
    }

    public int getMessageCount() {
        return messageCount;
    }

    public boolean isCritical() {
        return this == CRITICAL;
    }

    /// Progresión de deuda
    public DebtLevel next() {
        return switch (this) {
            case NONE -> LOW;
            case LOW -> MEDIUM;
            case MEDIUM -> HIGH;
            case HIGH -> CRITICAL;
            case CRITICAL -> CRITICAL;
        };
    }

    /// Genera una key aleatoria para usar con Component.translatable(...)
    /// Ej: chat.presidentstaxcollection.debt.high.3
    public String getRandomMessageKey(RandomSource random) {
        int index = random.nextInt(messageCount) + 1;
        return "chat.presidentstaxcollection.debt."
                + this.name().toLowerCase()
                + "."
                + index;
    }
}