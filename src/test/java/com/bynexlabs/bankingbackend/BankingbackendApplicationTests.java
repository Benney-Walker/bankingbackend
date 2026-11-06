package com.bynexlabs.bankingbackend;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;

// Starts the whole application, so it needs a real database, RabbitMQ and the R2/Textract/Vynfy
// settings. Tests don't go through main(), so the .env file is not loaded here; the test only
// runs when the variables are exported in the shell (POSTGRES_URL is used as the signal).
@EnabledIfEnvironmentVariable(named = "POSTGRES_URL", matches = ".+")
@SpringBootTest
class BankingbackendApplicationTests {

    @Test
    void contextLoads() {
    }

}
