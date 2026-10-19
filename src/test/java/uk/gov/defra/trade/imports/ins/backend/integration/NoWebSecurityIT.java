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

@TestPropertySource(properties = {
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

    @Test
    void health_shouldAnswerWithoutCredentials() throws Exception {
        mockMvc.perform(get("/health"))
            .andExpect(status().is2xxSuccessful());
    }

    @Test
    void context_shouldHaveNoSecurityFilterChain() {
        assertThat(applicationContext.getBeanNamesForType(SecurityFilterChain.class)).isEmpty();
    }

    @Test
    void context_shouldHaveNoDefaultUserDetailsService() {
        assertThat(applicationContext.getBeanNamesForType(UserDetailsService.class)).isEmpty();
    }
}
