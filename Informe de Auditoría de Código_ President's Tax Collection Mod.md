# Informe de Auditoría de Código: President's Tax Collection Mod

**Fecha:** 24 de abril de 2026

**Autor:** Manus AI

## 1. Resumen Ejecutivo

Este informe detalla los hallazgos de una auditoría de código exhaustiva del mod de Minecraft Forge "President's Tax Collection". El objetivo principal fue identificar fallos silenciosos, problemas estructurales, riesgos de rendimiento y malas prácticas de codificación, incluso en un sistema funcional. El mod introduce una entidad "Tax Collector" que interactúa con los jugadores para cobrar diamantes, gestionando un sistema de deuda y estados de comportamiento.

En general, el mod presenta una estructura clara y una implementación funcional de su lógica central. Sin embargo, se han identificado varias áreas de mejora, particularmente en la gestión de la persistencia de datos del jugador, la progresión de la deuda, la robustez frente a condiciones de borde y la claridad en la lógica de algunos estados. La ausencia de persistencia de la máquina de estados de la entidad y la dependencia de NBT directamente en el jugador son puntos críticos.

## 2. Hallazgos Detallados

### 2.1. Problemas Críticos

| Descripción | Impacto | Dónde Ocurre | Cómo Arreglarlo |
|---|---|---|---|
| **Falta de persistencia de `TaxStateMachine` en `TaxCollectorEntity`** | La máquina de estados (`currentState`, `targetPlayer`, `paymentTimer`, `demandMessageSent`, `greeted`) no se guarda ni se carga cuando la entidad se descarga y recarga (ej. chunk unload/load, reinicio del servidor). Esto significa que la entidad perderá su estado actual, el jugador objetivo, el temporizador de pago y si ya envió mensajes, reiniciando su comportamiento desde `IDLE` o un estado inconsistente. | `TaxCollectorEntity.java`, `TaxStateMachine.java` | Implementar los métodos `addAdditionalSaveData(CompoundTag tag)` y `readAdditionalSaveData(CompoundTag tag)` en `TaxCollectorEntity` para serializar y deserializar los campos relevantes de `TaxStateMachine` (o la propia `TaxStateMachine` si se hace serializable). Es crucial guardar el `UUID` del `targetPlayer` en lugar del objeto `Player` directamente. |
| **Dependencia directa de NBT del jugador para la deuda** | La deuda del jugador (`tax_debt_level`) se almacena directamente en el `PersistentData` del objeto `Player`. Esto es problemático porque el `PersistentData` del jugador no se guarda automáticamente en todos los escenarios (ej. si el jugador no ha interactuado con el mundo de ciertas maneras). Además, es una práctica común usar `Capabilities` para datos persistentes personalizados asociados a entidades o jugadores, lo que ofrece un ciclo de vida de persistencia más robusto y extensible. | `TaxStateMachine.java` (métodos `getPlayerDebt`, `increaseDebt`, `tryUpgradeDebtFromDiamonds`) | Implementar un sistema de `Capabilities` (ej. `IPlayerDebt`, `PlayerDebtProvider`, `PlayerDebtStorage`) para gestionar la deuda del jugador. Esto asegura que los datos se guarden y carguen correctamente con el jugador y proporciona una API más limpia para acceder a ellos. |

### 2.2. Errores Silenciosos y Casos de Borde

| Escenario | Problema | Por Qué Sucede |
|---|---|---|
| **Progresión de deuda inconsistente** | La lógica en `increaseDebt` puede saltarse niveles de deuda. Específicamente, si un jugador pasa de `LOW` (nivel 1) a un aumento de deuda, la lógica `current + 2` lo lleva a `HIGH` (nivel 3), saltándose `MEDIUM` (nivel 2). | La condición `if(current >= 2)` y la duplicación de `next = Math.min(current + 2, DebtLevel.values().length - 1);` crean una progresión no lineal y potencialmente inesperada. La intención de `current + 1` para niveles bajos y `current + 2` para niveles más altos no se implementa correctamente. |
| **`targetPlayer` nulo en `TaxStateMachine`** | Aunque hay verificaciones para `targetPlayer == null` en `handleApproach` y `handleWaiting`, si el jugador objetivo se desconecta o muere justo antes de una llamada a `sendDemandMessage` o `increaseDebt`, podría haber un `NullPointerException` si no se maneja explícitamente en esos métodos. | La asignación de `targetPlayer` se realiza en `handleIdle`, pero su validez no se revalida en cada tick de los estados subsiguientes antes de su uso directo en métodos que no tienen su propia verificación de nulidad. |
| **Interacción con el mob después de que el temporizador expira pero antes de volverse hostil** | Si el `paymentTimer` llega a cero y el mob aún no ha cambiado a `HOSTILE` (ej. por orden de ticks o lag), un jugador podría intentar pagar. El `mobInteract` solo permite el pago en estado `WAITING`. | El estado `WAITING` se mantiene hasta el siguiente tick donde `handleWaiting` transiciona a `HOSTILE`. Existe una pequeña ventana donde el pago no es posible aunque el mob aún no sea hostil, lo que puede ser confuso para el jugador. |
| **`tryUpgradeDebtFromDiamonds` solo para `DebtLevel.NONE`** | Un jugador con deuda `LOW` o `MEDIUM` que adquiere diamantes no verá su deuda aumentar a `LOW` si ya tiene una deuda mayor. Esto es intencional para evitar que el jugador sea penalizado por tener diamantes si ya tiene deuda, pero podría ser un caso de borde si la intención es que la presencia de diamantes siempre active el inicio de la deuda. | La condición `if (current != 0) return;` limita la funcionalidad a jugadores sin deuda. |

### 2.3. Problemas de Arquitectura

| Problema | Riesgo a Futuro | Recomendación |
|---|---|---|
| **Lógica de máquina de estados acoplada a la entidad** | La `TaxStateMachine` es una clase interna (implícitamente) y está fuertemente acoplada a `TaxCollectorEntity`. Aunque esto es común para máquinas de estados simples, dificulta la reutilización de la lógica de estados para otras entidades o la extensión de la misma. | Considerar hacer `TaxStateMachine` una clase de nivel superior o una interfaz si se planea tener múltiples entidades con lógica de estado similar. Esto permitiría una mayor modularidad y reutilización. |
| **Gestión de eventos y bus de Forge** | La clase principal `PresidentsTaxCollection` se registra en `MinecraftForge.EVENT_BUS` pero no contiene ningún método `@SubscribeEvent`. Esto es un registro innecesario que no causa daño, pero es una práctica subóptima. | El registro de `PresidentsTaxCollection` en `MinecraftForge.EVENT_BUS` es redundante si no hay métodos `@SubscribeEvent` dentro de la clase. |
| **Configuración de `MobCategory.MISC` para `TaxCollectorEntity`** | La entidad se registra con `MobCategory.MISC`, lo que significa que no spawnea naturalmente. Si en el futuro se desea que el recaudador aparezca de forma natural, se requerirá una modificación significativa para configurar `SpawnPlacements` y posiblemente cambiar la categoría a `MONSTER` o `CREATURE`. | Si hay planes futuros para spawns naturales, considerar la implicación de `MobCategory.MISC` desde el principio. Documentar claramente cómo se espera que la entidad sea invocada. |

### 2.4. Riesgos de Rendimiento

| Problema | Costo en Rendimiento | Optimización Sugerida |
|---|---|---|
| **`findNearestPlayer` en cada tick de `IDLE`** | En el estado `IDLE`, `findNearestPlayer` se llama cada 20 ticks (1 segundo). Aunque la búsqueda se limita a un radio de 12 bloques, realizar una búsqueda de entidades y una iteración sobre ellas cada segundo puede ser costoso en servidores con muchas entidades o jugadores. | Implementar un temporizador más largo para la búsqueda de jugadores en estado `IDLE` (ej. cada 60-100 ticks). Considerar optimizaciones si la base de jugadores es muy grande, como usar un sistema de `Capability` para marcar jugadores con deuda y solo buscar entre ellos. |
| **Creación de `Component.literal` en cada mensaje** | Cada vez que se envía un mensaje al jugador, se crea un nuevo objeto `Component.literal`. Aunque Java y Minecraft optimizan esto, la creación constante de objetos puede contribuir a la presión del recolector de basura en situaciones de alta concurrencia. | Para mensajes estáticos o que se repiten con frecuencia, considerar pre-crear los objetos `Component` una vez y reutilizarlos. Para mensajes dinámicos, esto es menos aplicable. |

### 2.5. Code Smells

| Patrón | Por Qué es Problemática | Cómo Mejorarla |
|---|---|---|---|
| **Magic Numbers en `TaxStateMachine`** | Valores como `3.0` (distancia de aproximación), `16.0` (distancia de escape), `20` (ticks por segundo), `10` (partículas de villager feliz) están codificados directamente en la lógica. | Mover estos valores a constantes con nombres descriptivos (ej. `APPROACH_DISTANCE`, `ESCAPE_DISTANCE`, `TICKS_PER_SECOND`, `HAPPY_VILLAGER_PARTICLE_COUNT`) en la clase `TaxStateMachine` o en `Config.java` si son configurables. |
| **Lógica de `increaseDebt` compleja y duplicada** | La lógica para calcular `next` en `increaseDebt` es confusa y contiene una duplicación de `Math.min(current + 2, DebtLevel.values().length - 1);`. | Simplificar la lógica de `increaseDebt` para que sea más clara y evitar la duplicación. Por ejemplo, definir una progresión clara en `DebtLevel.next()` y usarla. |
| **Comentarios redundantes o desactualizados** | Algunos comentarios describen el código de forma obvia o pueden desactualizarse fácilmente (ej. `// reset` para `playerPaid = false;`). | Eliminar comentarios redundantes y actualizar aquellos que no reflejan la lógica actual. Priorizar código auto-documentado y comentarios que expliquen el *porqué* de una decisión, no el *qué* hace el código. |
| **`PlayerModel` en `TaxCollectorRenderer`** | Usar `PlayerModel` para una entidad que no es un jugador puede ser una solución rápida, pero limita la personalización del modelo y la animación. | Si se busca un modelo único o animaciones personalizadas, se debería crear un modelo y una capa de modelo personalizados para `TaxCollectorEntity` en lugar de reutilizar `PlayerModel`. |

## 3. Nivel de Confianza

**medium**

El nivel de confianza es **medio** debido a que la auditoría se realizó de forma estática, sin la ejecución del código en un entorno de prueba. La detección de fallos silenciosos y casos de borde se basa en la interpretación del código fuente y el conocimiento de las APIs de Minecraft Forge. Una auditoría de nivel de confianza **alto** requeriría pruebas unitarias, pruebas de integración y pruebas de juego exhaustivas para validar todos los escenarios identificados y descubrir otros que solo se manifiestan en tiempo de ejecución.

## 4. Conclusión y Próximos Pasos

El mod "President's Tax Collection" es un proyecto bien estructurado con una funcionalidad clara. Los problemas identificados, aunque no impiden el funcionamiento básico, representan riesgos de inconsistencia de datos, comportamiento inesperado en ciertas condiciones y oportunidades de mejora en la robustez y el diseño. La implementación de `Capabilities` para la persistencia de la deuda del jugador y la corrección de la lógica de progresión de la deuda son los puntos más críticos a abordar.

Se recomienda encarecidamente abordar los "Problemas Críticos" y los "Errores Silenciosos" para mejorar la estabilidad y la experiencia del usuario del mod. Las "Recomendaciones de Arquitectura" y las "Optimizaciones de Rendimiento" pueden considerarse en fases posteriores de desarrollo o si surgen problemas específicos.

Para una auditoría más profunda y con mayor nivel de confianza, se sugiere:

*   **Pruebas de Integración:** Ejecutar el mod en un entorno de desarrollo y probar los escenarios de deuda, pago, escape y desconexión del jugador.
*   **Análisis de Concurrencia:** Si el mod se expande para manejar múltiples recaudadores o interacciones complejas, un análisis de `race conditions` y sincronización sería crucial.
*   **Revisión de Seguridad:** Evaluar posibles vulnerabilidades (ej. exploits de NBT, manipulación de paquetes).
