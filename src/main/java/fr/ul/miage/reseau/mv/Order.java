package fr.ul.miage.reseau.mv;

/**
 * Classe qui sert de tuple d'information d'un ordre donnée à un worker
 */
public class Order {

    private int start;
    private int increment;

    private int difficulty;

    private String payload;

    public Order(int start, int increment, int difficulty, String payload) {
        //On vérifie les données
        if (!checkStart(start)) throw new IllegalArgumentException("Start must be positive");
        if (!checkIncrement(increment)) throw new IllegalArgumentException("Increment must be positive");
        if (!checkDifficulty(difficulty)) throw new IllegalArgumentException("difficulty must be positive");
        if (!checkPayload(payload)) throw new IllegalArgumentException("Payload must be positive");

        //On attribue les données
        this.start = start;
        this.increment = increment;
        this.difficulty = difficulty;
        this.payload = payload;
    }

    private boolean checkStart(int start) {
        return start >= 0;
    }

    private boolean checkIncrement(int increment) {
        return increment > 0;
    }

    private boolean checkDifficulty(int difficulty) {
        return difficulty > 0;
    }

    private boolean checkPayload(String payload) {
        return payload != null && !payload.isEmpty();
    }

    public int getIncrement() {
        return increment;
    }

    public int getStart() {
        return start;
    }

    public int getDifficulty() {
        return difficulty;
    }

    public String getPayload() {
        return payload;
    }

    public void setIncrement(int increment) {
        if (!checkIncrement(increment)) throw new IllegalArgumentException("Increment must be positive");
        this.increment = increment;
    }

    public void setStart(int start) {
        if (!checkStart(start)) throw new IllegalArgumentException("Start must be positive");
        this.start = start;
    }

    public void setDifficulty(int difficulty) {
        if (!checkDifficulty(difficulty)) throw new IllegalArgumentException("difficulty must be positive");
        this.difficulty = difficulty;
    }

    public void setPayload(String payload) {
        if (!checkPayload(payload)) throw new IllegalArgumentException("Payload must be defined");
        this.payload = payload;
    }
}
