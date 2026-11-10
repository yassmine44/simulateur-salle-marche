package tn.esprit.simulateurbackend.config;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertySource;

@Configuration(proxyBeanMethods = false)
@Profile("banking-test")
public class BankingTestDatabaseGuard {

    private static final Pattern APPROVED_DATABASE_URL = Pattern.compile(
            "^jdbc:postgresql://localhost:5432/simulateur_scenario_test$"
    );

    private static final Set<String> ALLOWED_DATASOURCE_PROPERTIES = Set.of(
            "spring.datasource.url",
            "spring.datasource.username",
            "spring.datasource.password"
    );

    private static final Set<String> ALLOWED_HIBERNATE_SCHEMA_PROPERTIES =
            Set.of(
                    "spring.jpa.properties.hibernate.hbm2ddl.auto",
                    "spring.jpa.properties.hibernate.hbm2ddl.create_namespaces"
            );

    private static final Set<String> ALLOWED_JPA_SCHEMA_PROPERTIES = Set.of(
            "spring.jpa.properties.jakarta.persistence.schema-generation.database.action",
            "spring.jpa.properties.jakarta.persistence.schema-generation.scripts.action"
    );

    @Bean
    static BeanFactoryPostProcessor validateBankingTestDatabase(
            Environment environment
    ) {
        return beanFactory -> validate(environment);
    }

    public static void validate(Environment environment) {
        String[] activeProfiles = environment.getActiveProfiles();
        if (!Arrays.equals(activeProfiles, new String[]{"banking-test"})
                || !"banking-test".equals(
                        environment.getProperty("spring.profiles.active", ""))) {
            throw new IllegalStateException(
                    "The banking-test profile must be the only explicitly active profile."
            );
        }

        rejectAdditionalConfiguration(environment);
        validateDatasource(environment);
        validateSchemaAndMigrations(environment);
    }

    private static void validateDatasource(Environment environment) {
        String databaseUrl = environment.getProperty("spring.datasource.url", "");
        String username = environment.getProperty("spring.datasource.username", "");
        String password = environment.getProperty("spring.datasource.password", "");

        if (!APPROVED_DATABASE_URL.matcher(databaseUrl).matches()
                || databaseUrl.contains("simulateur_salle_marche")
                || !"simulateur_banking_test".equals(username)
                || password.isBlank()
                || password.contains("${")) {
            throw new IllegalStateException(
                    "The banking-test profile only permits the approved local "
                            + "simulateur_scenario_test database and dedicated user."
            );
        }
    }

    private static void validateSchemaAndMigrations(Environment environment) {
        if (!"none".equalsIgnoreCase(
                    environment.getProperty("spring.jpa.hibernate.ddl-auto", ""))
                || environment.getProperty(
                        "spring.jpa.generate-ddl", Boolean.class, true)
                || !"none".equalsIgnoreCase(environment.getProperty(
                        "spring.jpa.properties.hibernate.hbm2ddl.auto", ""))
                || environment.getProperty(
                        "spring.jpa.properties.hibernate.hbm2ddl.create_namespaces",
                        Boolean.class,
                        true)
                || !"none".equalsIgnoreCase(environment.getProperty(
                        "spring.jpa.properties.jakarta.persistence.schema-generation.database.action",
                        ""))
                || !"none".equalsIgnoreCase(environment.getProperty(
                        "spring.jpa.properties.jakarta.persistence.schema-generation.scripts.action",
                        ""))
                || !"never".equalsIgnoreCase(
                        environment.getProperty("spring.sql.init.mode", ""))
                || environment.getProperty(
                        "spring.sql.init.continue-on-error", Boolean.class, false)
                || environment.getProperty(
                        "spring.jpa.defer-datasource-initialization",
                        Boolean.class,
                        false)
                || !"false".equalsIgnoreCase(
                        environment.getProperty("spring.flyway.enabled", ""))
                || !"false".equalsIgnoreCase(
                        environment.getProperty("spring.liquibase.enabled", ""))) {
            throw new IllegalStateException(
                    "The banking-test profile requires all schema generation, "
                            + "SQL initialization, and migration mechanisms to be disabled."
            );
        }
    }

    private static void rejectAdditionalConfiguration(
            Environment environment
    ) {
        if (!(environment instanceof ConfigurableEnvironment configurable)) {
            throw new IllegalStateException(
                    "Cannot inspect all configuration sources for banking-test."
            );
        }

        for (PropertySource<?> source : configurable.getPropertySources()) {
            if (!(source instanceof EnumerablePropertySource<?> enumerable)) {
                if (!"configurationProperties".equals(source.getName())) {
                    throw new IllegalStateException(
                            "Cannot inspect configuration source for banking-test: "
                                    + source.getName()
                    );
                }
                continue;
            }

            for (String propertyName : enumerable.getPropertyNames()) {
                String key = normalizePropertyName(propertyName);

                if (isProfileOrExternalConfigOverride(key)
                        || isAlternativeDatasourceProperty(key)
                        || isUnauthorizedSchemaProperty(key)) {
                    throw new IllegalStateException(
                            "Unsupported banking-test configuration property: "
                                    + propertyName
                    );
                }
            }
        }
    }

    private static boolean isProfileOrExternalConfigOverride(String key) {
        return key.equals("spring.profiles.include")
                || key.startsWith("spring.profiles.group.")
                || key.startsWith("spring.config.")
                || key.equals("spring.main.sources");
    }

    private static boolean isAlternativeDatasourceProperty(String key) {
        return key.startsWith("spring.datasource.")
                && !ALLOWED_DATASOURCE_PROPERTIES.contains(key)
                || key.startsWith(
                        "spring.jpa.properties.hibernate.connection.")
                || key.equals("spring.jpa.jta-data-source")
                || key.equals("spring.jpa.non-jta-data-source")
                || key.startsWith(
                "spring.jpa.properties.jakarta.persistence.jdbc.")
                || key.startsWith(
                "spring.jpa.properties.javax.persistence.jdbc.")
                || key.startsWith(
                "spring.jpa.properties.jakarta.persistence.nonjtadatasource")
                || key.startsWith(
                "spring.jpa.properties.jakarta.persistence.jtadatasource")
                || key.startsWith(
                "spring.jpa.properties.javax.persistence.nonjtadatasource")
                || key.startsWith(
                "spring.jpa.properties.javax.persistence.jtadatasource")
                || key.startsWith(
                "spring.jpa.properties.jakarta.persistence.non.jta.data.source")
                || key.startsWith(
                "spring.jpa.properties.jakarta.persistence.jta.data.source")
                || key.startsWith(
                "spring.jpa.properties.javax.persistence.non.jta.data.source")
                || key.startsWith(
                "spring.jpa.properties.javax.persistence.jta.data.source")
                || key.startsWith("hibernate.connection.")
                || key.startsWith("jakarta.persistence.jdbc.")
                || key.startsWith("javax.persistence.jdbc.")
                || key.startsWith("jakarta.persistence.nonjtadatasource")
                || key.startsWith("jakarta.persistence.jtadatasource")
                || key.startsWith("javax.persistence.nonjtadatasource")
                || key.startsWith("javax.persistence.jtadatasource");
    }

    private static boolean isUnauthorizedSchemaProperty(String key) {
        return key.startsWith("spring.jpa.properties.hibernate.hbm2ddl.")
                && !ALLOWED_HIBERNATE_SCHEMA_PROPERTIES.contains(key)
                || key.startsWith("spring.jpa.properties.hibernate.schema")
                || key.startsWith(
                "spring.jpa.properties.jakarta.persistence.schema-generation.")
                && !ALLOWED_JPA_SCHEMA_PROPERTIES.contains(key)
                || key.startsWith(
                "spring.jpa.properties.javax.persistence.schema-generation.")
                || key.startsWith("hibernate.hbm2ddl.")
                || key.startsWith("hibernate.schema")
                || key.startsWith("jakarta.persistence.schema-generation.")
                || key.startsWith("javax.persistence.schema-generation.")
                || key.startsWith("spring.sql.init.")
                && !key.equals("spring.sql.init.mode")
                || key.startsWith("spring.flyway.")
                && !key.equals("spring.flyway.enabled")
                || key.startsWith("spring.liquibase.")
                && !key.equals("spring.liquibase.enabled")
                || key.startsWith("flyway.")
                || key.startsWith("liquibase.");
    }

    private static String normalizePropertyName(String propertyName) {
        if (propertyName.equals(propertyName.toLowerCase(Locale.ROOT))) {
            return propertyName;
        }

        return propertyName.toLowerCase(Locale.ROOT)
                .replace('_', '.')
                .replace(
                        "spring.jpa.properties.hibernate.hbm2ddl.create.namespaces",
                        "spring.jpa.properties.hibernate.hbm2ddl.create_namespaces"
                )
                .replace(
                        "spring.jpa.properties.jakarta.persistence.schema.generation.",
                        "spring.jpa.properties.jakarta.persistence.schema-generation."
                )
                .replace(
                        "spring.jpa.properties.javax.persistence.schema.generation.",
                        "spring.jpa.properties.javax.persistence.schema-generation."
                );
    }
}
