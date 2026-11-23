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

                    eat();

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

    private void eat() throws InterruptedException {
        int leftFork = philosopherId;
        int rightFork = (philosopherId % 5) + 1;
        boolean hasBothForks = false;

        while (!hasBothForks) {
            try {
                Boolean response = restTemplate.postForObject(
                        supervisorUrl + "/forks/take?leftFork=" + leftFork + "&rightFork=" + rightFork + "&philosopherId=" + philosopherId,
                        null,
                        Boolean.class
                );
                hasBothForks = (response != null && response);
            } catch (Exception e) {
                System.err.println("Superviseur injoignable : " + e.getMessage());
            }

            if (!hasBothForks) {
                Thread.sleep(100);
            }
        }

        sendEvent("EATING");
        Thread.sleep((long) (Math.random() * 2000 + 1000));

        try {
            restTemplate.postForObject(
                    supervisorUrl + "/forks/release?leftFork=" + leftFork + "&rightFork=" + rightFork + "&philosopherId=" + philosopherId,
                    null,
                    Void.class
            );
        } catch (Exception e) {
            System.err.println("Erreur lors de la libération : " + e.getMessage());
        }
    }
}
