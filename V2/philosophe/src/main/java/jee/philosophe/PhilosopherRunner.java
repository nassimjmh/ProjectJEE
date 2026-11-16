package jee.philosophe;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class PhilosopherRunner implements CommandLineRunner {

    private final int philosopherId;
    private final String monitorUrl;
    private final String supervisorUrl;
    private final RestTemplate restTemplate;
    private int logicalClock = 0;
    private final int leftFork;
    private final int rightFork;

    public PhilosopherRunner(
            @Value("${philosopher.id}") int philosopherId,
            @Value("${monitor.url}") String monitorUrl,
            @Value("${supervisor.url}") String supervisorUrl,
            RestTemplate restTemplate) {
        this.philosopherId = philosopherId;
        this.monitorUrl = monitorUrl;
        this.restTemplate = restTemplate;
        this.supervisorUrl = supervisorUrl;
        this.leftFork = philosopherId;
        this.rightFork = (philosopherId % 5) + 1;
    }

    @Override
    public void run(String... args) {
        new Thread(() -> {
            try {
                while (true) {

                    sendEvent("THINKING");
                    Thread.sleep((long) (Math.random() * 2000 + 1000));

                    sendEvent("HUNGRY");

                    eat(leftFork);
                    eat(rightFork);

                    sendEvent("EATING");
                    Thread.sleep((long) (Math.random() * 2000 + 1000));

                    // Libérer les fourchettes
                    restTemplate.postForObject(supervisorUrl + "/fork/" + leftFork + "/release?philosopherId=" + philosopherId, null, Void.class);
                    restTemplate.postForObject(supervisorUrl + "/fork/" + rightFork + "/release?philosopherId=" + philosopherId, null, Void.class);

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

    private synchronized void sendSupervisorEvent() {

        SupervisorEvent SupervisorEvent = new SupervisorEvent(philosopherId);
        try {
            restTemplate.postForObject(supervisorUrl, SupervisorEvent, Void.class);
        } catch (Exception e) {
            System.err.println("Impossible de joindre le monitor : " + e.getMessage());
        }
    }

    private void eat(int fork) throws InterruptedException {

        boolean hasFork = false;
        while (!hasFork) {
            Boolean available = restTemplate.getForObject(supervisorUrl + "/fork/" + fork + "/available", Boolean.class);
            if (Boolean.TRUE.equals(available)) {
                try {
                    Boolean response = restTemplate.postForObject(
                            supervisorUrl + "/fork/" + fork + "/take?philosopherId=" + philosopherId,
                            null,
                            Boolean.class
                    );

                    hasFork = (response != null && response);
                } catch (Exception e) {
                    System.err.println("Impossible de joindre le superviseur : " + e.getMessage());
                }
            }
            if (!hasFork) {
                Thread.sleep(100);
            }
        }
    }

}
