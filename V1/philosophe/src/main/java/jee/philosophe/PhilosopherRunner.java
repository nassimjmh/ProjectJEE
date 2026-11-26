package jee.philosophe;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class PhilosopherRunner implements CommandLineRunner {

    private final int philosopherId;
    private final String monitorUrl;
    private final boolean autostart;
    private final RestTemplate restTemplate;

    private int logicalClock = 0;

    public PhilosopherRunner(
            @Value("${philosopher.id:0}") int configuredId,
            @Value("${server.port:8081}") int serverPort,
            @Value("${philosopher.base-port:8080}") int basePort,
            @Value("${philosopher.autostart:true}") boolean autostart,
            @Value("${monitor.url}") String monitorUrl,
            RestTemplate restTemplate) {
        this.philosopherId = idFromPort(configuredId, serverPort, basePort);
        this.autostart = autostart;
        this.monitorUrl = monitorUrl;
        this.restTemplate = restTemplate;
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
                    sendEvent("EATING");
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

    private void eat() throws InterruptedException {
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
}
