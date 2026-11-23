package jee.philosophe.distributed;

import jee.philosophe.Event;
import jee.philosophe.distributed.Messages.DoneMsg;
import jee.philosophe.distributed.Messages.TokenMsg;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Protocole distribué par jetons (type Ricart-Agrawala généralisé à des ressources à jeton).
 *
 * <p><b>Principe</b>
 * <ul>
 *   <li>Le philosophe Pi possède au départ le jeton de sa fourchette Fi. Un jeton est unique : seul son
 *       détenteur peut utiliser la fourchette, d'où l'exclusion mutuelle par construction.</li>
 *   <li>Pour manger, Pi choisit 2 fourchettes (règle {@link #chooseForks()}), incrémente son horloge de Lamport
 *       et diffuse REQUEST(id, ts, forks) à tous les autres. Chacun mémorise la demande.</li>
 *   <li>Priorité d'une demande = (ts, id), ordre total (Lamport + id pour départager les ex aequo).</li>
 *   <li>Règle de transfert : un détenteur qui n'est pas en train de manger avec la fourchette f envoie son jeton
 *       à la demande la plus prioritaire (la plus ancienne) parmi les demandes connues portant sur f
 *       (la sienne comprise). Elle est ré-évaluée à chaque message reçu et à chaque fin de repas.</li>
 *   <li>Un philosophe mange quand il détient les 2 jetons de sa demande. À la fin il diffuse DONE(id, ts)
 *       (la demande est oubliée) et ses jetons partent vers les demandes en attente.</li>
 * </ul>
 *
 * <p><b>Interblocage</b> : impossible. La demande la plus ancienne R0 est la plus prioritaire pour chacun de ses
 * jetons chez tous les détenteurs ; personne ne la garde (sauf un repas en cours, qui se termine) donc R0 obtient
 * ses 2 jetons. Un jeton détenu par une demande plus jeune est cédé à une plus ancienne : pas d'attente circulaire.
 *
 * <p><b>Famine</b> : impossible. La priorité (ts, id) d'une demande est figée et l'horloge de Lamport garantit
 * qu'une nouvelle demande a un ts supérieur à toute demande déjà vue : il n'existe qu'un nombre fini de demandes
 * plus anciennes (FIFO global), donc chaque demande est servie en temps borné : équité.
 *
 * <p><b>Pertes / délais</b> : les messages sont retransmis (REQUEST rediffusée périodiquement tant que le
 * philosophe a faim, DONE/TOKEN réessayés). Les traitements sont idempotents (dédoublonnage par (from, ts)).
 * Une REQUEST arrivée après le DONE correspondant est ignorée (lastDone).
 */
@Service
public class DistributedProtocolService {

    private static final Comparator<TokenRequest> PRIORITY =
            Comparator.comparingLong(TokenRequest::ts).thenComparingInt(TokenRequest::from);

    private record Transfer(int fork, int to) {}

    private final int id;
    private final int n;
    private final String monitorUrl;
    private final long retransmitMs;
    private final RestTemplate rest;
    private final Map<Integer, String> peerUrls = new HashMap<>();
    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "p-sender");
        t.setDaemon(true);
        return t;
    });

    // ---- état protégé par lock ----
    private final Object lock = new Object();
    private long clock = 0;                                          // horloge de Lamport
    private final Set<Integer> held = new TreeSet<>();               // jetons détenus
    private final Map<Integer, TokenRequest> known = new HashMap<>();// demandes actives des autres
    private final Map<Integer, Long> lastDone = new HashMap<>();     // dernière demande terminée par philosophe
    private TokenRequest myRequest;
    private boolean eating;
    private int meals;
    private long changes;                                            // évite les réveils perdus

    public DistributedProtocolService(@Value("${philosopher.id}") int id,
                                      @Value("${philosopher.count:5}") int n,
                                      @Value("${philosopher.base-port:8080}") int basePort,
                                      @Value("${philosopher.host:localhost}") String host,
                                      @Value("${philosopher.retransmit-ms:2000}") long retransmitMs,
                                      @Value("${monitor.url}") String monitorUrl,
                                      RestTemplate rest) {
        this.id = id;
        this.n = n;
        this.monitorUrl = monitorUrl;
        this.retransmitMs = retransmitMs;
        this.rest = rest;
        for (int j = 1; j <= n; j++) {
            if (j != id) peerUrls.put(j, "http://" + host + ":" + (basePort + j) + "/api/philosopher");
        }
        held.add(id);
    }

    // ------------------------------------------------------------------ règle d'attribution

    /**
     * Règle d'attribution automatique : le philosophe réclame sa propre fourchette Fi et une seconde fourchette
     * quelconque, sans contrainte de voisinage. La seconde tourne sur tous les autres philosophes d'un repas
     * à l'autre : Fj avec j = ((i-1 + 1 + (repas mod (N-1))) mod N) + 1. Ainsi toutes les fourchettes circulent.
     */
    private List<Integer> chooseForks() {
        int offset = 1 + (meals % (n - 1));
        int other = ((id - 1 + offset) % n) + 1;
        return List.of(id, other);
    }

    public String forksToString(TokenRequest r) {
        return r.forks().get(0) + "," + r.forks().get(1);
    }

    // ------------------------------------------------------------------ côté demandeur

    /** Bloque jusqu'à détenir les 2 jetons. Retourne la demande servie. */
    public TokenRequest acquire() throws InterruptedException {
        TokenRequest req;
        synchronized (lock) {
            clock++;
            req = new TokenRequest(id, clock, chooseForks());
            myRequest = req;
            changes++;
        }
        event("HUNGRY", "forks=" + forksToString(req) + " ts=" + req.ts());
        log("DEMANDE diffusée ts=" + req.ts() + " forks=" + req.forks());
        broadcast("/request", req);
        long lastBroadcast = System.currentTimeMillis();

        while (true) {
            List<Transfer> out;
            boolean ready;
            long seen;
            synchronized (lock) {
                out = evaluateLocked();
                ready = held.containsAll(req.forks());
                if (ready) eating = true;
                seen = changes;
            }
            send(out);
            if (ready) {
                log("a les 2 jetons " + req.forks() + " -> MANGE");
                return req;
            }
            synchronized (lock) {
                if (changes == seen) lock.wait(retransmitMs);
            }
            if (System.currentTimeMillis() - lastBroadcast >= retransmitMs) {
                log("RETRANSMISSION de la demande ts=" + req.ts());
                broadcast("/request", req);
                lastBroadcast = System.currentTimeMillis();
            }
        }
    }

    /** Fin de repas : libère les fourchettes, annonce DONE, les jetons vont aux demandes en attente. */
    public void release(TokenRequest req) {
        // le moniteur est prévenu AVANT la libération des jetons (ordre des traces cohérent)
        event("DONE", "forks=" + forksToString(req));
        DoneMsg done;
        synchronized (lock) {
            eating = false;
            myRequest = null;
            meals++;
            clock++;
            done = new DoneMsg(id, req.ts(), clock);
            changes++;
            lock.notifyAll();
        }
        log("FIN de repas, DONE diffusé");
        broadcast("/done", done);
        pump();
    }

    // ------------------------------------------------------------------ côté récepteur

    public void onRequest(TokenRequest msg) {
        synchronized (lock) {
            clock = Math.max(clock, msg.ts()) + 1;
            if (msg.ts() <= lastDone.getOrDefault(msg.from(), 0L)) return; // déjà terminée (message en retard)
            TokenRequest old = known.get(msg.from());
            if (old == null || old.ts() < msg.ts()) {                      // sinon : doublon de retransmission
                known.put(msg.from(), msg);
                log("MÉMORISE demande de P" + msg.from() + " ts=" + msg.ts() + " forks=" + msg.forks());
            }
        }
        pump();
    }

    public void onDone(DoneMsg msg) {
        synchronized (lock) {
            clock = Math.max(clock, msg.clock()) + 1;
            lastDone.merge(msg.from(), msg.ts(), Math::max);
            TokenRequest r = known.get(msg.from());
            if (r != null && r.ts() <= msg.ts()) known.remove(msg.from());
        }
        pump();
    }

    public void onToken(TokenMsg msg) {
        synchronized (lock) {
            clock = Math.max(clock, msg.clock()) + 1;
            if (!held.add(msg.fork())) log("!! ANOMALIE : jeton F" + msg.fork() + " reçu en double");
            else log("REÇOIT jeton F" + msg.fork() + " de P" + msg.from());
        }
        pump();
    }

    // ------------------------------------------------------------------ règle de transfert

    /** À appeler sous lock. Retire de {@code held} les jetons à céder et les retourne. */
    private List<Transfer> evaluateLocked() {
        List<Transfer> out = new ArrayList<>();
        for (Integer f : new ArrayList<>(held)) {
            if (eating && myRequest != null && myRequest.forks().contains(f)) continue; // en cours d'utilisation
            TokenRequest best = null;
            for (TokenRequest r : known.values()) {
                if (r.forks().contains(f) && (best == null || PRIORITY.compare(r, best) < 0)) best = r;
            }
            if (myRequest != null && myRequest.forks().contains(f)
                    && (best == null || PRIORITY.compare(myRequest, best) < 0)) {
                best = myRequest;
            }
            if (best != null && best.from() != id) {
                held.remove(f);
                out.add(new Transfer(f, best.from()));
            }
        }
        return out;
    }

    private void pump() {
        List<Transfer> out;
        synchronized (lock) {
            out = evaluateLocked();
            changes++;
            lock.notifyAll();
        }
        send(out);
    }

    // ------------------------------------------------------------------ envoi

    private void send(List<Transfer> transfers) {
        for (Transfer t : transfers) executor.submit(() -> sendToken(t));
    }

    private void sendToken(Transfer t) {
        for (int attempt = 1; attempt <= 20; attempt++) {
            long c;
            synchronized (lock) { c = ++clock; }
            try {
                rest.postForObject(peerUrls.get(t.to()) + "/token", new TokenMsg(t.fork(), id, c), Void.class);
                log("ENVOIE jeton F" + t.fork() + " -> P" + t.to());
                return;
            } catch (Exception e) {
                log("échec envoi jeton F" + t.fork() + " -> P" + t.to() + " (essai " + attempt + ")");
                sleep(500);
            }
        }
        synchronized (lock) { held.add(t.fork()); }   // destinataire injoignable : on garde le jeton
        pump();
    }

    private void broadcast(String path, Object msg) {
        for (Map.Entry<Integer, String> p : peerUrls.entrySet()) {
            executor.submit(() -> {
                for (int attempt = 1; attempt <= 30; attempt++) {
                    try {
                        rest.postForObject(p.getValue() + path, msg, Void.class);
                        return;
                    } catch (Exception e) {
                        sleep(500);
                    }
                }
            });
        }
    }

    /** Évènement d'observation pour le moniteur. */
    public void event(String state, String detail) {
        long c;
        synchronized (lock) { c = ++clock; }
        try {
            rest.postForObject(monitorUrl, new Event(id, state, c, detail), Void.class);
        } catch (Exception e) {
            System.err.println("Impossible de joindre le moniteur : " + e.getMessage());
        }
    }

    public Map<String, Object> snapshot() {
        synchronized (lock) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", id);
            m.put("clock", clock);
            m.put("tokensHeld", new ArrayList<>(held));
            m.put("eating", eating);
            m.put("myRequest", myRequest);
            m.put("knownRequests", new ArrayList<>(known.values()));
            m.put("meals", meals);
            return m;
        }
    }

    private void log(String s) {
        long c;
        synchronized (lock) { c = clock; }
        System.out.printf("[P%d | t=%d] %s%n", id, c, s);
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
