# V1 - Philosophes gâtés

N philosophes indépendants (un microservice Spring Boot chacun) qui pensent, ont faim, mangent, et envoient leurs événements à un moniteur. Aucune fourchette : ils mangent tous en même temps.

## Lancement en une commande (un seul terminal)

Depuis le dossier `V1/`, dans PowerShell, copier-coller :

```powershell
powershell -ExecutionPolicy Bypass -File .\run.ps1 -N 10
```

`run.ps1` a été généré par IA pour faciliter le lancement du programme sur Windows (il n'est pas nécessaire au projet, IntelliJ suffit).

`-N` est le nombre de philosophes (5 par défaut). Le script compile les deux modules, lance le moniteur et les N philosophes, puis attend. **Ctrl+C arrête tout.** Tableau de bord : http://localhost:8080/

Les sorties de chaque service sont écrites dans `V1/logs/` (dossier généré par le script, non versionné).

## Lancement avec IntelliJ

1. Utiliser le JDK `openjdk-21`.
2. Lancer la configuration **PhilosophesV1 x5** : elle démarre le moniteur et 5 philosophes.
3. Ouvrir le tableau de bord : http://localhost:8080/

## Ports

| Service | Port |
|---|---|
| Moniteur | 8080 |
| Philosophe i | 8080 + i (8081, 8082, ...) |

L'identifiant d'un philosophe est déduit de son port. `--philosopher.id=X` reste possible pour le forcer.

## Changer le nombre de philosophes

Il n'y a pas de limite dans le code : un philosophe de plus = une instance de plus avec un port différent.

Dans IntelliJ : dupliquer une configuration `PhilosopheApplicationV1 (x)`, et mettre dans *Program arguments* `--server.port=8086` (puis 8087, ...). Elle n'est lancée que si on l'ajoute à la configuration composée **PhilosophesV1 x5** (ou qu'on la lance à la main).

En ligne de commande (après `mvnw package` dans chaque module) :

```
java -jar monitor-service/target/monitor-serviceV1-0.0.1-SNAPSHOT.jar
java -jar philosophe/target/philosopheV1-0.0.1-SNAPSHOT.jar --server.port=8081
java -jar philosophe/target/philosopheV1-0.0.1-SNAPSHOT.jar --server.port=8082
```

## Événements

Chaque philosophe envoie `POST /api/monitor/event` avec `{ philosopherId, state, logicalClock }`. L'horloge logique est un compteur local à chaque philosophe, incrémenté à chaque événement.

## API du moniteur

- `GET /` : tableau de bord
- `GET /api/monitor/state` : état courant de chaque philosophe et derniers événements
- `GET /api/monitor/history` : tous les événements reçus
