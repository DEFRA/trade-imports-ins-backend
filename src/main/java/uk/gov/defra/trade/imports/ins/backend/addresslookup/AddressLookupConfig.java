package uk.gov.defra.trade.imports.ins.backend.addresslookup;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.web.client.OAuth2ClientHttpRequestInterceptor;
import org.springframework.web.client.RestClient;

/**
 * The dev/local gate for the whole EUDPA-390 address lookup spike.
 *
 * <p>Config binding, the token chain ({@link FederatedTokenConfig}) and the lookup client are
 * all gated by {@code @Profile({"dev", "local"})}. The profile comes from {@code ENVIRONMENT},
 * so outside {@code dev}/{@code local} none of it exists: no beans, no config binding, and no
 * {@code application-<profile>.yml} carrying the values either.
 *
 * <p>The two component-scanned classes here — {@link FederatedTokenConfig} and
 * {@link AddressLookupController} — repeat that {@code @Profile}, because scanning would
 * otherwise register them whatever this class decides. For the controller that is unavoidable:
 * it has to be a scanned {@code @RestController} rather than a bean made here, because Spring
 * MVC's {@code RequestMappingHandlerMapping.isHandler()} only recognises the {@code @Controller}
 * stereotype as a handler.
 *
 * <p>{@link AddressLookupClient} is not a {@code @Component} — it isn't a handler, so it stays
 * as a plain class instantiated only here, with no risk of classpath scanning double-registering
 * it. It receives a RestClient that already carries Spring Security's
 * {@link OAuth2ClientHttpRequestInterceptor}, so the GET itself has no token hops.
 *
 */
@Configuration
@Profile({"dev", "local"})
@EnableConfigurationProperties(AddressLookupProperties.class)
@Import(FederatedTokenConfig.class)
@Slf4j
class AddressLookupConfig {

    /**
     * One application-scoped principal for client_credentials, so Spring Security caches a
     * single Entra token rather than one per incoming user.
     */
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
        // Optional on purpose. Metrics are a nice-to-have on a spike; a context without a
        // registry — a slice test, or metrics turned off — must still start and still search.
        ObjectProvider<MeterRegistry> meterRegistry) {
        // In dev the likeliest failure is configuration rather than code, and a wrong value shows
        // up as a refusal from Entra or the gateway that looks like something else. Identifiers
        // only — no token, assertion or secret is ever logged.
        log.info("Address lookup spike enabled: apiUrl={} tenantId={} clientId={} scope={} maxResults={} "
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

    /**
     * Spring Security's documented RestClient arrangement: intercept every call, resolve the
     * registration and principal here, attach {@code Authorization: Bearer}. Callers then
     * {@code restClient.get().uri(...)} with no OAuth attributes.
     */
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
            // Registered after the OAuth2 interceptor, so it sees the header that one attached.
            // "Access token is missing or invalid" covers both halves, and until now nothing
            // proved which: that a token was obtained does not prove it reached the gateway.
            .requestInterceptor((request, body, execution) -> {
                String authorization = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
                log.info("Calling the lookup with authorization={}",
                    authorization == null ? "ABSENT"
                        : authorization.split(" ")[0] + ", token length " + (authorization.length() - 7));
                return execution.execute(request, body);
            })
            .build();
    }
}
