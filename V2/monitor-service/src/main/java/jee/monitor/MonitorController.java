package jee.monitor;

import org.springframework.web.bind.annotation.*;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/monitor")
public class MonitorController {

    private final List<Event> history = new ArrayList<>();

    @PostMapping("/event")
    public void receiveEvent(@RequestBody Event event) {
        history.add(event);
        // Optionnel : Trier l'historique par horloge logique ou afficher l'événement
        System.out.println("Reçu - Philosophe: " + event.getPhilosopherId()
                + " | État: " + event.getState()
                + " | Horloge: " + event.getLogicalClock());
    }

    @GetMapping("/history")
    public List<Event> getHistory() {
        return history;
    }
}
