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
            writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()));
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            /*
            Processus de connection
            4 way hello
             */

            // On initie la connection
            Serveur.LOG.info("Connection " + number + " : " + "WHO_ARE_YOU_? envoyé");
            writer.println("WHO_ARE_YOU_?");
            writer.flush();

            // S'il ne répond pas correctement on tue la connection
            String supposedItsME = reader.readLine();
            if (!Objects.equals(supposedItsME, "ITS_ME")) {
                Serveur.LOG.info("Connection " + number + " : " + "ITS_ME non reçu");
                killConnection(socket, writer, reader);
            }
            Serveur.LOG.info("Connection " + number + " : " + "ITS_ME reçu");

            //On demande le mot de passe
            Serveur.LOG.info("Connection " + number + " : " + "GIMME_PASSWORD envoyé");
            writer.println("GIMME_PASSWORD");
            writer.flush();

            String supposedPassword = reader.readLine();
            // Si le mot de passe est incorrect
            if (!Objects.equals(supposedPassword, "PASSWD " + password)) {
                // On précise au client que son mot de passe est faux puis ferme la connection
                Serveur.LOG.info("Connection " + number + " : " + "YOU_DONT_FOOL_ME envoyé");
                writer.println("YOU_DONT_FOOL_ME");
                writer.flush();

                killConnection(socket, writer, reader);
            } else {
                Serveur.LOG.info("Connection " + number + " : " + "HELLO_YOU envoyé");
                writer.println("HELLO_YOU");
                writer.flush();

                String supposedREADY = reader.readLine();
                if (!Objects.equals(supposedREADY, "READY")) {
                    Serveur.LOG.info("Connection " + number + " : " + "READY non reçu");
                    killConnection(socket, writer, reader);
                }
                Serveur.LOG.info("Connection " + number + " : " + "OK envoyé");
                writer.println("OK");
                writer.flush();

                connectionStatus = ConnectionStatus.IDLE;
            }


            /*
            On boucle et écoute et traite les messages reçus
             */

            Serveur.LOG.info("Connection " + number + " : " + "Démarrage de la boucle");
            boolean errorFound = false;
            //On écoute et répond aux messages
            while (socket.isConnected() && !errorFound) {
                try {
                    //On récupère le message
                    String message = reader.readLine();

                    /*
                     * On traite le message
                     */


                    //Ready
                    if (Objects.equals(message, "READY")) {
                        readyReceived();
                        writer.println("OK");
                        writer.flush();


                    //Found
                    } else if (message != null && message.startsWith("FOUND ")) {
                        String[] args = message.split(" ");
                        if (args.length != 3) {
                            LOG.severe("Message reçu invalide, la connection va être fermée\nmessage :\n" + message);
                            killConnection(socket, writer, reader);
                        }
                        foundReceived(new Solution(args[1], args[2]));


                    //STATUS (Réponse au PROGRESS)
                    } else if (message != null && message.startsWith("STATUS ")) {
                        LOG.info("Connection " + getNumber() + " : Status reçu (" + message + ")");
                        statusReceived(message.substring(6));


                    //Message inconnu
                    } else {
                        LOG.severe("Message reçu invalide, la connection va être fermée\nmessage :\n" + message);
                        killConnection(socket, writer, reader);
                    }


                } catch (IOException e) {
                    errorFound = true;
                    //Si c'est fini on laisse juste tout mourir
                    if (!socket.isClosed()) {
                        LOG.severe("Erreur dans reader ou writer " + e.getMessage());
                        try {
                            killConnection(socket, writer, reader);
                        } catch (IOException ex) {
                            throw new RuntimeException(ex);
                        }
                    }

                }
            }
            Serveur.LOG.info("Connection " + number + " : " + "Sortie de la boucle");

            //Si on n'a pas déjà tué la connection, on la tue
            if (connectionStatus != ConnectionStatus.DEAD) {
                killConnection(socket, writer, reader);
            }
        } catch (IOException | RuntimeException e) {
            connectionStatus = ConnectionStatus.DEAD;
            e.printStackTrace();
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

    /**
     * Action a effectuer si on reçoit READY du worker
     */
    private void readyReceived() {
        connectionStatus = ConnectionStatus.IDLE;
    }

    /**
     * Action a effectuer si on reçoit FOUND du worker
     * @param solution solution trouvée
     */
    private void foundReceived(Solution solution) {
        serveur.solutionFound(this, solution);
    }

    /**
     * Action a effectuer si on reçoit STATUS du worker
     * @param status status reçu
     */
    private void statusReceived(String status) {
        serveur.statusObtained(this, status);
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

