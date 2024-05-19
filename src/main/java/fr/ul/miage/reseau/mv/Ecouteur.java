package fr.ul.miage.reseau.mv;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Objects;
import java.util.logging.Logger;

public class Ecouteur implements Runnable {

    private final Connection connection;

    private final Socket socket;

    private final BufferedReader reader;

    private final PrintWriter writer;

    private boolean errorFound = false;

    private static final Logger LOG = Logger.getLogger(Ecouteur.class.getName());

    public Ecouteur(Connection connection, Socket socket, PrintWriter writer, BufferedReader reader) {
        this.connection = connection;
        this.socket = socket;
        this.reader = reader;
        this.writer = writer;
    }

    /**
     * Runs this operation.
     */
    @Override
    public void run() {
        LOG.info("Écouteur de la connection " + connection.getNumber() + " lancé");
        while (socket.isConnected() && !errorFound) {
            try {
                String message = reader.readLine();

                //Ready
                if (Objects.equals(message, "READY")) {
                    connection.readyReceived();
                    writer.println("OK");
                    writer.flush();

                    //Found
                } else if (message != null && message.startsWith("FOUND ")) {
                    String[] args = message.split(" ");
                    if (args.length != 3) {
                        LOG.severe("Message reçu invalide, la connection va être fermée\nmessage :\n" + message);
                        connection.killConnection(socket, writer, reader);
                    }
                    connection.foundReceived(new Solution(args[1], args[2]));

                    //STATUS (Réponse au PROGRESS)
                } else if (message != null && message.startsWith("STATUS ")) {
                    LOG.info("Connection " + connection.getNumber() + " : Status reçu (" + message + ")");
                    connection.statusReceived(message.substring(6));

                    //Message inconnu
                } else {
                    LOG.severe("Message reçu invalide, la connection va être fermée\nmessage :\n" + message);
                    connection.killConnection(socket, writer, reader);
                }

            } catch (IOException e) {
                errorFound = true;
                //Si c'est fini on laisse juste tout mourir
                if (!socket.isClosed()) {
                    LOG.severe("Erreur dans reader ou writer " + e.getMessage());
                    try {
                        connection.killConnection(socket, writer, reader);
                    } catch (IOException ex) {
                        throw new RuntimeException(ex);
                    }
                }

            }
        }
    }
}
