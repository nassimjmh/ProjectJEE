package jee.philosophe;

import jee.philosophe.distributed.DistributedProtocolService;
import jee.philosophe.distributed.TokenRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

/** Cycle de vie du philosophe : penser -> avoir faim -> manger -> terminer. */
@Component
public class PhilosopherRunner implements CommandLineRunner {

    private final DistributedProtocolService node;
    private final int thinkMin, thinkMax, eatMin, eatMax;
    private final boolean autostart;

    public PhilosopherRunner(DistributedProtocolService node,
                             @Value("${philosopher.autostart:true}") boolean autostart,
                             @Value("${philosopher.think-min-ms:500}") int thinkMin,
                             @Value("${philosopher.think-max-ms:2000}") int thinkMax,
                             @Value("${philosopher.eat-min-ms:500}") int eatMin,
                             @Value("${philosopher.eat-max-ms:1500}") int eatMax) {
        this.node = node;
        this.autostart = autostart;
        this.thinkMin = thinkMin;
        this.thinkMax = thinkMax;
        this.eatMin = eatMin;
        this.eatMax = eatMax;
    }

    @Override
    public void run(String... args) {
        if (!autostart) return;
        Thread t = new Thread(() -> {
            try {
                while (true) {
                    node.event("THINKING", "");
                    Thread.sleep(rand(thinkMin, thinkMax));

                    TokenRequest req = node.acquire();          // bloque jusqu'à posséder les 2 jetons
                    node.event("EATING", "forks=" + node.forksToString(req));
                    Thread.sleep(rand(eatMin, eatMax));
                    node.release(req);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "philosopher-loop");
        t.setDaemon(true);
        t.start();
    }

    private static long rand(int min, int max) {
        return ThreadLocalRandom.current().nextLong(min, Math.max(min, max) + 1);
    }
}
