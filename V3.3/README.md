# V3.3 – Protocole distribué par jetons (sans superviseur)

N microservices philosophes Spring Boot (5 par défaut, ports 8081..8085) + un moniteur **passif** (port 8080) qui
observe et vérifie, sans jamais décider de quoi que ce soit.

## Protocole (`philosophe/.../distributed/DistributedProtocolService`)

| Élément | Choix |
|---|---|
| Jetons | Un jeton unique par fourchette ; Pi détient le jeton de Fi au départ. Seul le détenteur peut utiliser la fourchette : **exclusion mutuelle par construction**. |
| Attribution des 2 fourchettes | Pi réclame Fi + une seconde fourchette quelconque Fj, avec `j = ((i-1 + 1 + (repas mod (N-1))) mod N) + 1` (aucune contrainte de voisinage, toutes les fourchettes circulent). |
| Demande | `POST /api/philosopher/request` diffusée à tous : `(from, ts, forks)`. `ts` = horloge de Lamport. Chacun la mémorise. |
| Priorité | `(ts, id)` : ordre total, la plus ancienne gagne. |
| Transfert | Un détenteur qui ne mange pas avec la fourchette f envoie le jeton (`POST /token`) à la demande **la plus prioritaire** portant sur f (la sienne comprise). Réévalué à chaque message reçu et à chaque fin de repas. |
| Repas | Quand Pi détient ses 2 jetons. À la fin : `POST /done` diffusé (la demande est oubliée), les jetons partent vers les demandes en attente. |
| Retransmission | REQUEST rediffusée toutes les `philosopher.retransmit-ms` tant que le philosophe a faim ; envois REQUEST / DONE / TOKEN réessayés si le pair est injoignable. Tout est idempotent (dédoublonnage par `(from, ts)`, `lastDone` ignore une REQUEST arrivée après son DONE). |
| Concurrence | Tout l'état est protégé par un verrou ; les appels HTTP se font hors verrou, de façon asynchrone. |

### Interblocage
Impossible : la demande la plus ancienne R0 est la plus prioritaire pour chacun de ses jetons chez tous les
détenteurs, donc tous les lui cèdent (au pire après la fin d'un repas en cours, qui est fini). Un philosophe qui a
déjà un jeton le cède à une demande plus ancienne, il n'y a donc pas d'attente circulaire (contrairement à
« je prends une fourchette puis j'attends l'autre »).

### Famine / équité
Impossible : la priorité d'une demande est figée et l'horloge de Lamport garantit qu'une nouvelle demande est plus
récente que toutes celles déjà vues. Il n'y a donc qu'un nombre fini de demandes plus anciennes : service FIFO global,
attente bornée.

### Limites assumées
- Pas de tolérance aux pannes franches : un jeton perdu avec un philosophe qui tombe est perdu.
- Si une réponse HTTP d'un transfert de jeton est perdue alors que le jeton est arrivé, l'émetteur peut le réémettre ;
  le récepteur le signale par `ANOMALIE : jeton reçu en double` dans ses logs (non observé dans les tests).

## Lancer
Config IntelliJ « PhilosophesV3.3 x5 » (moniteur + 5 philosophes), ou à la main :
```
java -jar monitor-service/target/*.jar
java -jar philosophe/target/*.jar --philosopher.id=1 --server.port=8081   # idem 2..5 / 8082..8085
```

## Analyse des traces
- Logs de chaque philosophe : `[Pi | t=horloge]` DEMANDE / MÉMORISE / ENVOIE jeton / REÇOIT jeton / MANGE / DONE.
- `GET :8080/api/monitor/history` : tous les évènements avec horloge logique.
- `GET :8080/api/monitor/violations` : doit être `[]` (le moniteur signale si une fourchette est utilisée par deux
  philosophes en même temps).
- `GET :8080/api/monitor/stats` : repas, attente moyenne et max par philosophe (équité).
- `GET :808i/api/philosopher/state` : état interne (jetons détenus, demandes connues).

Test réalisé (5 philosophes, 45 s, demandes simultanées) : **0 violation**, 9–10 repas par philosophe,
attente max ≈ 4 s, aucun jeton dupliqué.
