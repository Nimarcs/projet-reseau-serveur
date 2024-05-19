package fr.ul.miage.reseau.mv;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Objects;

public class Connection implements Runnable {

    private ConnectionStatus connectionStatus;

    private final String password;

    private boolean solvedBySomeoneElse;

    private Order currentOrder;

    private boolean needToUpdateStatus;

    private Solution solutionFound;

    private boolean needToBeKilled;

    private String statusObtained;

    private PrintWriter writer;

    private BufferedReader reader;

    private Socket socket;

    /**
     * TODO A termes mettre une façade qui permet juste de retransmettre quand on trouve la solution
     */
    private Serveur serveur;

    public Connection(String password, Serveur serveur) {
        this.password = password;
        connectionStatus = ConnectionStatus.ALONE;
        solvedBySomeoneElse = false;
        statusObtained = null;
        currentOrder = null;
        this.serveur = serveur;
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
            System.out.println("En attente de connection");
            socket = serverSocket.accept();
            System.out.println("Connection trouvé avec " + socket.toString());
            writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()));
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            // On initie la connection
            System.out.println("WHO_ARE_YOU_? envoyé");
            writer.println("WHO_ARE_YOU_?");
            writer.flush();

            // S'il ne répond pas correctement on tue la connection
            String supposedItsME = reader.readLine();
            if (!Objects.equals(supposedItsME, "ITS_ME")) {
                System.out.println("ITS_ME non reçu");
                killConnection(socket, writer, reader);
            }
            System.out.println("reçu");

            //On demande le mot de passe
            System.out.println("GIMME_PASSWORD envoyé");
            writer.println("GIMME_PASSWORD");
            writer.flush();

            String supposedPassword = reader.readLine();
            // Si le mot de passe est incorrect
            if (!Objects.equals(supposedPassword, "PASSWD " + password)) {
                // On précise au client que son mot de passe est faux puis ferme la connection
                System.out.println("YOU_DONT_FOOL_ME envoyé");
                writer.println("YOU_DONT_FOOL_ME");
                writer.flush();

                killConnection(socket, writer, reader);
            } else {
                System.out.println("HELLO_YOU envoyé");
                writer.println("HELLO_YOU");
                writer.flush();

                String supposedREADY = reader.readLine();
                if (!Objects.equals(supposedREADY, "READY")) {
                    System.out.println("READY non reçu");
                    killConnection(socket, writer, reader);
                }
                System.out.println("OK envoyé");
                writer.println("OK");
                writer.flush();

                connectionStatus = ConnectionStatus.IDLE;
            }

            //on démarre un écouteur
            Ecouteur ecouteur = new Ecouteur(this, socket, writer, reader);
            Thread ecouteurThread = new Thread(ecouteur);
            ecouteurThread.start();

            System.out.println("Démarrage de la boucle");
            //On maintient la connection
            while (socket.isConnected()) {

                //Si on bosse pas mais qu'on est censé bosser
                if (currentOrder != null && !solvedBySomeoneElse) {

                }

                //Si on a besoin du status du client
                if (needToUpdateStatus) {

                }

                //Si on bosse mais que c'est déjà résolu
                if (solvedBySomeoneElse) {

                }

                //Si on est en train de fermer le serveur et que donc on ferme tout
                if (needToBeKilled) {

                }


            }
            System.out.println("Sortie de la boucle");

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
    public void killConnection(Socket socket, PrintWriter writer, BufferedReader reader) throws IOException {
        connectionStatus = ConnectionStatus.DEAD;
        writer.close();
        reader.close();
        socket.close();
    }

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
        this.solutionFound = null;
        this.solvedBySomeoneElse = false;
        this.currentOrder = order;
        System.out.println("Commence ça travailler sur " + currentOrder.getPayload());
        assert connectionStatus == ConnectionStatus.IDLE;
        writer.println("PAYLOAD " + currentOrder.getPayload());
        writer.println("NONCE " + currentOrder.getStart() + " " + currentOrder.getIncrement());
        writer.println("SOLVE " + currentOrder.getDifficulty());
        writer.flush();
    }

    /**
     * Donne l'information à la connection que le problème en cours a déjà été résolu
     */
    public void tooSlow() {
        this.solvedBySomeoneElse = true;
        assert connectionStatus == ConnectionStatus.WORKING;
        writer.println("SOLVED");
        writer.flush();
        connectionStatus = ConnectionStatus.IDLE;
    }

    public void updateStatus() {
        this.needToUpdateStatus = true;
        this.statusObtained = null;
        needToUpdateStatus = false;
        writer.println("PROGRESS");
        writer.flush();
    }

    public void readyReceived() {
        connectionStatus = ConnectionStatus.IDLE;
    }

    public void foundReceived(Solution solution) {
        serveur.solutionFound(this, solution);
    }

    /**
     * Return the current status of the connection
     * /!\ It is not the status of the worker
     *
     * @return status of the connection
     */
    public ConnectionStatus getConnectionStatus() {
        return connectionStatus;
    }

    public void scheduleKilling() {
        this.needToBeKilled = true;
        try {
            killConnection(socket, writer, reader);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String getStatusObtained() {
        return statusObtained;
    }

    public void setStatusObtained(String statusObtained) {
        this.statusObtained = statusObtained;
    }

    public Order getCurrentOrder() {
        return currentOrder;
    }
}

