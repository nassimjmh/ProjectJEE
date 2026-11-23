package jee.monitor;

import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Moniteur purement passif : il observe les évènements, il ne décide de rien.
 * Il vérifie l'exclusion mutuelle (aucune fourchette utilisée par deux philosophes) et mesure l'équité.
 */
@RestController
@RequestMapping("/api/monitor")
public class MonitorController {

    private final List<Event> history = new ArrayList<>();
    private final List<String> violations = new ArrayList<>();
    private final Map<Integer, Integer> forkOwner = new HashMap<>();      // fourchette -> philosophe qui mange
    private final Map<Integer, Long> hungrySince = new HashMap<>();
    private final Map<Integer, Integer> meals = new TreeMap<>();
    private final Map<Integer, Long> maxWaitMs = new TreeMap<>();
    private final Map<Integer, Long> totalWaitMs = new TreeMap<>();

    @PostMapping("/event")
    public synchronized void receiveEvent(@RequestBody Event event) {
        event.setReceivedAt(System.currentTimeMillis());
        history.add(event);
        int p = event.getPhilosopherId();
        List<Integer> forks = parseForks(event.getDetail());

        switch (event.getState()) {
            case "HUNGRY" -> hungrySince.put(p, event.getReceivedAt());
            case "EATING" -> {
                for (int f : forks) {
                    Integer owner = forkOwner.put(f, p);
                    if (owner != null && owner != p) {
                        String v = "VIOLATION : fourchette F" + f + " utilisée par P" + owner + " ET P" + p
                                + " (horloge " + event.getLogicalClock() + ")";
                        violations.add(v);
                        System.err.println(v);
                    }
                }
                Long since = hungrySince.remove(p);
                if (since != null) {
                    long wait = event.getReceivedAt() - since;
                    meals.merge(p, 1, Integer::sum);
                    maxWaitMs.merge(p, wait, Math::max);
                    totalWaitMs.merge(p, wait, Long::sum);
                }
            }
            case "DONE" -> forks.forEach(f -> forkOwner.remove(f, p));
            default -> { }
        }
        System.out.println("Reçu - Philosophe: " + p + " | État: " + event.getState()
                + " | Horloge: " + event.getLogicalClock()
                + (event.getDetail() == null || event.getDetail().isEmpty() ? "" : " | " + event.getDetail()));
    }

    @GetMapping("/history")
    public synchronized List<Event> getHistory() {
        return new ArrayList<>(history);
    }

    @GetMapping("/violations")
    public synchronized List<String> getViolations() {
        return new ArrayList<>(violations);
    }

    /** Équité : nombre de repas et temps d'attente (HUNGRY -> EATING) par philosophe. */
    @GetMapping("/stats")
    public synchronized Map<String, Object> stats() {
        Map<Integer, Map<String, Object>> perPhilosopher = new TreeMap<>();
        for (int p : meals.keySet()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("meals", meals.get(p));
            m.put("avgWaitMs", totalWaitMs.get(p) / meals.get(p));
            m.put("maxWaitMs", maxWaitMs.get(p));
            perPhilosopher.put(p, m);
        }
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("violations", violations.size());
        res.put("philosophers", perPhilosopher);
        return res;
    }

    private static List<Integer> parseForks(String detail) {
        List<Integer> res = new ArrayList<>();
        if (detail == null) return res;
        int i = detail.indexOf("forks=");
        if (i < 0) return res;
        String s = detail.substring(i + 6).split(" ")[0];
        for (String x : s.split(",")) {
            try { res.add(Integer.parseInt(x.trim())); } catch (NumberFormatException ignored) { }
        }
        return res;
    }
}
