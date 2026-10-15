package tn.esprit.simulateurbackend.config;

import jakarta.persistence.EntityManagerFactory;

import org.springframework.context.annotation.Configuration;
import org.springframework.orm.jpa.support.OpenEntityManagerInViewInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/*
 * « Open EntityManager in View » pour toutes les requêtes,
 * sauf les flux temps réel (SSE).
 *
 * Ce mécanisme garde la connexion à la base pendant toute la
 * durée d'une requête. Un flux SSE reste ouvert plusieurs minutes :
 * chaque onglet Trading immobiliserait une connexion, et quelques
 * onglets suffisent à épuiser le pool (10 connexions), ce qui bloque
 * toute l'application. Le comportement par défaut de Spring Boot
 * (spring.jpa.open-in-view) est donc désactivé et remplacé par cet
 * intercepteur, qui exclut les flux.
 */
@Configuration
public class OpenEntityManagerInViewConfig
        implements WebMvcConfigurer {

    private final EntityManagerFactory entityManagerFactory;

    public OpenEntityManagerInViewConfig(
            EntityManagerFactory entityManagerFactory
    ) {
        this.entityManagerFactory = entityManagerFactory;
    }

    @Override
    public void addInterceptors(
            InterceptorRegistry registry
    ) {

        OpenEntityManagerInViewInterceptor interceptor =
                new OpenEntityManagerInViewInterceptor();

        interceptor.setEntityManagerFactory(entityManagerFactory);

        registry
                .addWebRequestInterceptor(interceptor)
                .excludePathPatterns("/api/order-book/*/stream");
    }
}
