# V2 - Philosophes sous la supervision d'un superviseur

N philosophes (un microservice Spring Boot chacun) partagent N fourchettes gérées par un `supervisor-service` naïf. Pour manger, un philosophe demande sa fourchette gauche, puis sa fourchette droite, une à la fois. Le superviseur ne décide rien : il attribue une fourchette si elle est libre, et accepte qu'on la rende.

Cette version est **volontairement fragile**. Elle sert à montrer deux défauts :

- **Course** : le superviseur vérifie qu'une fourchette est libre, puis l'écrit en deux étapes séparées. Deux philosophes qui arrivent en même temps peuvent recevoir la même fourchette (une pause de 20 ms dans `takeFork` rend la faille reproductible).
- **Interblocage** : si tous les philosophes prennent leur fourchette gauche, chacun attend la droite, tenue par son voisin. Plus personne ne mange.

## Lancement en une commande (un seul terminal)

Depuis le dossier `V2/`, dans PowerShell, copier-coller :

```powershell
powershell -ExecutionPolicy Bypass -File .\run.ps1 -N 5
```

`run.ps1` a été généré par IA pour faciliter le lancement du programme sur Windows (il n'est pas nécessaire au projet, IntelliJ suffit).

`-N` est le nombre de philosophes et de fourchettes (5 par défaut). Le script compile les trois modules, lance le moniteur, le superviseur et les N philosophes, puis attend. **Ctrl+C arrête tout.** Tableau de bord : http://localhost:8080/

Les sorties de chaque service sont écrites dans `V2/logs/` (dossier généré par le script, non versionné).

## Lancement avec IntelliJ

1. Utiliser le JDK `openjdk-21`.
2. Lancer la configuration **PhilosophesV2 x5** : elle démarre le moniteur, le superviseur et 5 philosophes.
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

Dans IntelliJ : dupliquer une configuration `PhilosopheApplicationV2 (x)`, mettre `--server.port=8086 --philosopher.count=6` dans *Program arguments*, et ajouter `--supervisor.fork-count=6` à la configuration du superviseur. Pour que tout démarre ensemble, ajouter la nouvelle configuration à **PhilosophesV2 x5**.

## API du superviseur (`/api/supervisor`)

- `GET /fork/{id}/available` : la fourchette est-elle libre ?
- `POST /fork/{id}/take?philosopherId=X` : la prendre
- `POST /fork/{id}/release?philosopherId=X` : la rendre
- `GET /state` : propriétaire de chaque fourchette (`-1` = libre)

## Tests

`SupervisorTests` (sans réseau) démontre les failles :

- la course : plusieurs philosophes obtiennent la même fourchette ;
- l'interblocage : quand chacun tient sa fourchette gauche, personne ne peut prendre la droite ;
- la libération et la configuration du nombre de fourchettes.

Les deux premiers tests **réussissent quand la faille existe** : ils prouvent le défaut de cette version.
