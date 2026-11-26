package jee.monitor;

import org.springframework.web.bind.annotation.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@RestController
@RequestMapping("/api/monitor")
public class MonitorController {

    private static final int RECENT_EVENTS = 20;

    private final List<Event> history = new ArrayList<>();
    private final Map<Integer, Event> lastEvent = new TreeMap<>();
    private final Map<Integer, Integer> meals = new TreeMap<>();

    @PostMapping("/event")
    public synchronized void receiveEvent(@RequestBody Event event) {
        history.add(event);
        lastEvent.put(event.getPhilosopherId(), event);
        if ("DONE".equals(event.getState())) {
            meals.merge(event.getPhilosopherId(), 1, Integer::sum);
        }
        System.out.println("Reçu - Philosophe: " + event.getPhilosopherId()
                + " | État: " + event.getState()
                + " | Horloge: " + event.getLogicalClock());
    }

    @GetMapping("/history")
    public synchronized List<Event> getHistory() {
        return new ArrayList<>(history);
    }

    @GetMapping("/state")
    public synchronized Map<String, Object> getState() {
        List<Map<String, Object>> philosophers = new ArrayList<>();
        for (Event e : lastEvent.values()) {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("id", e.getPhilosopherId());
            p.put("state", e.getState());
            p.put("logicalClock", e.getLogicalClock());
            p.put("meals", meals.getOrDefault(e.getPhilosopherId(), 0));
            philosophers.add(p);
        }
        List<Event> recent = new ArrayList<>(
                history.subList(Math.max(0, history.size() - RECENT_EVENTS), history.size()));
        Collections.reverse(recent);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("philosophers", philosophers);
        res.put("recent", recent);
        res.put("totalEvents", history.size());
        return res;
    }
}
