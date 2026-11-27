package jee.philosophe;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "philosopher.autostart=false")
class PhilosopheApplicationTests {

    @Test
    void contextLoads() {
    }

}
