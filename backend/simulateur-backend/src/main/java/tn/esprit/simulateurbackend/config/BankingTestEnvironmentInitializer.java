package tn.esprit.simulateurbackend.config;

import java.util.Arrays;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;

public class BankingTestEnvironmentInitializer
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        ConfigurableEnvironment environment =
                context.getEnvironment();
        if (Arrays.asList(environment.getActiveProfiles())
                .contains("banking-test")) {
            BankingTestDatabaseGuard.validate(environment);
        }
    }
}
