# V3.1 - Section critique sans verrou

N philosophes (un microservice Spring Boot chacun) partagent N fourchettes gérées par un `supervisor-service`. Pour corriger l'interblocage de V2, chaque philosophe demande ses **deux fourchettes en une seule requête** (« tout ou rien »). Le superviseur vérifie que les deux sont libres, puis les réserve. Plus d'interblocage : personne ne garde une fourchette en attendant l'autre.

Cette version garde **volontairement un défaut** :

- **Course** : le serveur web traite plusieurs requêtes en même temps (un thread par requête). « Vérifier » puis « réserver » ne sont pas atomiques : deux voisins peuvent tous les deux voir la fourchette partagée libre et la recevoir. Une pause de 15 ms dans `takeBothForks` rend la faille reproductible. C'est corrigé en V3.2 avec un verrou.

## Lancement en une commande (un seul terminal)

Depuis le dossier `V3.1/`, dans PowerShell, copier-coller :

```powershell
powershell -ExecutionPolicy Bypass -File .\run.ps1 -N 5
```

`run.ps1` a été généré par IA pour faciliter le lancement du programme sur Windows (il n'est pas nécessaire au projet, IntelliJ suffit).

`-N` est le nombre de philosophes et de fourchettes (5 par défaut). Le script compile les trois modules, lance le moniteur, le superviseur et les N philosophes, puis attend. **Ctrl+C arrête tout.** Tableau de bord : http://localhost:8080/

Les sorties de chaque service sont écrites dans `V3.1/logs/` (dossier généré par le script, non versionné).

## Lancement avec IntelliJ

1. Utiliser le JDK `openjdk-21`.
2. Lancer la configuration **PhilosophesV3.1 x5** : elle démarre le moniteur, le superviseur et 5 philosophes.
3. Ouvrir le tableau de bord : http://localhost:8080/

## Ports

| Service | Port |
|---|---|
| Moniteur | 8080 |
| Philosophe i | 8080 + i (8081, 8082, ...) |
| Superviseur | 9090 |

L'identifiant d'un philosophe est déduit de son port. `--philosopher.id=X` reste possible pour le forcer.

## Changer le nombre de philosophes

Il faut donner le même N au superviseur et aux philosophes :

- superviseur : `--supervisor.fork-count=N`
- chaque philosophe : `--philosopher.count=N --server.port=808X`

Dans IntelliJ : dupliquer une configuration `PhilosopheApplicationV3.1 (x)`, mettre `--server.port=8086 --philosopher.count=6` dans *Program arguments*, et ajouter `--supervisor.fork-count=6` à la configuration du superviseur. Pour que tout démarre ensemble, ajouter la nouvelle configuration à **PhilosophesV3.1 x5**.

## API du superviseur (`/api/supervisor`)

- `POST /forks/take?leftFork=X&rightFork=Y&philosopherId=Z` : prendre les deux fourchettes (tout ou rien)
- `POST /forks/release?leftFork=X&rightFork=Y&philosopherId=Z` : rendre les deux fourchettes
- `POST /reset` : libérer toutes les fourchettes
- `GET /state` : propriétaire de chaque fourchette (`-1` = libre)

## Tests

`SupervisorTests` (sans réseau) démontre la faille :

- la course : deux voisins obtiennent en même temps la fourchette qu'ils partagent ;
- plus de 2 philosophes sur 5 mangent en même temps ;
- la libération et la configuration du nombre de fourchettes.

Les deux premiers tests **réussissent quand la faille existe** : ils prouvent le défaut de cette version.
