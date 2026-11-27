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
    private final boolean autostart;
    private final RestTemplate restTemplate;
    private int logicalClock = 0;
    private final int leftFork;
    private final int rightFork;

    public PhilosopherRunner(
            @Value("${philosopher.id:0}") int configuredId,
            @Value("${server.port:8081}") int serverPort,
            @Value("${philosopher.base-port:8080}") int basePort,
            @Value("${philosopher.count:5}") int philosopherCount,
            @Value("${philosopher.autostart:true}") boolean autostart,
            @Value("${monitor.url}") String monitorUrl,
            @Value("${supervisor.url}") String supervisorUrl,
            RestTemplate restTemplate) {
        this.philosopherId = idFromPort(configuredId, serverPort, basePort);
        this.autostart = autostart;
        this.monitorUrl = monitorUrl;
        this.restTemplate = restTemplate;
        this.supervisorUrl = supervisorUrl;
        this.leftFork = philosopherId;
        this.rightFork = (philosopherId % philosopherCount) + 1;
    }

    private static int idFromPort(int configuredId, int serverPort, int basePort) {
        return configuredId > 0 ? configuredId : serverPort - basePort;
    }

    @Override
    public void run(String... args) {
        if (!autostart) return;
        new Thread(() -> {
            try {
                while (true) {
                    think();

                    sendEvent("HUNGRY");

                    eat();

                    sendEvent("DONE");


                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    private void think() throws InterruptedException {
        Thread.sleep((long) (Math.random() * 2000 + 1000));
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
