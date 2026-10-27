package network.foodsharing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class FoodsharingApplication {

    public static void main(String[] args) {
        SpringApplication.run(FoodsharingApplication.class, args);
    }
}
