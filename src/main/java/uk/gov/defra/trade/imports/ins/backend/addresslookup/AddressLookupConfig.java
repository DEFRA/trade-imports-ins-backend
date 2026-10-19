package uk.gov.defra.trade.imports.ins.backend.addresslookup;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.web.client.OAuth2ClientHttpRequestInterceptor;
import org.springframework.web.client.RestClient;

/**
 * Builds the address lookup client, with its token chain from {@link FederatedTokenConfig}, in
 * the {@code dev} and {@code local} profiles only.
 */
@Configuration
@Profile({"dev", "local"})
@EnableConfigurationProperties(AddressLookupProperties.class)
@Import(FederatedTokenConfig.class)
@Slf4j
class AddressLookupConfig {

    private static final Authentication ADDRESS_LOOKUP_PRINCIPAL = new AnonymousAuthenticationToken(
        FederatedTokenConfig.CLIENT_REGISTRATION_ID,
        FederatedTokenConfig.CLIENT_REGISTRATION_ID,
        AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));

    @Bean
    AddressLookupClient addressLookupClient(
        RestClient.Builder restClientBuilder,
        OAuth2AuthorizedClientManager addressLookupAuthorizedClientManager,
        OAuth2AuthorizedClientService addressLookupAuthorizedClientService,
        AddressLookupProperties properties,
        ObjectMapper objectMapper,
        ObjectProvider<MeterRegistry> meterRegistry) {
        log.info("Address lookup enabled: apiUrl={} tenantId={} clientId={} scope={} maxResults={} "
                + "defaultPostcode={} stsEndpointOverride={}",
            properties.apiUrl(), properties.tenantId(), properties.clientId(), properties.clientScope(),
            properties.maxResults(), properties.defaultPostcode(),
            properties.stsEndpointOverride() == null || properties.stsEndpointOverride().isBlank()
                ? "none (real AWS STS)" : properties.stsEndpointOverride());
        return new AddressLookupClient(
            createAddressLookupRestClient(
                restClientBuilder.clone(), addressLookupAuthorizedClientManager, properties),
            properties,
            new AddressLookupMapper(objectMapper),
            addressLookupAuthorizedClientService,
            new AddressLookupMetrics(meterRegistry.getIfAvailable(SimpleMeterRegistry::new)));
    }

    static RestClient createAddressLookupRestClient(
        RestClient.Builder restClientBuilder,
        OAuth2AuthorizedClientManager authorizedClientManager,
        AddressLookupProperties properties) {
        OAuth2ClientHttpRequestInterceptor oauthInterceptor =
            new OAuth2ClientHttpRequestInterceptor(authorizedClientManager);
        oauthInterceptor.setClientRegistrationIdResolver(request -> FederatedTokenConfig.CLIENT_REGISTRATION_ID);
        oauthInterceptor.setPrincipalResolver(request -> ADDRESS_LOOKUP_PRINCIPAL);
        return restClientBuilder
            .baseUrl(properties.apiUrl())
            .requestInterceptor(oauthInterceptor)
            .requestInterceptor(AddressLookupConfig::logAuthorizationSchemeAndTokenLength)
            .build();
    }

    private static ClientHttpResponse logAuthorizationSchemeAndTokenLength(
        HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
        String authorization = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        log.info("Calling the lookup with authorization={}",
            authorization == null ? "ABSENT"
                : authorization.split(" ")[0] + ", token length " + (authorization.length() - 7));
        return execution.execute(request, body);
    }
}
