package uk.gov.defra.trade.imports.ins.backend.addresslookup;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.security.oauth2.client.OAuth2ClientAutoConfiguration;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.web.client.RestClient;
import software.amazon.awssdk.services.sts.StsClient;

/**
 * Every address-lookup bean loads only under the {@code dev} or {@code local}
 * profile, and none of them exist otherwise. The profile is derived from {@code ENVIRONMENT} in
 * {@code application.yml}, so the CDP environment names are the profile names.
 *
 * <p>The properties are supplied here rather than read from the profile's YAML — an
 * {@link ApplicationContextRunner} never loads {@code application.yml} at all.
 */
class AddressLookupConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withBean(ObjectMapper.class, ObjectMapper::new)
        .withBean(RestClient.Builder.class, RestClient::builder)
        .withConfiguration(AutoConfigurations.of(OAuth2ClientAutoConfiguration.class))
        .withUserConfiguration(AddressLookupConfig.class, AddressLookupController.class, FederatedTokenConfig.class)
        .withPropertyValues(
            "aws.region=eu-west-2",
            // The registration keys the dev and local YAML carry — the token chain has no
            // ClientRegistrationRepository without them.
            "spring.security.oauth2.client.registration.address-lookup.client-id="
                + "22222222-2222-2222-2222-222222222222",
            "spring.security.oauth2.client.registration.address-lookup.client-authentication-method=none",
            "spring.security.oauth2.client.registration.address-lookup.authorization-grant-type=client_credentials",
            "spring.security.oauth2.client.registration.address-lookup.scope="
                + "33333333-3333-3333-3333-333333333333/.default",
            "spring.security.oauth2.client.provider.address-lookup.token-uri=http://localhost:8087/token",
            "address-lookup.api-url=http://localhost:8087/addresses",
            "address-lookup.tenant-id=11111111-1111-1111-1111-111111111111",
            "address-lookup.oauth-token-url=http://localhost:8087/token",
            "address-lookup.client-id=22222222-2222-2222-2222-222222222222",
            "address-lookup.client-scope=33333333-3333-3333-3333-333333333333/.default",
            "address-lookup.audience=api://AzureADTokenExchange",
            "address-lookup.signing-algorithm=RS256",
            "address-lookup.assertion-duration-seconds=900",
            "address-lookup.max-results=100",
            "address-lookup.default-postcode=SW1A 1AA");

    @Test
    void beans_shouldLoad_whenProfileIsDev() {
        // Given
        // When
        contextRunner.withPropertyValues("spring.profiles.active=dev")
            .run(context -> {
                // Then
                assertThat(context).hasSingleBean(AddressLookupController.class);
                assertThat(context).hasSingleBean(AddressLookupClient.class);
                assertThat(context).hasSingleBean(OAuth2AuthorizedClientManager.class);
                assertThat(context).hasSingleBean(StsClient.class);
            });
    }

    @Test
    void beans_shouldLoad_whenProfileIsLocal() {
        // Given
        // When
        contextRunner.withPropertyValues("spring.profiles.active=local")
            .run(context -> {
                // Then
                assertThat(context).hasSingleBean(AddressLookupController.class);
                assertThat(context).hasSingleBean(AddressLookupClient.class);
                assertThat(context).hasSingleBean(OAuth2AuthorizedClientManager.class);
                assertThat(context).hasSingleBean(StsClient.class);
            });
    }

    @Test
    void beans_shouldBeAbsent_whenProfileIsTest() {
        // Given
        // When
        contextRunner.withPropertyValues("spring.profiles.active=test")
            .run(context -> {
                // Then
                assertAbsent(context);
            });
    }

    @Test
    void beans_shouldBeAbsent_whenProfileIsPerfTest() {
        // Given
        // When
        contextRunner.withPropertyValues("spring.profiles.active=perf-test")
            .run(context -> {
                // Then
                assertAbsent(context);
            });
    }

    @Test
    void beans_shouldBeAbsent_whenProfileIsProd() {
        // Given
        // When
        contextRunner.withPropertyValues("spring.profiles.active=prod")
            .run(context -> {
                // Then
                assertAbsent(context);
            });
    }

    @Test
    void beans_shouldBeAbsent_whenNoProfileIsActive() {
        // Given
        // When
        contextRunner.run(context -> {
            // Then
            assertAbsent(context);
        });
    }

    private static void assertAbsent(AssertableApplicationContext context) {
        assertThat(context).doesNotHaveBean(AddressLookupController.class);
        assertThat(context).doesNotHaveBean(AddressLookupClient.class);
        assertThat(context).doesNotHaveBean(OAuth2AuthorizedClientManager.class);
        assertThat(context).doesNotHaveBean(StsClient.class);
    }
}
