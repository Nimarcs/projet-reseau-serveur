# Projet réseaux serveur

Ce serveur est l'une des deux partie d'un projet. Voyez le projet [worker](https://github.com/GuatierKlein/projet-reseau-maven) sur github
pour avoir une vue d'ensemble

# Compilation du projet avec MAVEN

Projet testé avec Java 17 et Apache Maven 3.6.3
Faire un mvm package vous permettra d'obtenir :
- des executables dans bindist/bin
- Un .jar dans target

# Utilisation

Le serveur prend en paramètre 5 options facultatives :
-c ou --connection suivi du nombre de connection maximale : Spécifie le nombre de connection maximale (default : 10)
-d ou--debug active le mode debug
-h ou --help affiche le message d’aide à l’usage
-p ou --port suivit d’un numéro de port : Spécifie le numéro du port (default : 1337)
-i ou --ip suivit de l’adresse ip : Spécifie l'adresse ip utilisé par le serveur (default : 127.0.0.1)

Le serveur doit être démarré avant de tenter de connecter des clients
