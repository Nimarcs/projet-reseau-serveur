package fr.ul.miage.reseau.mv;

import org.apache.commons.cli.*;

import java.io.Console;
import java.util.logging.Level;
import java.util.logging.Logger;


public class Serveur {

    private static final Logger LOG = Logger.getLogger(Serveur.class.getName());

    public void run(String[] args) throws Exception {

        // Gestion d'un mode debug
        Options options = new Options();
        Option debug = new Option("d", "debug", false, "Allow debugging output");
        options.addOption(debug);
        CommandLineParser commandLineParser = new DefaultParser();
        try {
            CommandLine commandLine = commandLineParser.parse(options, args);
            LOG.setLevel(Level.WARNING);
            if (commandLine.hasOption(debug)) {
                LOG.setLevel(Level.INFO);
            }
        } catch (ParseException exception) {
            LOG.severe("Erreur dans la ligne de commande");
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("run", options);
            System.exit(1);
        }


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

    public String generateWork(){
        try {
            // URL du service
            String url = "https://projet-raizo-idmc.netlify.app/.netlify/functions/generate_work?d=4";
            
            // Création de l'objet URL
            URL obj = new URL(url);
            
            // Ouverture de la connexion
            HttpURLConnection con = (HttpURLConnection) obj.openConnection();
            
            // Spécification de la méthode de la requête
            con.setRequestMethod("GET");
            
            // Ajout de l'en-tête Authorization
            con.setRequestProperty("Authorization", "Bearer recWL3uDC7EY3haCr");
            
            // Lecture de la réponse
            int responseCode = con.getResponseCode();
            System.out.println("Code de réponse : " + responseCode);
            if (responseCode == HttpURLConnection.HTTP_CREATED) { // Succès
                BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
                String inputLine;
                StringBuffer response = new StringBuffer();
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();
                // Affichage des données de la tâche
                System.out.println("Données de la tâche : " + response.toString());
                return response.toString();
            } else if (responseCode == HttpURLConnection.HTTP_CONFLICT) { // Conflit
                BufferedReader in = new BufferedReader(new InputStreamReader(con.getErrorStream()));
                String inputLine;
                StringBuffer response = new StringBuffer();
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();
                // Affichage du message d'erreur
                System.out.println("Erreur : " + response.toString());
                return "Conflit";
            } else { // Autre code d'erreur
                System.out.println("Erreur : La requête a échoué avec le code " + responseCode);
            }
            return "Erreur";
        } catch (IOException e) {
            e.printStackTrace();
            return "Erreur";
        }
    }

    public boolean validateWork(){
        try {
            // URL du service
            String url = "https://projet-raizo-idmc.netlify.app/.netlify/functions/validate_work";
            
            // Paramètres de la requête
            int difficulty = 3;
            String nonce = "f42";
            String hash = "000a23efc...";
            
            // Création de l'objet URL
            URL obj = new URL(url);
            
            // Ouverture de la connexion
            HttpURLConnection con = (HttpURLConnection) obj.openConnection();
            
            // Spécification de la méthode de la requête
            con.setRequestMethod("POST");
            
            // Activation de l'envoi de données
            con.setDoOutput(true);
            
            // Création du corps de la requête
            String requestBody = "{\"d\": " + difficulty + ", \"n\": \"" + nonce + "\", \"h\": \"" + hash + "\"}";
            
            // Envoi des données
            DataOutputStream wr = new DataOutputStream(con.getOutputStream());
            wr.writeBytes(requestBody);
            wr.flush();
            wr.close();
            
            // Lecture de la réponse
            int responseCode = con.getResponseCode();
            System.out.println("Code de réponse : " + responseCode);
            if (responseCode == HttpURLConnection.HTTP_OK) { // Succès
                System.out.println("La tâche a été validée avec succès.");
                return true;
            } else if (responseCode == HttpURLConnection.HTTP_CONFLICT) { // Conflit
                System.out.println("Erreur : La difficulté a déjà été résolue.");
                return false;
            } else { // Autre code d'erreur
                System.out.println("Erreur : La requête a échoué avec le code " + responseCode);
            }
            return false;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }


    public static void main(String[] args) throws Exception {
        new Serveur().run(args);
    }

}