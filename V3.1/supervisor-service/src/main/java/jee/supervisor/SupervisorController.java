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

    @GetMapping("/fork/{forkId}/available")
    public boolean isAvailable(@PathVariable int forkId) {
        return forks.containsKey(forkId) && forks.get(forkId) == FREE;
    }

    @PostMapping("/fork/{forkId}/take")
    public boolean takeFork(@PathVariable int forkId, @RequestParam int philosopherId) {
        if (forks.containsKey(forkId) && forks.get(forkId) == FREE) {
            try { Thread.sleep(20); } catch (InterruptedException ignored) {}
            forks.put(forkId, philosopherId);
            System.out.println("Philosophe: " + philosopherId + " took Fork: " + forkId);
            return true;
        }
        return false;
    }

    @PostMapping("/fork/{forkId}/release")
    public void releaseFork(@PathVariable int forkId, @RequestParam int philosopherId) {
        if (forks.containsKey(forkId) && forks.get(forkId).equals(philosopherId)) {
            forks.put(forkId, FREE);
            System.out.println("Philosophe: " + philosopherId
                    + " release Fork: " + forkId);
        }
    }

    @GetMapping("/state")
    public Map<Integer, Integer> getState() {
        return forks;
    }
}
