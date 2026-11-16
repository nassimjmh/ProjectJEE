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

                    sendEvent("THINKING");
                    Thread.sleep((long) (Math.random() * 2000 + 1000));

                    sendEvent("HUNGRY");

                    sendEvent("EATING");
                    Thread.sleep((long) (Math.random() * 2000 + 1000));

                    sendEvent("DONE");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }


    private synchronized void sendEvent(String state) {
        logicalClock++;
        Event event = new Event(philosopherId, state, logicalClock);
        try {
            restTemplate.postForObject(monitorUrl, event, Void.class);
        } catch (Exception e) {
            System.err.println("Impossible de joindre le moniteur : " + e.getMessage());
        }
    }
}
