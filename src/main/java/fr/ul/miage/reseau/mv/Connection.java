package fr.ul.miage.reseau.mv;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Objects;

public class Connection implements Runnable {

    private ConnectionStatus connectionStatus;

    private final String password;

    private Order currentOrder;

    private PrintWriter writer;

    private BufferedReader reader;

    private Socket socket;

    private int number;

    private Serveur serveur;

    public Connection(String password, Serveur serveur, int number) {
        this.password = password;
        this.number = number;
        this.serveur = serveur;

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
            final InetAddress bindAddress = InetAddress.getByName("127.0.0.1");
            ServerSocket serverSocket = new ServerSocket(25555, 1, bindAddress);
            Serveur.LOG.info("Connection " + number + " : " + "En attente de connection");
            socket = serverSocket.accept();
            Serveur.LOG.info("Connection " + number + " : " + "Connection trouvé avec " + socket.toString());
            writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()));
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            /*
            Processus de connection
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
            Séparation en 2 thread
            Lecture / Maintiens en vie
             */

            //On démarre un écouteur
            //Lecture
            Ecouteur ecouteur = new Ecouteur(this, socket, writer, reader);
            Thread ecouteurThread = new Thread(ecouteur);
            ecouteurThread.start();

            //Maintient en vie
            Serveur.LOG.info("Connection " + number + " : " + "Démarrage de la boucle");
            //On maintient la connection
            while (socket.isConnected()) {
                //On attend la fin de vie de la socket
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

    /*
    Appelé par l'écouteur
     */

    /**
     * Tue la connection fournie proprement
     *
     * @param socket socket de la connection à tuer
     * @throws IOException renvoyé si une erreur se produit lors de la fermeture du socket
     */
    public void killConnection(Socket socket, PrintWriter writer, BufferedReader reader) throws IOException {
        connectionStatus = ConnectionStatus.DEAD;
        writer.close();
        reader.close();
        socket.close();
    }

    public void readyReceived() {
        connectionStatus = ConnectionStatus.IDLE;
    }

    public void foundReceived(Solution solution) {
        serveur.solutionFound(this, solution);
    }

    public void statusReceived(String status) {
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

    public void obtainStatus() {
        writer.println("PROGRESS");
        writer.flush();
    }

    public void scheduleKilling() {
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

    public Order getCurrentOrder() {
        return currentOrder;
    }

    public int getNumber() {
        return number;
    }

    public ConnectionStatus getConnectionStatus() {
        return connectionStatus;
    }
}

