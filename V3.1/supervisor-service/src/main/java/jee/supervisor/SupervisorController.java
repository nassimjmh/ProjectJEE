package jee.supervisor;

import org.springframework.web.bind.annotation.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/supervisor")
public class SupervisorController {

    public static final int FREE = -1;
    private final Map<Integer, Integer> forks = new HashMap<>();

    public SupervisorController() {
        for (int i = 1; i <= 5; i++) {
            forks.put(i, FREE);
        }
    }

    @PostMapping("/forks/take")
    public boolean takeBothForks(
            @RequestParam int leftFork,
            @RequestParam int rightFork,
            @RequestParam int philosopherId) {

        if (forks.getOrDefault(leftFork, -2) == FREE && forks.getOrDefault(rightFork, -2) == FREE) {

            // Temps d'attente pour simuler la course
            try { Thread.sleep(15); } catch (InterruptedException ignored) {}

            forks.put(leftFork, philosopherId);
            forks.put(rightFork, philosopherId);

            System.out.println("Philosophe " + philosopherId + " a pris les fourchettes " + leftFork + " et " + rightFork);
            return true;
        }

        return false;
    }

    @PostMapping("/forks/release")
    public void releaseBothForks(
            @RequestParam int leftFork,
            @RequestParam int rightFork,
            @RequestParam int philosopherId) {

        if (forks.getOrDefault(leftFork, -2) == philosopherId) {
            forks.put(leftFork, FREE);
        }
        if (forks.getOrDefault(rightFork, -2) == philosopherId) {
            forks.put(rightFork, FREE);
        }
        System.out.println("Philosophe " + philosopherId + " a libéré les fourchettes " + leftFork + " et " + rightFork);
    }

    @PostMapping("/reset")
    public void reset() {
        for (int i = 1; i <= 5; i++) {
            forks.put(i, FREE);
        }
    }


    @GetMapping("/state")
    public Map<Integer, Integer> getState() {
        return forks;
    }
}
