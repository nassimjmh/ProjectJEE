package jee.philosophe;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@SpringBootApplication
public class PhilosopheApplication {

    public static void main(String[] args) {
        SpringApplication.run(PhilosopheApplication.class, args);
    }

    @Bean
    public RestTemplate restTemplate() {
        // Délais courts : un pair injoignable ne doit pas bloquer indéfiniment (retransmission ensuite).
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(1000);
        factory.setReadTimeout(3000);
        return new RestTemplate(factory);
    }
}
