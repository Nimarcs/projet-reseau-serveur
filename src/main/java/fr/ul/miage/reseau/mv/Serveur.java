package fr.ul.miage.reseau.mv;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;


public class Serveur {
    private static final Logger LOG = Logger.getLogger(Serveur.class.getName());
    private static int maxNbConnection = 10; // Nombre maximum de connection simultanée par défaut

    private List<Connection> connections = new LinkedList<>();

    private List<Thread> threads = new LinkedList<>();

    private static final String usageMessage = """
            Usage :
            Serveur
             [-c|--connection NUMBER_OF_CONNECTION] - Specifie le nombre de connection maximale (default : 10)
             [-d|--debug] - Active le mode debug
             [-h|--help] - Affiche ce message
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

        // Initialise le groupe de connection
        ThreadGroup connectionGroup = new ThreadGroup("Groupe de connection");
        Connection connection = new Connection(password, this);
        Thread thread = new Thread(connectionGroup, connection);
        thread.start();
        threads.add(thread);
        connections.add(connection);

        // écoute les commandes
        boolean keepGoing = true;
        final Console console = System.console();
        while (keepGoing) {
            final String commande = console.readLine("$ ");
            if (commande == null) break;

            keepGoing = processCommand(commande.trim());
        }
    }

    private boolean processCommand(String cmd) throws Exception {
        if (("quit").equals(cmd)) {
            for (Connection connection : connections) {
                connection.scheduleKilling();
            }
            //On attend que toutes les connections meurt
            while (connections.stream()
                    .filter((connection) -> (connection.getConnectionStatus() != ConnectionStatus.DEAD))
                    .toList().isEmpty()) {
                //TODO ajouter timeout
            }
            return false;
        }

        if (("cancel").equals(cmd)) {
            // TODO cancel task

        } else if (("status").equals(cmd)) {
            for (Connection connection : connections) {
                connection.updateStatus();
            }
            while (connections.stream()
                    .filter((connection) -> (connection.getStatusObtained() == null))
                    .toList().isEmpty()) {
                //TODO ajouter timeout
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

            } catch (NumberFormatException e) {
                LOG.severe("solve <d> - try to mine with given difficulty prend un nombre en parametre.\n" + cmd.substring(6) + " n'est pas un nombre");
            }
        }

        return true;
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
            LOG.info("Solution validée");
            for (Connection c : connections) {
                c.tooSlow();
            }
        } else {
            //TODO traiter le cas ou le client m'a menti
        }
    }

    private String generateWork(int difficulty) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("https://projet-raizo-idmc.netlify.app/.netlify/functions/generate_work?d=");
            sb.append(difficulty);
            String url = sb.toString();
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

                LOG.severe("Erreur : " + response.toString());
            } else {
                LOG.severe("Erreur : La requête a échoué avec le code " + responseCode);
            }


            return String.valueOf(responseCode);
        } catch (IOException e) {
            e.printStackTrace();
            return String.valueOf(0);
        }
    }

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
                System.out.println("Erreur : " + errorResponse.toString());
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