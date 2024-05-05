package fr.ul.miage.reseau.mv;

import org.apache.commons.cli.*;

import java.io.Console;
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


    public static void main(String[] args) throws Exception {
        new Serveur().run(args);
    }

}