package tn.esprit.simulateurbackend.config;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BankingTestDatabaseGuardTest {

    @Test
    void acceptsOnlyTheApprovedBankingTestConfiguration() {
        assertDoesNotThrow(() -> BankingTestDatabaseGuard.validate(approvedEnvironment()));
    }

    @Test
    void rejectsDevProfile() {
        MockEnvironment environment = approvedEnvironment();
        environment.setActiveProfiles("dev");
        environment.setProperty("spring.profiles.active", "dev");

        assertRejected(environment);
    }

    @Test
    void rejectsMultipleActiveProfiles() {
        MockEnvironment environment = approvedEnvironment();
        environment.setActiveProfiles("banking-test", "dev");
        environment.setProperty("spring.profiles.active", "banking-test,dev");

        assertRejected(environment);
    }

    @Test
    void rejectsProfileIncludes() {
        MockEnvironment environment = approvedEnvironment();
        environment.setProperty("spring.profiles.include", "dev");

        assertRejected(environment);
    }

    @Test
    void rejectsExternalConfigurationLocationsAndImports() {
        for (String propertyName : new String[]{
                "spring.config.import",
                "spring.config.location",
                "spring.config.additional-location"
        }) {
            MockEnvironment environment = approvedEnvironment();
            environment.setProperty(propertyName, "file:./external.properties");

            assertRejected(environment);
        }
    }

    @Test
    void rejectsAdditionalSpringApplicationSources() {
        MockEnvironment environment = approvedEnvironment();
        environment.setProperty(
                "spring.main.sources",
                "tn.esprit.simulateurbackend.ExternalDataSourceConfiguration"
        );

        assertRejected(environment);
    }

    @Test
    void rejectsThePrimaryDatabase() {
        MockEnvironment environment = approvedEnvironment();
        environment.setProperty(
                "spring.datasource.url",
                "jdbc:postgresql://localhost:5432/simulateur_salle_marche"
        );

        assertRejected(environment);
    }

    @Test
    void rejectsPrimaryDatabaseFromEnvironmentVariableOverride() {
        MockEnvironment environment = approvedEnvironment();
        environment.getPropertySources().addFirst(
                new SystemEnvironmentPropertySource(
                        "testSystemEnvironment",
                        Map.of(
                                "SPRING_DATASOURCE_URL",
                                "jdbc:postgresql://localhost:5432/simulateur_salle_marche"
                        )
                )
        );

        assertRejected(environment);
    }

    @Test
    void rejectsAnIncorrectDatabaseName() {
        MockEnvironment environment = approvedEnvironment();
        environment.setProperty(
                "spring.datasource.url",
                "jdbc:postgresql://localhost:5432/another_database"
        );

        assertRejected(environment);
    }

    @Test
    void rejectsAnUnapprovedDatabaseHost() {
        MockEnvironment environment = approvedEnvironment();
        environment.setProperty(
                "spring.datasource.url",
                "jdbc:postgresql://db-test.example:5432/simulateur_scenario_test"
        );

        assertRejected(environment);
    }

    @Test
    void rejectsAnUnapprovedDatabasePort() {
        MockEnvironment environment = approvedEnvironment();
        environment.setProperty(
                "spring.datasource.url",
                "jdbc:postgresql://localhost:5433/simulateur_scenario_test"
        );

        assertRejected(environment);
    }

    @Test
    void rejectsAnUnapprovedDatabaseUser() {
        MockEnvironment environment = approvedEnvironment();
        environment.setProperty("spring.datasource.username", "postgres");

        assertRejected(environment);
    }

    @Test
    void rejectsAMissingDatabasePassword() {
        assertRejected(approvedEnvironment(false));
    }

    @Test
    void rejectsHibernateSchemaGeneration() {
        MockEnvironment environment = approvedEnvironment();
        environment.setProperty("spring.jpa.hibernate.ddl-auto", "update");

        assertRejected(environment);
    }

    @Test
    void rejectsJpaGenerateDdl() {
        MockEnvironment environment = approvedEnvironment();
        environment.setProperty("spring.jpa.generate-ddl", "true");

        assertRejected(environment);
    }

    @Test
    void rejectsNativeSchemaGenerationProperties() {
        MockEnvironment hibernateEnvironment = approvedEnvironment();
        hibernateEnvironment.setProperty(
                "spring.jpa.properties.hibernate.hbm2ddl.auto",
                "create"
        );
        assertRejected(hibernateEnvironment);

        MockEnvironment persistenceEnvironment = approvedEnvironment();
        persistenceEnvironment.setProperty(
                "spring.jpa.properties.jakarta.persistence.schema-generation.database.action",
                "create"
        );
        assertRejected(persistenceEnvironment);
    }

    @Test
    void rejectsHibernateDdlFromEnvironmentVariableOverride() {
        MockEnvironment environment = approvedEnvironment();
        environment.getPropertySources().addFirst(
                new SystemEnvironmentPropertySource(
                        "testSystemEnvironment",
                        Map.of(
                                "SPRING_JPA_PROPERTIES_HIBERNATE_HBM2DDL_AUTO",
                                "create"
                        )
                )
        );

        assertRejected(environment);
    }

    @Test
    void rejectsAnAlternativeDatasourceConfiguration() {
        MockEnvironment environment = approvedEnvironment();
        environment.setProperty(
                "spring.datasource.hikari.jdbc-url",
                "jdbc:postgresql://other-host:5432/other_database"
        );

        assertRejected(environment);
    }

    @Test
    void rejectsFlywayWhenEnabled() {
        MockEnvironment environment = approvedEnvironment();
        environment.setProperty("spring.flyway.enabled", "true");

        assertRejected(environment);
    }

    @Test
    void rejectsLiquibaseWhenEnabled() {
        MockEnvironment environment = approvedEnvironment();
        environment.setProperty("spring.liquibase.enabled", "true");

        assertRejected(environment);
    }

    @Test
    void rejectsSqlInitializationWhenEnabled() {
        MockEnvironment environment = approvedEnvironment();
        environment.setProperty("spring.sql.init.mode", "always");

        assertRejected(environment);
    }

    @Test
    void schedulersAreExcludedFromBankingTestByProfile() {
        assertArrayEquals(
                new String[]{"!banking-test"},
                MarketMakerScheduler.class.getAnnotation(Profile.class).value()
        );
        assertArrayEquals(
                new String[]{"!banking-test"},
                OrderExpiryScheduler.class.getAnnotation(Profile.class).value()
        );
    }

    @Test
    void initializerIsRegisteredThroughSpringFactories() {
        var initializerNames = SpringFactoriesLoader.loadFactoryNames(
                ApplicationContextInitializer.class,
                getClass().getClassLoader()
        );

        assertTrue(initializerNames.contains(
                BankingTestEnvironmentInitializer.class.getName()
        ));
    }

    private static MockEnvironment approvedEnvironment() {
        return approvedEnvironment(true);
    }

    private static MockEnvironment approvedEnvironment(boolean includePassword) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("banking-test");
        environment.setProperty("spring.profiles.active", "banking-test");
        environment.setProperty(
                "spring.datasource.url",
                "jdbc:postgresql://localhost:5432/simulateur_scenario_test"
        );
        environment.setProperty("spring.datasource.username", "simulateur_banking_test");
        if (includePassword) {
            environment.setProperty(
                    "spring.datasource.password",
                    "unit-test-only-placeholder"
            );
        }
        environment.setProperty("spring.jpa.hibernate.ddl-auto", "none");
        environment.setProperty("spring.jpa.generate-ddl", "false");
        environment.setProperty(
                "spring.jpa.properties.hibernate.hbm2ddl.auto",
                "none"
        );
        environment.setProperty(
                "spring.jpa.properties.hibernate.hbm2ddl.create_namespaces",
                "false"
        );
        environment.setProperty(
                "spring.jpa.properties.jakarta.persistence.schema-generation.database.action",
                "none"
        );
        environment.setProperty(
                "spring.jpa.properties.jakarta.persistence.schema-generation.scripts.action",
                "none"
        );
        environment.setProperty("spring.jpa.defer-datasource-initialization", "false");
        environment.setProperty("spring.sql.init.mode", "never");
        environment.setProperty("spring.flyway.enabled", "false");
        environment.setProperty("spring.liquibase.enabled", "false");
        return environment;
    }

    private static void assertRejected(MockEnvironment environment) {
        assertThrows(
                IllegalStateException.class,
                () -> BankingTestDatabaseGuard.validate(environment)
        );
    }
}
