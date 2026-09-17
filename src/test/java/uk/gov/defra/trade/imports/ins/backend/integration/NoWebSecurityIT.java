package uk.gov.defra.trade.imports.ins.backend.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.TestPropertySource;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Guards the spring.autoconfigure.exclude list in {@code application.yml}. The address lookup
 * spike puts {@code spring-security-config} on the classpath for its outbound OAuth2 client;
 * without every one of those exclusions Boot builds a {@link SecurityFilterChain} of its own and
 * existing endpoints start demanding credentials. Drop any one of them and this fails.
 */
@TestPropertySource(properties = {
    // A registration, as dev and local carry one. Boot's OAuth2 client auto-configuration then
    // builds a ClientRegistrationRepository and an authorized-client service, which is what
    // OAuth2ClientWebSecurityAutoConfiguration waits for before adding a chain of its own — so
    // without these the exclusion list would look shorter than it needs to be.
    "spring.security.oauth2.client.registration.address-lookup.client-id=test-client",
    "spring.security.oauth2.client.registration.address-lookup.client-authentication-method=none",
    "spring.security.oauth2.client.registration.address-lookup.authorization-grant-type=client_credentials",
    "spring.security.oauth2.client.registration.address-lookup.scope=test-scope/.default",
    "spring.security.oauth2.client.provider.address-lookup.token-uri=http://localhost:8087/token"})
class NoWebSecurityIT extends IntegrationBase {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void notifications_shouldAnswerWithoutCredentials() throws Exception {
        mockMvc.perform(get("/notifications"))
            .andExpect(status().is2xxSuccessful());
    }

    /** The actuator side. {@code health} is the only endpoint this profile exposes. */
    @Test
    void health_shouldAnswerWithoutCredentials() throws Exception {
        mockMvc.perform(get("/health"))
            .andExpect(status().is2xxSuccessful());
    }

    @Test
    void context_shouldHaveNoSecurityFilterChain() {
        assertThat(applicationContext.getBeanNamesForType(SecurityFilterChain.class)).isEmpty();
    }

    /** The generated-password user Boot creates when its default security is left switched on. */
    @Test
    void context_shouldHaveNoDefaultUserDetailsService() {
        assertThat(applicationContext.getBeanNamesForType(UserDetailsService.class)).isEmpty();
    }
}
