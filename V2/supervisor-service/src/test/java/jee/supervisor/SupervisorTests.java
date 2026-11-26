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
    void testRaceConditionOnSingleFork() throws InterruptedException {
        SupervisorController supervisor = new SupervisorController(N);
        ExecutorService executor = Executors.newFixedThreadPool(N);
        CountDownLatch start = new CountDownLatch(1);
        List<Integer> winners = Collections.synchronizedList(new ArrayList<>());

        for (int i = 1; i <= N; i++) {
            final int philosopherId = i;
            executor.submit(() -> {
                start.await();
                if (supervisor.takeFork(1, philosopherId)) {
                    winners.add(philosopherId);
                }
                return null;
            });
        }

        start.countDown();
        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

        System.out.println("Philosophes ayant obtenu la fourchette 1 : " + winners);
        assertTrue(winners.size() > 1,
                "Faille attendue : plus d'un philosophe obtient la même fourchette.");
    }

    @Test
    void testDeadlockWhenEveryoneTakesLeftFirst() {
        SupervisorController supervisor = new SupervisorController(N);

        for (int philosopherId = 1; philosopherId <= N; philosopherId++) {
            assertTrue(supervisor.takeFork(philosopherId, philosopherId),
                    "Chaque philosophe doit pouvoir prendre sa fourchette gauche.");
        }

        for (int philosopherId = 1; philosopherId <= N; philosopherId++) {
            int rightFork = (philosopherId % N) + 1;
            assertFalse(supervisor.isAvailable(rightFork),
                    "La fourchette droite du philosophe " + philosopherId + " est déjà tenue par son voisin.");
            assertFalse(supervisor.takeFork(rightFork, philosopherId),
                    "Personne ne peut obtenir sa seconde fourchette : interblocage.");
        }
    }

    @Test
    void testReleaseMakesForkAvailableAgain() {
        SupervisorController supervisor = new SupervisorController(N);

        assertTrue(supervisor.takeFork(1, 1));
        assertFalse(supervisor.isAvailable(1));

        supervisor.releaseFork(1, 1);
        assertTrue(supervisor.isAvailable(1));
    }

    @Test
    void testOnlyOwnerCanReleaseFork() {
        SupervisorController supervisor = new SupervisorController(N);

        assertTrue(supervisor.takeFork(1, 1));
        supervisor.releaseFork(1, 2);

        assertFalse(supervisor.isAvailable(1));
    }

    @Test
    void testForkCountIsConfigurable() {
        SupervisorController supervisor = new SupervisorController(8);

        assertEquals(8, supervisor.getState().size());
        assertTrue(supervisor.isAvailable(8));
        assertFalse(supervisor.isAvailable(9));
    }
}
