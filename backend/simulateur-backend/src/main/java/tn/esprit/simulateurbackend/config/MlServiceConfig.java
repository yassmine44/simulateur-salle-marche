package tn.esprit.simulateurbackend.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
@EnableConfigurationProperties(MlServiceProperties.class)
public class MlServiceConfig {

    @Bean
    RestTemplate mlRestTemplate(
            MlServiceProperties properties
    ) {

        SimpleClientHttpRequestFactory factory =
                new SimpleClientHttpRequestFactory();

        factory.setConnectTimeout(
                properties.getConnectTimeoutMs()
        );

        factory.setReadTimeout(
                properties.getReadTimeoutMs()
        );

        return new RestTemplate(factory);
    }
}
