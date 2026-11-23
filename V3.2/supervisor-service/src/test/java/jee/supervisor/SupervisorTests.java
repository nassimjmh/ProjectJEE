package jee.supervisor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
class SupervisorTests {

    private final RestTemplate restTemplate = new RestTemplate();
    private final String baseUrl = "http://localhost:8086/api/supervisor";

    @BeforeEach
    void setUp() {
        // Réinitialise l'état des fourchettes avant chaque test
        restTemplate.postForObject(baseUrl + "/reset", null, Void.class);
    }

    // SCÉNARIO 1 : Deux voisins demandent leurs fourchettes simultanément
    // Un seul des deux doit réussir à obtenir la fourchette partagée (fourchette 2)
    @Test
    void testMutexBetweenNeighborsWithLock() throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Integer> eatingPhilosophers = Collections.synchronizedList(new ArrayList<>());

        // Philosophe 1 (fourchettes 1 et 2)
        executor.submit(() -> {
            try {
                startLatch.await();
                Boolean success = restTemplate.postForObject(
                        baseUrl + "/forks/take?leftFork=1&rightFork=2&philosopherId=1",
                        null, Boolean.class);
                if (Boolean.TRUE.equals(success)) {
                    eatingPhilosophers.add(1);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        // Philosophe 2 (fourchettes 2 et 3)
        executor.submit(() -> {
            try {
                startLatch.await();
                Boolean success = restTemplate.postForObject(
                        baseUrl + "/forks/take?leftFork=2&rightFork=3&philosopherId=2",
                        null, Boolean.class);
                if (Boolean.TRUE.equals(success)) {
                    eatingPhilosophers.add(2);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        startLatch.countDown(); // Lancement simultané
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        System.out.println("Voisin autorisé à manger (avec verrou) : " + eatingPhilosophers);

        // VALIDATION : Un seul philosophe a pu prendre les fourchettes
        assertEquals(1, eatingPhilosophers.size(),
                "Avec verrou, un seul des deux voisins doit obtenir la fourchette partagée.");
    }

    // SCÉNARIO 2 : Les 5 philosophes demandent à manger au même instant
    // Avec 5 fourchettes, au maximum 2 philosophes non-voisins peuvent manger simultanément
    @Test
    void testMaxTwoPhilosophersEatingSimultaneouslyWithLock() throws InterruptedException {
        int n = 5;
        ExecutorService executor = Executors.newFixedThreadPool(n);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Integer> eatingPhilosophers = Collections.synchronizedList(new ArrayList<>());

        for (int i = 1; i <= n; i++) {
            final int philoId = i;
            final int leftFork = i;
            final int rightFork = (i % n) + 1;

            executor.submit(() -> {
                try {
                    startLatch.await();
                    Boolean success = restTemplate.postForObject(
                            baseUrl + "/forks/take?leftFork=" + leftFork + "&rightFork=" + rightFork + "&philosopherId=" + philoId,
                            null, Boolean.class);
                    if (Boolean.TRUE.equals(success)) {
                        eatingPhilosophers.add(philoId);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }

        startLatch.countDown();
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        System.out.println("Philosophes autorisés à manger sur les 5 (avec verrou) : " + eatingPhilosophers);

        // VALIDATION : Au moins 1 et au maximum 2 philosophes mangent en même temps
        assertTrue(eatingPhilosophers.size() >= 1 && eatingPhilosophers.size() <= 2,
                "Avec verrou, au maximum 2 philosophes peuvent manger simultanément avec 5 fourchettes.");
    }

    // SCÉNARIO 3 : Vérification de la cohérence globale de l'état mémoire et de l'absence d'interblocage
    @Test
    void testCompleteCycleWithoutDeadlock() throws InterruptedException {
        int n = 5;
        ExecutorService executor = Executors.newFixedThreadPool(n);
        CountDownLatch doneLatch = new CountDownLatch(n);

        // Chaque philosophe doit réussir à manger une fois malgré la concurrence
        for (int i = 1; i <= n; i++) {
            final int philoId = i;
            final int leftFork = i;
            final int rightFork = (i % n) + 1;

            executor.submit(() -> {
                boolean ate = false;
                while (!ate) {
                    Boolean took = restTemplate.postForObject(
                            baseUrl + "/forks/take?leftFork=" + leftFork + "&rightFork=" + rightFork + "&philosopherId=" + philoId,
                            null, Boolean.class);
                    if (Boolean.TRUE.equals(took)) {
                        try {
                            Thread.sleep(50); // Temps de repas simulé
                        } catch (InterruptedException ignored) {}
                        restTemplate.postForObject(
                                baseUrl + "/forks/release?leftFork=" + leftFork + "&rightFork=" + rightFork + "&philosopherId=" + philoId,
                                null, Void.class);
                        ate = true;
                    } else {
                        try {
                            Thread.sleep(20);
                        } catch (InterruptedException ignored) {}
                    }
                }
                doneLatch.countDown();
            });
        }

        // Tous les philosophes doivent avoir terminé dans un délai raisonnable (pas d'interblocage)
        boolean allFinished = doneLatch.await(10, TimeUnit.SECONDS);
        assertTrue(allFinished, "Tous les philosophes doivent pouvoir manger sans interblocage.");

        // Vérification finale : toutes les fourchettes doivent être libres (-1) à la fin
        Map<String, Integer> finalState = restTemplate.getForObject(baseUrl + "/state", Map.class);
        assertNotNull(finalState);
        finalState.values().forEach(owner -> assertEquals(-1, owner, "Toutes les fourchettes doivent être libérées."));
    }
}