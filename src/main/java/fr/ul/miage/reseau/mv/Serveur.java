package fr.ul.miage.reseau.mv;

import org.apache.commons.cli.*;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;


public class Serveur {

    private static final String CMD_LINE_SYNTAX = "run";

    private static final Logger LOG = Logger.getLogger(Serveur.class.getName());
    private static int maxNbConnection = 10; // Nombre maximum de connection simultanée par défaut

    public void run(String[] args) throws Exception {

        // Gestion d'un mode debug
        Options options = new Options();
        Option opDebug = new Option("d", "debug", false, "Allow debugging output");
        Option opConnection = new Option("c", "connection", true, "Maximum number of allowed connection");
        options.addOption(opDebug);
        options.addOption(opConnection);
        CommandLineParser commandLineParser = new DefaultParser();
        try {
            CommandLine commandLine = commandLineParser.parse(options, args);
            LOG.setLevel(Level.WARNING);
            if (commandLine.hasOption(opDebug)) {
                LOG.setLevel(Level.INFO);
            }
            if (commandLine.hasOption(opConnection)){
                String optionValue = commandLine.getOptionValue(opConnection);
                maxNbConnection = Integer.parseInt(optionValue);
            }
        } catch (ParseException exception) {
            LOG.severe("Erreur dans la ligne de commande");
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp(CMD_LINE_SYNTAX, options);
            System.exit(1);
        } catch (NumberFormatException exception){
            LOG.severe("connection attend un entier positif");
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp(CMD_LINE_SYNTAX, options);
            System.exit(2);
        }

        // Initialise le groupe de connection
        ThreadGroup connectionGroup = new ThreadGroup("Groupe de connection");

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
            // TODO shutdown
            return false;
        }

        if (("cancel").equals(cmd)) {
            // TODO cancel task

        } else if (("status").equals(cmd)) {
            // TODO show workers status

        } else if (("help").equals(cmd.trim())) {
            System.out.println(" • status - display informations about connected workers");
            System.out.println(" • solve <d> - try to mine with given difficulty");
            System.out.println(" • cancel - cancel a task");
            System.out.println(" • help - describe available commands");
            System.out.println(" • quit - terminate pending work and quit");

        } else if (cmd.startsWith("solve")) {
            // TODO start solving ...
        }

        return true;
    }

    public int generateWork(int difficulty) throws org.json.simple.parser.ParseException{
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
            System.out.println("Code de réponse : " + responseCode);
            if (responseCode == HttpURLConnection.HTTP_CREATED) {
                BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
                String inputLine;
                StringBuffer response = new StringBuffer();
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();
                JSONParser parser = new JSONParser();
                JSONObject jsonResponse = (JSONObject) parser.parse(response.toString());
                String data = (String) jsonResponse.get("data");
                System.out.println("Données de la tâche : " + data);
            } else if (responseCode == HttpURLConnection.HTTP_CONFLICT) {
                BufferedReader in = new BufferedReader(new InputStreamReader(con.getErrorStream()));
                String inputLine;
                StringBuffer response = new StringBuffer();
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();
                JSONParser parser = new JSONParser();
                JSONObject jsonResponse = (JSONObject) parser.parse(response.toString());
                String details = (String) jsonResponse.get("details");
                System.out.println("Erreur : " + details);
            } else {
                System.out.println("Erreur : La requête a échoué avec le code " + responseCode);
            }
            return responseCode;
        } catch (IOException e) {
            e.printStackTrace();
            return 0;
        }
    }

    public int validateWork(int difficulty, String nonce, String hash) throws org.json.simple.parser.ParseException{
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

            try(OutputStream os = con.getOutputStream()) {
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
                JSONParser parser = new JSONParser();
                JSONObject jsonResponse = (JSONObject) parser.parse(errorResponse.toString());
                String details = (String) jsonResponse.get("details");
                System.out.println("Erreur : " + details);
            }
            return responseCode;
        } catch (IOException e) {
            e.printStackTrace();
            return 0;
        }
    }


    public static void main(String[] args) throws Exception {
        assert true;
        new Serveur().run(args);
        //new Serveur().validateWork(5, "4",  "0c4f12188163dae848bd233757f3b0966972dd9efcaa54af4de92dfceb2c755e");
        //new Serveur().generateWork(3);
    }

}