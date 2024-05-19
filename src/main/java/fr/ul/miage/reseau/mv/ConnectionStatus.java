package fr.ul.miage.reseau.mv;

public enum ConnectionStatus {

    ALONE, // Connection n'est pas connecté à qui que ce soit
    IDLE, // En attente d'ordre
    WORKING, // Connecter à quelqu'un qui travaille
    DEAD // La connection est morte

}
