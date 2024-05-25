package fr.ul.miage.reseau.mv;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;


public class Serveur {

    public static final Logger LOG = Logger.getLogger(Serveur.class.getName());

    private static int maxNbConnection = 10; // Nombre maximum de connection simultanée par défaut

    private List<Connection> connections = new LinkedList<>();

    private List<Thread> threads = new LinkedList<>();

    private static final String usageMessage = """
            Usage :
            Serveur
             [-c|--connection NUMBER_OF_CONNECTION] - Specifie le nombre de connection maximale (default : 10)
             [-d|--debug] - Active le mode debug
             [-h|--help] - Affiche ce message
             [-p|--port PORT_NUMBER] - Specifie le numero du port (default : 1337)
             [-i|--ip IP_ADRESS] - Specifie l'adresse ip utilise par le serveur (default : 127.0.0.1)
            """;

    private String password = "GAUTIER_EST_TRES_CHAUD";

    public void run(String[] args) throws Exception {

        List<String> arguments = Arrays.asList(args);
        LOG.setLevel(Level.WARNING);

        //Gestion de --help
        if (arguments.contains("-h") || arguments.contains("--help")) {
            System.out.println(usageMessage);
            System.exit(0);
        }
        // Gestion d'un mode debug
        if (arguments.contains("-d") || arguments.contains("--debug")) {
            LOG.setLevel(Level.INFO);
        }

        // Gestion de --connection
        int pos = arguments.indexOf("-c");
        if (pos == -1) pos = arguments.indexOf("--connection");
        // If pos == -1 then the parameter -c is not there
        if (pos != -1) {
            try {
                maxNbConnection = Integer.parseInt(arguments.get(pos + 1));
            } catch (NumberFormatException | IndexOutOfBoundsException e) {
                LOG.severe(usageMessage);
                System.exit(1);
            }
        }

        // Gestion de --port
        int port = 1337;
        pos = arguments.indexOf("-p");
        if (pos == -1) pos = arguments.indexOf("--port");
        // If pos == -1 then the parameter -c is not there
        if (pos != -1) {
            try {
                port = Integer.parseInt(arguments.get(pos + 1));
            } catch (NumberFormatException | IndexOutOfBoundsException e) {
                LOG.severe(usageMessage);
                System.exit(2);
            }
        }

        // Gestion de --ip
        InetAddress bindAddress = InetAddress.getByName("127.0.0.1");
        pos = arguments.indexOf("-i");
        if (pos == -1) pos = arguments.indexOf("--ip");
        // If pos == -1 then the parameter -c is not there
        if (pos != -1) {
            try {
                bindAddress = InetAddress.getByName(arguments.get(pos + 1));
            } catch (java.net.UnknownHostException | IndexOutOfBoundsException e) {
                LOG.severe(usageMessage);
                System.exit(3);
            }
        }


        // Initialise le groupe de connection
        ServerSocket serverSocket = new ServerSocket(port, maxNbConnection, bindAddress);
        ThreadGroup connectionGroup = new ThreadGroup("Groupe de connection");
        Connection firstConnection = new Connection(password, this, threads.size(), serverSocket, LOG.getLevel() == Level.INFO);
        Thread firstThread = new Thread(connectionGroup, firstConnection);
        firstThread.start();
        threads.add(firstThread);
        connections.add(firstConnection);

        // Écoute les commandes & augmente le nombre de connection
        boolean keepGoing = true;
        final Console console = System.console();
        while (keepGoing) {
            final String commande = console.readLine("$ ");
            if (commande == null) break;
            LOG.info("Max connection : " + maxNbConnection + " - last connection status : " + connections.get(connections.size()-1).getConnectionStatus() + " (" + (connections.size()-1) + ")");

            //Si toute les connections sont prises et qu'on a pas atteint le max, on en ajoute une
            if (maxNbConnection > connections.size() && connections.get(connections.size()-1).getConnectionStatus() != ConnectionStatus.ALONE){
                System.out.println("Totalité de connection utilisé, ajout d'une nouvelle connection");
                //On créer et lance la nouvelle connection
                Connection newConnection = new Connection(password, this, threads.size(), serverSocket, LOG.getLevel() == Level.INFO);
                Thread newThread = new Thread(connectionGroup, newConnection);
                newThread.start();
                threads.add(newThread);
                connections.add(newConnection);
            }

            keepGoing = processCommand(commande.trim());
        }
        System.exit(0);
    }

    private boolean processCommand(String cmd) {
        if (("quit").equals(cmd)) {
            for (Connection connection : getNotAloneConnections()) {
                connection.killConnection();
            }
            return false;
        }

        if (("cancel").equals(cmd)) {
            for (Connection connection : getNotAloneConnections()) {
                connection.cancelOrder();
            }

        } else if (("status").equals(cmd)) {
            LOG.info("Demande des status de toutes les connections");
            for (Connection connection : getNotAloneConnections()) {
                connection.obtainStatus();
            }

        } else if (("help").equals(cmd.trim())) {
            System.out.println(" • status - display informations about connected workers");
            System.out.println(" • solve <d> - try to mine with given difficulty");
            System.out.println(" • cancel - cancel a task");
            System.out.println(" • help - describe available commands");
            System.out.println(" • quit - terminate pending work and quit");

        } else if (cmd.startsWith("solve")) {
            try {
                int difficulty = Integer.parseInt(cmd.substring(6));
                String payload = generateWork(difficulty);
                Pattern pattern = Pattern.compile("[0-9]{1,3}");

                //Si le payload est un nombre, on a une erreur
                if (pattern.matcher(payload).matches()) {
                    LOG.severe("Erreur web : " + payload);
                } else {
                    //Sinon on peut continuer
                    LOG.info("Payload récupéré : " + payload);


                    //TODO faire en sorte que les connections soit prete a recevoir l'ordre au lieu de juste prier

                    //On récupère les connections qui sont prêtes à travailler
                    List<Connection> connectionPreteATravailler = connections.stream()
                            .filter((connection) -> (connection.getConnectionStatus() == ConnectionStatus.IDLE)).toList();
                    //On demande aux connection de travailler
                    int nbConnected = connectionPreteATravailler.size();
                    LOG.info("Le serveur demande aux " + nbConnected + " client de travailler");
                    for (int i = 0; i < nbConnected; i++) {
                        Connection connection = connectionPreteATravailler.get(i);
                        connection.setNewOrder(new Order(i, nbConnected, difficulty, payload));
                    }
                }
                System.out.println("Minage débuté");

            } catch (NumberFormatException e) {
                LOG.severe("solve <d> - try to mine with given difficulty prend un nombre en parametre.\n" + cmd.substring(6) + " n'est pas un nombre");
            }
        }

        return true;
    }

    private List<Connection> getNotAloneConnections() {
        return connections.stream()
                .filter((connection -> connection.getConnectionStatus() != ConnectionStatus.ALONE))
                .toList();
    }

    /**
     * Méthode appelé par la connection lorsqu'elle trouve la solution
     * Doit envoyer la solution pour verification et retransmettre aux autres connection que le résultat à été trouvé
     *
     * @param connection connection qui a trouvé la solution
     * @param solution   solution trouvée
     */
    public void solutionFound(Connection connection, Solution solution) {
        LOG.info("Solution trouvé, envoie de la demande de validation");
        Order order = connection.getCurrentOrder();
        int webResponseCode = validateWork(order.getDifficulty(), solution.getNonce(), solution.getHash());

        //Si la solution est correcte
        if (webResponseCode == HttpURLConnection.HTTP_OK) {
            System.out.println("Solution validée");
            for (Connection c : getNotAloneConnections()) {
                c.tooSlow();
            }
        } else {
            LOG.severe("Le worker a menti ce salaud !");
            LOG.severe("Info : " + order.getDifficulty() + " - " + solution.getNonce() + " - " + solution.getHash());
            //TODO traiter le cas ou le client m'a menti
        }
    }

    /**
     * Méthode appelé par la connection lorsqu'elle obtient le status de son client
     *
     * @param connection connection dont le status est associé
     * @param status     status du client connecté
     */
    public void statusObtained(Connection connection, String status) {
        System.out.printf("Connection %d : %s\n", connection.getNumber(), status);
    }

    /**
     * Méthode qui demande du travail a l'api web
     * Si le travail est trouvé renvoie le payload
     * Si une erreur nous reviens le code d'erreur web est renvoyé
     * @param difficulty difficulté demandé pour le travail
     * @return Code d'erreur web | Payload
     */
    private String generateWork(int difficulty) {
        try {
            String url = "https://projet-raizo-idmc.netlify.app/.netlify/functions/generate_work?d=" + difficulty;
            URL obj = new URL(url);
            HttpURLConnection con = (HttpURLConnection) obj.openConnection();
            con.setRequestMethod("GET");
            con.setRequestProperty("Authorization", "Bearer recWL3uDC7EY3haCr");

            int responseCode = con.getResponseCode();
            LOG.info("Code de réponse : " + responseCode);
            if (responseCode == HttpURLConnection.HTTP_CREATED || responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
                String inputLine;
                StringBuffer response = new StringBuffer();
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();

                LOG.info(response.toString());
                return response.toString().split("\"")[3];
            } else if (responseCode == HttpURLConnection.HTTP_CONFLICT) {
                BufferedReader in = new BufferedReader(new InputStreamReader(con.getErrorStream()));
                String inputLine;
                StringBuffer response = new StringBuffer();
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();

                LOG.severe("Erreur : " + response);
            } else {
                LOG.severe("Erreur : La requête a échoué avec le code " + responseCode);
            }


            return String.valueOf(responseCode);
        } catch (IOException e) {
            e.printStackTrace();
            return String.valueOf(0);
        }
    }

    /**
     * Envoie une solution a l'api web
     * @param difficulty difficultée associé au probleme
     * @param nonce nonce trouvé
     * @param hash hash obtenu avec le payload + nonce
     * @return Code de reponse web
     */
    private int validateWork(int difficulty, String nonce, String hash) {
        try {
            String url = "https://projet-raizo-idmc.netlify.app/.netlify/functions/validate_work";

            StringBuilder sb = new StringBuilder();
            sb.append("{\"d\": ");
            sb.append(difficulty);
            sb.append(", \"n\": \"");
            sb.append(nonce);
            sb.append("\", \"h\": \"");
            sb.append(hash);
            sb.append("\"}");
            String requestBody = sb.toString();
            URL obj = new URL(url);
            HttpURLConnection con = (HttpURLConnection) obj.openConnection();
            con.setRequestMethod("POST");
            con.setDoOutput(true);
            con.setRequestProperty("Authorization", "Bearer recWL3uDC7EY3haCr");
            con.setRequestProperty("Accept", "application/json");

            try (OutputStream os = con.getOutputStream()) {
                byte[] input = requestBody.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int responseCode = con.getResponseCode();
            System.out.println("Code de réponse : " + responseCode);
            if (responseCode == HttpURLConnection.HTTP_OK) {
                System.out.println("La tâche a été validée avec succès.");
            } else if (responseCode == HttpURLConnection.HTTP_CONFLICT) {
                System.out.println("Erreur : La difficulté a déjà été résolue.");
            } else {
                BufferedReader errorReader = new BufferedReader(new InputStreamReader(con.getErrorStream()));
                StringBuilder errorResponse = new StringBuilder();
                String line;
                while ((line = errorReader.readLine()) != null) {
                    errorResponse.append(line);
                }
                errorReader.close();
                System.out.println("Erreur : " + errorResponse);
            }
            return responseCode;
        } catch (IOException e) {
            e.printStackTrace();
            return 0;
        }
    }


    public static void main(String[] args) throws Exception {
        new Serveur().run(args);
    }

}