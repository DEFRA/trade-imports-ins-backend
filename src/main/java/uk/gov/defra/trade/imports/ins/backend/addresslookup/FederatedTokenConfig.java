package uk.gov.defra.trade.imports.ins.backend.addresslookup;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;
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
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sts.StsClient;

/**
 * The federated-credential token chain for the address lookup: AWS STS
 * {@code GetWebIdentityToken}, then Entra {@code client_credentials} with that token as the
 * {@code client_assertion}, then a cached Entra access token. No client secret is stored.
 */
@Configuration
@Profile({"dev", "local"})
@Slf4j
class FederatedTokenConfig {

    static final String CLIENT_REGISTRATION_ID = "address-lookup";

    @Bean
    StsClient addressLookupStsClient(@Value("${aws.region}") String region, AddressLookupProperties properties) {
        var builder = StsClient.builder().region(Region.of(region));
        if (StringUtils.hasText(properties.stsEndpointOverride())) {
            log.info("Using STS endpoint override for the address lookup: {}", properties.stsEndpointOverride());
            builder.endpointOverride(URI.create(properties.stsEndpointOverride()));
            builder.credentialsProvider(
                StaticCredentialsProvider.create(AwsBasicCredentials.create("simulator", "simulator")));
        } else {
            builder.credentialsProvider(DefaultCredentialsProvider.builder().build());
        }
        return builder.build();
    }

    @Bean
    OAuth2AccessTokenResponseClient<OAuth2ClientCredentialsGrantRequest> addressLookupTokenResponseClient(
        StsClient addressLookupStsClient, RestClient.Builder restClientBuilder, AddressLookupProperties properties) {
        var tokenResponseClient = new RestClientClientCredentialsTokenResponseClient();
        RestClient entraRestClient = restClientBuilder.clone()
            .messageConverters(converters -> {
                converters.clear();
                converters.add(new FormHttpMessageConverter());
                converters.add(new OAuth2AccessTokenResponseHttpMessageConverter());
            })
            .defaultStatusHandler(new OAuth2ErrorResponseErrorHandler())
            .requestInterceptor(FederatedTokenConfig::timeAndLogEntraTokenCall)
            .build();
        tokenResponseClient.setRestClient(entraRestClient);
        tokenResponseClient.addParametersConverter(grantRequest -> clientAssertionParameters(addressLookupStsClient, properties));
        return grantRequest -> {
            var response = tokenResponseClient.getTokenResponse(grantRequest);
            logTokenClaims(response.getAccessToken().getTokenValue());
            return response;
        };
    }

    private static ClientHttpResponse timeAndLogEntraTokenCall(
        HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
        long start = System.currentTimeMillis();
        ClientHttpResponse response = execution.execute(request, body);
        long elapsed = System.currentTimeMillis() - start;
        LookupTimingsRecorder.recordEntra(elapsed);
        log.info("Entra token endpoint answered {} in {}ms", response.getStatusCode().value(), elapsed);
        return response;
    }

    private static void logTokenClaims(String accessToken) {
        try {
            String[] segments = accessToken.split("\\.");
            if (segments.length < 2) {
                log.warn("Entra access token is not a JWT, so its claims cannot be logged");
                return;
            }
            byte[] payload = Base64.getUrlDecoder().decode(segments[1]);
            JsonNode claims = new ObjectMapper().readTree(new String(payload, StandardCharsets.UTF_8));
            log.info("Entra access token claims: aud={} iss={} appid={} azp={} roles={} exp={}",
                claims.path("aud").asText(null), claims.path("iss").asText(null),
                claims.path("appid").asText(null), claims.path("azp").asText(null),
                claims.path("roles"), claims.path("exp").asText(null));
        } catch (RuntimeException | com.fasterxml.jackson.core.JsonProcessingException ex) {
            log.warn("Could not read the Entra access token's claims", ex);
        }
    }

    MultiValueMap<String, String> clientAssertionParameters(StsClient stsClient, AddressLookupProperties properties) {
        long start = System.currentTimeMillis();
        var token = stsClient.getWebIdentityToken(r -> r
            .audience(properties.audience())
            .signingAlgorithm(properties.signingAlgorithm())
            .durationSeconds(properties.assertionDurationSeconds()));
        String assertion = token.webIdentityToken();
        long elapsed = System.currentTimeMillis() - start;
        LookupTimingsRecorder.recordSts(elapsed);
        log.info("Minted an STS web identity assertion for audience={} in {}ms, expires {}",
            properties.audience(), elapsed, token.expiration());

        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.set(OAuth2ParameterNames.CLIENT_ASSERTION_TYPE, "urn:ietf:params:oauth:client-assertion-type:jwt-bearer");
        parameters.set(OAuth2ParameterNames.CLIENT_ASSERTION, assertion);
        return parameters;
    }

    @Bean
    OAuth2AuthorizedClientService addressLookupAuthorizedClientService(
        ClientRegistrationRepository clientRegistrationRepository) {
        return new InMemoryOAuth2AuthorizedClientService(clientRegistrationRepository);
    }

    @Bean
    OAuth2AuthorizedClientManager addressLookupAuthorizedClientManager(
        ClientRegistrationRepository clientRegistrationRepository,
        OAuth2AuthorizedClientService authorizedClientService,
        OAuth2AccessTokenResponseClient<OAuth2ClientCredentialsGrantRequest> addressLookupTokenResponseClient) {
        var authorizedClientProvider = OAuth2AuthorizedClientProviderBuilder.builder()
            .clientCredentials(configurer -> configurer.accessTokenResponseClient(addressLookupTokenResponseClient))
            .build();
        var manager = new AuthorizedClientServiceOAuth2AuthorizedClientManager(
            clientRegistrationRepository, authorizedClientService);
        manager.setAuthorizedClientProvider(authorizedClientProvider);
        return manager;
    }
}
