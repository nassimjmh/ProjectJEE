package jee.supervisor;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.client.RestTemplate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
class SupervisorTests {

    private final RestTemplate restTemplate = new RestTemplate();
    private final String baseUrl = "http://localhost:8086/api/supervisor";

    // TEST 1 : Condition de course (Double attribution d'une même fourchette)
    @Test
    void testRaceConditionOnSingleFork() throws InterruptedException {
        int numberOfPhilosophers = 5;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfPhilosophers);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Integer> winners = Collections.synchronizedList(new ArrayList<>());

        // 5 philosophes demandent la fourchette n°1 exactement au même instant
        for (int i = 1; i <= numberOfPhilosophers; i++) {
            final int philoId = i;
            executor.submit(() -> {
                try {
                    startLatch.await(); // Synchronise le départ
                    Boolean available = restTemplate.getForObject(baseUrl + "/fork/1/available", Boolean.class);
                    if (Boolean.TRUE.equals(available)) {
                        Boolean taken = restTemplate.postForObject(
                                baseUrl + "/fork/1/take?philosopherId=" + philoId, null, Boolean.class);
                        if (Boolean.TRUE.equals(taken)) {
                            winners.add(philoId);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }

        startLatch.countDown(); // Lancement simultané
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        System.out.println("Philosophes ayant obtenu la fourchette 1 : " + winners);
        // FAILLE DÉMONTRÉE : Plus d'un philosophe a reçu "true" !
        assertTrue(winners.size() > 1, "La condition de course aurait dû attribuer la fourchette à plusieurs philosophes.");
    }

    // TEST 2 : Interblocage (Deadlock)
    @Test
    void testDeadlockScenario() throws InterruptedException {
        int n = 5;
        ExecutorService executor = Executors.newFixedThreadPool(n);
        CountDownLatch allTookFirstFork = new CountDownLatch(n);

        for (int i = 1; i <= n; i++) {
            final int philoId = i;
            final int leftFork = i;
            final int rightFork = (i % n) + 1;

            executor.submit(() -> {
                // 1. Chaque philosophe prend sa fourchette gauche
                restTemplate.postForObject(baseUrl + "/fork/" + leftFork + "/take?philosopherId=" + philoId, null, Boolean.class);
                allTookFirstFork.countDown();

                try {
                    // On attend que tout le monde ait sa première fourchette
                    allTookFirstFork.await();
                    // 2. Chacun tente de prendre sa fourchette droite
                    Boolean gotSecond = restTemplate.postForObject(
                            baseUrl + "/fork/" + rightFork + "/take?philosopherId=" + philoId, null, Boolean.class);
                    assertFalse(gotSecond, "Personne ne devrait pouvoir prendre sa deuxième fourchette");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        // FAILLE DÉMONTRÉE : Aucun philosophe ne peut manger, le système est bloqué.
    }
}
