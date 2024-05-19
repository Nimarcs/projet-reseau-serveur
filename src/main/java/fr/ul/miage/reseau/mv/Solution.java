package fr.ul.miage.reseau.mv;

/**
 * Classe qui sert de stockage de tuple
 */
public class Solution {

    private String hash;

    private String nonce;

    public Solution(String hash, String nonce) {
        this.hash = hash;
        this.nonce = nonce;
    }

    public String getHash() {
        return hash;
    }

    public String getNonce() {
        return nonce;
    }

}
