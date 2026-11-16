package jee.monitor;

import org.springframework.web.bind.annotation.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/supervisor")
public class SupervisorController {

    private final Map<Integer, Integer> forks = new ConcurrentHashMap<>();

    @GetMapping("/fork/{forkId}/available")
    public boolean isAvailable(@PathVariable int forkId) {
        return forks.containsKey(forkId) && forks.get(forkId) == null;
    }

    @PostMapping("/fork/{forkId}/take")
    public synchronized boolean takeFork(@PathVariable int forkId, @RequestParam int philosopherId) {
        if (forks.containsKey(forkId) && forks.get(forkId) == null) {
            forks.put(forkId, philosopherId);
            System.out.println("Philosophe: " + philosopherId
                    + " took Fork: " + forkId);
            return true;
        }
        return false;
    }

    @PostMapping("/fork/{forkId}/release")
    public synchronized void releaseFork(@PathVariable int forkId, @RequestParam int philosopherId) {
        if (forks.containsKey(forkId) && forks.get(forkId).equals(philosopherId)) {
            forks.put(forkId, null);
            System.out.println("Philosophe: " + philosopherId
                    + " release Fork: " + forkId);
        }
    }
}
