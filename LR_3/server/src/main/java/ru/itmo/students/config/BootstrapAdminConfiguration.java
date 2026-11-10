package ru.itmo.students.config;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.itmo.students.dto.RegistrationRequest;
import ru.itmo.students.service.AuthService;

@Configuration
@EnableConfigurationProperties(BootstrapAdminProperties.class)
public class BootstrapAdminConfiguration {
    @Bean
    public ApplicationRunner bootstrapAdmin(BootstrapAdminProperties properties, AuthService auth) {
        return args -> {
            if (properties.enabled()) auth.provisionAdmin(new RegistrationRequest(
                    properties.username(), properties.email(), properties.password()));
        };
    }
}
