package jee.supervisor;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class SupervisorTests {

    private static final int N = 5;

    @Test
    void testNeighborRaceConditionOnSharedFork() throws InterruptedException {
        SupervisorController supervisor = new SupervisorController(N);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Integer> eating = Collections.synchronizedList(new ArrayList<>());

        executor.submit(() -> {
            start.await();
            if (supervisor.takeBothForks(1, 2, 1)) {
                eating.add(1);
            }
            return null;
        });
        executor.submit(() -> {
            start.await();
            if (supervisor.takeBothForks(2, 3, 2)) {
                eating.add(2);
            }
            return null;
        });

        start.countDown();
        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

        System.out.println("Voisins autorisés à manger simultanément : " + eating);
        assertEquals(2, eating.size(),
                "Faille attendue : sans verrou, deux voisins partagent la fourchette 2 et mangent en même temps.");
    }

    @Test
    void testAllPhilosophersSimultaneousRequest() throws InterruptedException {
        SupervisorController supervisor = new SupervisorController(N);
        ExecutorService executor = Executors.newFixedThreadPool(N);
        CountDownLatch start = new CountDownLatch(1);
        List<Integer> eating = Collections.synchronizedList(new ArrayList<>());

        for (int i = 1; i <= N; i++) {
            final int philosopherId = i;
            final int leftFork = i;
            final int rightFork = (i % N) + 1;
            executor.submit(() -> {
                start.await();
                if (supervisor.takeBothForks(leftFork, rightFork, philosopherId)) {
                    eating.add(philosopherId);
                }
                return null;
            });
        }

        start.countDown();
        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

        System.out.println("Philosophes autorisés à manger sur les " + N + " : " + eating);
        assertTrue(eating.size() > 2,
                "Faille attendue : plus de 2 philosophes mangent simultanément, ce qui est impossible avec 5 fourchettes.");
    }

    @Test
    void testReleaseBothForksAllowsNeighborToEat() {
        SupervisorController supervisor = new SupervisorController(N);

        assertTrue(supervisor.takeBothForks(1, 2, 1),
                "P1 doit pouvoir prendre les fourchettes 1 et 2.");
        assertFalse(supervisor.takeBothForks(2, 3, 2),
                "P2 ne doit pas pouvoir prendre la fourchette 2 tant que P1 mange.");

        supervisor.releaseBothForks(1, 2, 1);

        assertTrue(supervisor.takeBothForks(2, 3, 2),
                "P2 doit pouvoir manger après la libération par P1.");
    }

    @Test
    void testForkCountIsConfigurable() {
        SupervisorController supervisor = new SupervisorController(8);

        assertEquals(8, supervisor.getState().size(),
                "Le superviseur doit gérer 8 fourchettes.");
    }
}
