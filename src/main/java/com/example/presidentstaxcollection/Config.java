package com.example.presidentstaxcollection;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Configuración del mod usando ForgeConfigSpec.
 *
 * Tipo SERVER: los valores se guardan en
 *   saves/<mundo>/serverconfig/examplemod-server.toml
 *
 * Para acceder desde cualquier clase:
 *   Config.taxPaymentTime.get()
 *   Config.taxAmount.get()
 */
public class Config {

    // Builder y Spec — el Spec es lo que se registra en ExampleMod
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;

    // ── Variables configurables ───────────────────────────────────────────────

    /**
     * Tiempo que tiene el jugador para pagar antes de que el mob se vuelva hostil.
     * Unidad: ticks (20 ticks = 1 segundo)
     * Default: 200 ticks (10 segundos)
     * Rango: 40–1200 ticks (2 segundos mínimo, 1 minuto máximo)
     */
    public static final ForgeConfigSpec.IntValue taxPaymentTime;

    /**
     * Cantidad de diamantes requeridos para pagar al recaudador.
     * Default: 1
     * Rango: 1–64
     */
    public static final ForgeConfigSpec.IntValue taxAmount;

    // ── Bloque de inicialización estática ────────────────────────────────────
    static {
        BUILDER.comment("=== Tax Collector Mod - Configuración de Servidor ===");

        BUILDER.push("tax_system"); // agrupa las opciones bajo [tax_system] en el .toml

        taxPaymentTime = BUILDER
                .comment("Ticks que tiene el jugador para pagar antes de que el mob ataque.",
                        "20 ticks = 1 segundo. Default: 200 (10 segundos)")
                .defineInRange("taxPaymentTime", 200, 40, 1200);

        taxAmount = BUILDER
                .comment("Cantidad de diamantes que el jugador debe entregar para pagar.",
                        "Default: 1")
                .defineInRange("taxAmount", 1, 1, 64);

        BUILDER.pop();

        SPEC = BUILDER.build();
    }
}