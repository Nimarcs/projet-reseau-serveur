package java.fr.ul.miage.reseau.mv;

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


    public static void main(String[] args) throws Exception {
        new Serveur().run(args);
    }

}