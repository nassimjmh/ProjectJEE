package jee.philosophe;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/philosophe")
public class PhilosopheController {

    @Autowired
    private PhilosopheService philosopheService;

    @Value("${philosopher.id}")
    private int philosopherId;

    @GetMapping("/fourchette")
    public boolean demanderFourchette() {
        return philosopheService.preterFourchette();
    }

    @PostMapping("/rendre-fourchette")
    public void rendreFourchette() {
        philosopheService.recupererFourchette();
    }

    private final Map<String, List<PhilosopheEvent>> history = new ConcurrentHashMap<>();

    @PostMapping("history")
    public void receiveEvent(@RequestBody PhilosopheEvent event) {
        // Crée une liste synchronisée si le philosophe n'existe pas encore
        List<PhilosopheEvent> events = history.computeIfAbsent(
                event.id(),
                k -> Collections.synchronizedList(new ArrayList<>())
        );

        // Bloc synchronized pour ajouter et trier sans conflit concurrent
        synchronized (events) {
            events.add(event);
            events.sort(Comparator.comparingInt(PhilosopheEvent::logicalClock));
        }

        System.out.println("Reçu : " + event);
    }

}