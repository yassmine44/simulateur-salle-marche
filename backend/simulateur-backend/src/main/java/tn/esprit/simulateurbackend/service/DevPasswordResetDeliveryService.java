package tn.esprit.simulateurbackend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("dev")
public class DevPasswordResetDeliveryService {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(
                    DevPasswordResetDeliveryService.class
            );

    public void deliver(
            String email,
            String rawToken
    ) {

        String resetUrl =
                "http://localhost:4200/reset-password?token="
                        + rawToken;

        LOGGER.warn(
                "DEV ONLY - Password reset for {}: {}",
                email,
                resetUrl
        );
    }
}