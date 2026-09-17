package uk.gov.defra.trade.imports.ins.backend.addresslookup;

import java.net.URI;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2ClientCredentialsGrantRequest;
import org.springframework.security.oauth2.client.endpoint.RestClientClientCredentialsTokenResponseClient;
import org.springframework.security.oauth2.client.http.OAuth2ErrorResponseErrorHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sts.StsClient;

/**
 * Spring Security OAuth2 client for the federated-credential token chain (plan Step 2): AWS STS
 * {@code GetWebIdentityToken} → Entra {@code client_credentials} with a {@code client_assertion}
 * → cached Entra access token. No client secret is stored anywhere.
 *
 * <p>Matches the servlet OAuth2 client reference: a {@link RestClientClientCredentialsTokenResponseClient}
 * with a parameters converter for the assertion, then an
 * {@link AuthorizedClientServiceOAuth2AuthorizedClientManager} so the token is scoped to this
 * application (there is no servlet {@code SecurityFilterChain} — Boot's OAuth2 web-security
 * auto-configuration is excluded). The lookup {@code RestClient} attaches the bearer via
 * {@code OAuth2ClientHttpRequestInterceptor}; {@link AddressLookupClient} does not take part.
 *
 */
@Configuration
@Profile({"dev", "local"})
@Slf4j
class FederatedTokenConfig {

    static final String CLIENT_REGISTRATION_ID = "address-lookup";

    @Bean
    StsClient addressLookupStsClient(@Value("${aws.region}") String region, AddressLookupProperties properties) {
        var builder = StsClient.builder()
            .region(Region.of(region))
            // Explicit, though it is the SDK default: in CDP the container credentials are what
            // make the assertion's sub the service's own role ARN.
            .credentialsProvider(DefaultCredentialsProvider.builder().build());
        if (StringUtils.hasText(properties.stsEndpointOverride())) {
            log.info("Using STS endpoint override for the address lookup spike: {}", properties.stsEndpointOverride());
            builder.endpointOverride(URI.create(properties.stsEndpointOverride()));
        }
        return builder.build();
    }

    /**
     * Documented client-credentials customisation: publish an
     * {@link OAuth2AccessTokenResponseClient}{@code <OAuth2ClientCredentialsGrantRequest>}
     * and add a parameters converter. The converter is the STS hop — Spring Security does not
     * know about {@code GetWebIdentityToken}, so this is the hook that turns the STS JWT into
     * Entra's {@code client_assertion}. Token caching/renewal stays in the authorized-client
     * manager, so STS runs per token, not per search.
     */
    @Bean
    OAuth2AccessTokenResponseClient<OAuth2ClientCredentialsGrantRequest> addressLookupTokenResponseClient(
        StsClient addressLookupStsClient, RestClient.Builder restClientBuilder, AddressLookupProperties properties) {
        var tokenResponseClient = new RestClientClientCredentialsTokenResponseClient();
        // Keep the proxy-aware request factory + trace propagation from the shared builder, but
        // reinstate the OAuth2-specific converters/error handling that setRestClient() would
        // otherwise silently drop — without OAuth2AccessTokenResponseHttpMessageConverter, the
        // 200 response body never becomes a real OAuth2AccessTokenResponse (confirmed against a
        // live Entra simulator call, 2026-09-14: "accessToken cannot be null" from
        // ClientCredentialsOAuth2AuthorizedClientProvider, not from anything Entra rejected).
        RestClient entraRestClient = restClientBuilder.clone()
            .messageConverters(converters -> {
                converters.clear();
                converters.add(new FormHttpMessageConverter());
                converters.add(new OAuth2AccessTokenResponseHttpMessageConverter());
            })
            .defaultStatusHandler(new OAuth2ErrorResponseErrorHandler())
            .build();
        tokenResponseClient.setRestClient(entraRestClient);
        tokenResponseClient.addParametersConverter(grantRequest -> clientAssertionParameters(addressLookupStsClient, properties));
        return tokenResponseClient;
    }

    MultiValueMap<String, String> clientAssertionParameters(StsClient stsClient, AddressLookupProperties properties) {
        String assertion = stsClient.getWebIdentityToken(r -> r
                .audience(properties.audience())
                .signingAlgorithm(properties.signingAlgorithm())
                .durationSeconds(properties.assertionDurationSeconds()))
            .webIdentityToken();

        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.set(OAuth2ParameterNames.CLIENT_ASSERTION_TYPE, "urn:ietf:params:oauth:client-assertion-type:jwt-bearer");
        parameters.set(OAuth2ParameterNames.CLIENT_ASSERTION, assertion);
        return parameters;
    }

    /**
     * Client-credentials tokens are application-scoped, not request-scoped, so this is
     * {@link AuthorizedClientServiceOAuth2AuthorizedClientManager} rather than the servlet
     * {@code DefaultOAuth2AuthorizedClientManager}. Boot would publish a manager for us if
     * OAuth2 web security were on; it is excluded (D4c), so the manager is explicit.
     */
    @Bean
    OAuth2AuthorizedClientManager addressLookupAuthorizedClientManager(
        ClientRegistrationRepository clientRegistrationRepository,
        OAuth2AccessTokenResponseClient<OAuth2ClientCredentialsGrantRequest> addressLookupTokenResponseClient) {
        OAuth2AuthorizedClientService authorizedClientService =
            new InMemoryOAuth2AuthorizedClientService(clientRegistrationRepository);
        var authorizedClientProvider = OAuth2AuthorizedClientProviderBuilder.builder()
            .clientCredentials(configurer -> configurer.accessTokenResponseClient(addressLookupTokenResponseClient))
            .build();
        var manager = new AuthorizedClientServiceOAuth2AuthorizedClientManager(
            clientRegistrationRepository, authorizedClientService);
        manager.setAuthorizedClientProvider(authorizedClientProvider);
        return manager;
    }
}
