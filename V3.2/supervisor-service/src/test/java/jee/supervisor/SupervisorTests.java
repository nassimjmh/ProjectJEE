package jee.supervisor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.client.RestTemplate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
class Supervisor31Tests {

    private final RestTemplate restTemplate = new RestTemplate();
    private final String baseUrl = "http://localhost:8086/api/supervisor";

    @BeforeEach
    void setUp() {
        // Réinitialise toutes les fourchettes à FREE (-1) avant chaque scénario
        restTemplate.postForObject(baseUrl + "/reset", null, Void.class);
    }

    // SCÉNARIO 1 : Deux voisins demandent leurs deux fourchettes au même instant
    // P1 demande (1, 2) et P2 demande (2, 3) -> Conflit sur la fourchette 2
    @Test
    void testNeighborRaceConditionOnSharedFork() throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Integer> eatingPhilosophers = Collections.synchronizedList(new ArrayList<>());

        // Thread 1 : Philosophe 1 (fourchettes 1 et 2)
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

        // Thread 2 : Philosophe 2 (fourchettes 2 et 3)
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

        startLatch.countDown(); // Départ simultané des deux requêtes
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        System.out.println("Voisins autorisés à manger simultanément : " + eatingPhilosophers);

        // FAILLE DÉMONTRÉE : Les deux voisins ont reçu "true" en même temps !
        assertEquals(2, eatingPhilosophers.size(),
                "Sans verrou, deux voisins partagent la fourchette 2 et mangent en même temps.");
    }

    // SCÉNARIO 2 : Assaut général des 5 philosophes au même instant
    // En théorie, au maximum 2 philosophes non-voisins peuvent manger simultanément sur 5 fourchettes.
    @Test
    void testAllPhilosophersSimultaneousRequest() throws InterruptedException {
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

        System.out.println("Philosophes autorisés à manger sur les 5 : " + eatingPhilosophers);

        // FAILLE DÉMONTRÉE : Plus de 2 philosophes mangent en même temps (souvent les 5),
        // ce qui est physiquement impossible avec seulement 5 fourchettes.
        assertTrue(eatingPhilosophers.size() > 2,
                "Sans verrou, plus de 2 philosophes mangent simultanément, violant la contrainte physique des 5 fourchettes.");
    }

    // SCÉNARIO 3 : Vérification de la libération correcte des deux fourchettes
    @Test
    void testReleaseBothForksAllowsNeighborToEat() {
        // 1. P1 prend les fourchettes 1 et 2
        Boolean p1Took = restTemplate.postForObject(
                baseUrl + "/forks/take?leftFork=1&rightFork=2&philosopherId=1", null, Boolean.class);
        assertTrue(p1Took);

        // 2. P2 tente de prendre 2 et 3 juste après (sans concurrence) -> doit échouer
        Boolean p2TookBeforeRelease = restTemplate.postForObject(
                baseUrl + "/forks/take?leftFork=2&rightFork=3&philosopherId=2", null, Boolean.class);
        assertFalse(p2TookBeforeRelease, "P2 ne devrait pas pouvoir prendre la fourchette 2 tant que P1 mange.");

        // 3. P1 libère ses deux fourchettes
        restTemplate.postForObject(
                baseUrl + "/forks/release?leftFork=1&rightFork=2&philosopherId=1", null, Void.class);

        // 4. P2 retente -> doit maintenant réussir
        Boolean p2TookAfterRelease = restTemplate.postForObject(
                baseUrl + "/forks/take?leftFork=2&rightFork=3&philosopherId=2", null, Boolean.class);
        assertTrue(p2TookAfterRelease, "P2 devrait pouvoir manger après la libération par P1.");
    }
}
