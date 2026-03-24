package com.mpisoni.presidentstaxcollection.debt;

///Cada valor sabe cuántos diamantes cuesta,
/// qué mensaje envía y qué velocidad de ataque tiene el mob cuando se vuelve hostil.
public enum DebtLevel {
    NONE(0, 0f, "No me debes nada, por ahora..."),  
    LOW (1 , 0.2f , "Tranqui pa me pagas la prox pero CON INTERESES" ),
    MEDIUM(3, 0.3f, "Ya me estás debiendo...Y no te conviene "),
    HIGH(6, 0.5f, "Bueno me parece que alguien se va quedar sin casa."),
    CRITICAL(10, 0.8f, "Ahora vos sos de mi pertenencia.");

    private final int diamonds;
    private final float attackSpeed;
    private final String message;


    DebtLevel(int diamonds, float attackSpeed, String message) {
        this.diamonds = diamonds;
        this.attackSpeed = attackSpeed;
        this.message = message;
    }

    public int getDiamonds() {
        return diamonds;
    }

    public float getAttackSpeed() {
        return attackSpeed;
    }

    public String getMessage() {
        return message;
    }
    public boolean isCritical(){
        return this == CRITICAL;
    }

    public DebtLevel next(){
        return switch (this) {
            case NONE -> LOW;
            case LOW -> MEDIUM;
            case MEDIUM -> HIGH;
            case HIGH -> CRITICAL;
            case CRITICAL -> CRITICAL;
        };
    }

}

