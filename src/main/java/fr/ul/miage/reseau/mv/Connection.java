package fr.ul.miage.reseau.mv;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Connection implements Runnable {

    private ConnectionStatus connectionStatus;

    private final String password;

    private Order currentOrder;

    private PrintWriter writer;

    private BufferedReader reader;

    private Socket socket;

    private int number;

    private Serveur serveur;

    private ServerSocket serverSocket;

    private static final Logger LOG = Logger.getLogger(Connection.class.getName());

    public Connection(String password, Serveur serveur, int number, ServerSocket serverSocket, boolean debug) {
        this.password = password;
        this.number = number;
        this.serveur = serveur;
        this.serverSocket = serverSocket;

        LOG.setLevel(Level.WARNING);
        if (debug)
            LOG.setLevel(Level.INFO);

        connectionStatus = ConnectionStatus.ALONE;
        currentOrder = null;
    }


    /**
     * When an object implementing interface {@code Runnable} is used
     * to create a thread, starting the thread causes the object's
     * {@code run} method to be called in that separately executing
     * thread.
     * <p>
     * The general contract of the method {@code run} is that it may
     * take any action whatsoever.
     *
     * @see Thread#run()
     */
    @Override
    public void run() {
        connectionStatus = ConnectionStatus.ALONE;
        try {
            // On cree la connection avec le client
            Serveur.LOG.info("Connection " + number + " : " + "En attente de connection");
            socket = serverSocket.accept();
            Serveur.LOG.info("Connection " + number + " : " + "Connection trouvé avec " + socket.toString());

            //Cette connexion viens d'être prise donc le serveurs n'en a plus de disponible
            serveur.addConnexion();

            writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()));
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            /*
            Processus de connection
            4 way hello
             */

            do4WaysHello();

            /*
            On boucle et écoute et traite les messages reçus
             */

            Serveur.LOG.info("Connection " + number + " : " + "Démarrage de la boucle");

            //On écoute et répond aux messages
            while (socket.isConnected()) {
                String message = reader.readLine();
                processMessage(message);
            }

            Serveur.LOG.info("Connection " + number + " : " + "Sortie de la boucle");

            //Si on n'a pas déjà tué la connection, on la tue
            if (connectionStatus != ConnectionStatus.DEAD) {
                killConnection(socket, writer, reader);
            }
        } catch (IOException | RuntimeException e) {
            connectionStatus = ConnectionStatus.DEAD;
            try {
                killConnection(socket, writer, reader);
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
            e.printStackTrace();
        }
    }


    /*
    Appelé par le serveur
     */

    /**
     * Donne un nouvel ordre aux worker connecter
     * La connection doit être IDLE
     *
     * @param order ordre à faire
     * @throws IllegalStateException renvoyé si on tente de donner un nouvel ordre alors que le status de la connection n'est pas en IDLE
     */
    public void setNewOrder(Order order) {
        if (connectionStatus != ConnectionStatus.IDLE)
            throw new IllegalStateException("Can't define new order if the connection is not idle");

        //On définit l'ordre
        this.currentOrder = order;

        //On lance la demande de travail
        Serveur.LOG.info("Connection " + number + " : " + "Commence ça travailler sur " + currentOrder.getPayload());
        writer.println("PAYLOAD " + currentOrder.getPayload());
        writer.println("NONCE " + currentOrder.getStart() + " " + currentOrder.getIncrement());
        writer.println("SOLVE " + currentOrder.getDifficulty());
        writer.flush();
    }

    /**
     * Annule l'ordre courrant (donc la recherche de solution)
     */
    public void cancelOrder() {
        currentOrder = null;
        //on ment aux clients
        writer.println("CANCELLED");
        writer.flush();
    }

    /**
     * Donne l'information à la connection que le problème en cours a déjà été résolu
     */
    public void tooSlow() {
        assert connectionStatus == ConnectionStatus.WORKING;
        writer.println("SOLVED");
        writer.flush();
    }

    /**
     * Envoie au worker que l'on veux son status actuel
     */
    public void obtainStatus() {
        writer.println("PROGRESS");
        writer.flush();
    }

    /**
     * Kill the connection
     */
    public void killConnection() {
        try {
            killConnection(socket, writer, reader);
        } catch (IOException e) {
            Serveur.LOG.severe("Echec de la fermeture propre de la connection " + number);
            e.printStackTrace();
        }
    }

    /*
    Methode privée
     */


    /**
     * Permet d'initier une connection entre client et worker
     * Ce passe en théorie comme ceci
     * > WHO_ARE_YOU_?
     * < ITS_ME
     * > GIMME_PASSWORD
     * < PASSWD
     * > HELLO_YOU / YOU_DONT_FOOL_ME
     * Un READY est supposé être reçu après ce 4 ways hello
     * @throws IOException en cas d'erreur sur l'envoie et la reception de message
     */
    private void do4WaysHello() throws IOException {
        // On initie la connection
        // S'il ne répond pas correctement on tue la connection

        // > WHO_ARE_YOU_?
        Serveur.LOG.info("Connection " + number + " : " + "WHO_ARE_YOU_? envoyé");
        writer.println("WHO_ARE_YOU_?");
        writer.flush();

        // < ITS_ME
        String supposedItsME = reader.readLine();
        if (!Objects.equals(supposedItsME, "ITS_ME")) {
            Serveur.LOG.info("Connection " + number + " : " + "ITS_ME non reçu");
            killConnection(socket, writer, reader);
        }
        Serveur.LOG.info("Connection " + number + " : " + "ITS_ME reçu");

        //On demande le mot de passe
        // > GIMME_PASSWORD
        Serveur.LOG.info("Connection " + number + " : " + "GIMME_PASSWORD envoyé");
        writer.println("GIMME_PASSWORD");
        writer.flush();

        String supposedPassword = reader.readLine();
        // Si le mot de passe est incorrect
        if (!Objects.equals(supposedPassword, "PASSWD " + password)) {
            // > YOU_DONT_FOOL_ME
            // On précise au client que son mot de passe est faux puis ferme la connection
            Serveur.LOG.info("Connection " + number + " : " + "YOU_DONT_FOOL_ME envoyé");
            writer.println("YOU_DONT_FOOL_ME");
            writer.flush();

            killConnection(socket, writer, reader);

        // Si le mot de passe est correct
        } else {
            // > HELLO_YOU
            Serveur.LOG.info("Connection " + number + " : " + "HELLO_YOU envoyé");
            writer.println("HELLO_YOU");
            writer.flush();
        }
    }

    /**
     * On traite le message reçu du worker
     * @param message message reçu
     */
    private void processMessage(String message) throws IOException {


        //Ready
        if (Objects.equals(message, "READY")) {
            connectionStatus = ConnectionStatus.IDLE;
            writer.println("OK");
            writer.flush();


            //Found
        } else if (message != null && message.startsWith("FOUND ")) {
            String[] args = message.split(" ");
            if (args.length != 3) {
                LOG.severe("Message reçu invalide, la connection va être fermée\nmessage :\n" + message);
                killConnection(socket, writer, reader);
            }
            serveur.solutionFound(this, new Solution(args[1], args[2]));


            //STATUS (Réponse au PROGRESS)
        } else if (message != null && message.startsWith("STATUS ")) {
            LOG.info("Connection " + getNumber() + " : Status reçu (" + message + ")");
            serveur.statusObtained(this, message.substring(6));

            //Message inconnu
        } else {
            LOG.severe("Message reçu invalide, la connection va être fermée\nmessage :\n" + message);
            killConnection(socket, writer, reader);
        }
    }


    /**
     * Tue la connection fournie proprement
     *
     * @param socket socket de la connection à tuer
     * @throws IOException renvoyé si une erreur se produit lors de la fermeture du socket
     */
    private void killConnection(Socket socket, PrintWriter writer, BufferedReader reader) throws IOException {
        connectionStatus = ConnectionStatus.DEAD;
        writer.close();
        reader.close();
        socket.close();
    }


    /*
    Getter
     */

    /**
     * getter de currentOrder l'ordre en cours de résolution
     * @return currentOrder ordre en cours de résolution
     */
    public Order getCurrentOrder() {
        return currentOrder;
    }

    /**
     * Getter de number le numéro de la connection
     * @return number
     */
    public int getNumber() {
        return number;
    }

    /**
     * Getter du status de la connection
     * /!\ Différent du status du thread /!\
     * @return status de la connection
     */
    public ConnectionStatus getConnectionStatus() {
        return connectionStatus;
    }
}

