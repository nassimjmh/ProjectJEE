package jee.philosophe;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class PhilosopherRunner implements CommandLineRunner {

    private final int philosopherId;
    private final String monitorUrl;
    private final RestTemplate restTemplate;

    private int logicalClock = 0;

    // Injection via le constructeur
    public PhilosopherRunner(
            @Value("${philosopher.id}") int philosopherId,
            @Value("${monitor.url}") String monitorUrl,
            RestTemplate restTemplate) {
        this.philosopherId = philosopherId;
        this.monitorUrl = monitorUrl;
        this.restTemplate = restTemplate;
    }

    @Override
    public void run(String... args) {
        new Thread(() -> {
            try {
                while (true) {
                    // 1. Penser
                    sendEvent("THINKING");
                    Thread.sleep((long) (Math.random() * 2000 + 1000));

                    // 2. Avoir faim
                    sendEvent("HUNGRY");

                    // 3. Manger
                    sendEvent("EATING");
                    Thread.sleep((long) (Math.random() * 2000 + 1000));

                    // 4. Reprendre la pensée (Done)
                    sendEvent("DONE");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }


    private synchronized void sendEvent(String state) {
        logicalClock++; // Incrémentation de l'horloge logique locale
        Event event = new Event(philosopherId, state, logicalClock);
        try {
            restTemplate.postForObject(monitorUrl, event, Void.class);
        } catch (Exception e) {
            System.err.println("Impossible de joindre le moniteur : " + e.getMessage());
        }
    }
}
