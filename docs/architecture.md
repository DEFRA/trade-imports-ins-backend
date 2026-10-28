# trade-imports-ins-backend: design notes

## Address lookup

`GET /address-lookup` searches the DEFRA address lookup by postcode or by free text (`find`). It exists in the `dev` and `local` profiles only.

### Profiles

- The profile comes from `ENVIRONMENT`. Outside `dev` and `local` there are no address lookup beans, no bound settings and no profile YAML holding the values.
- `AddressLookupConfig` builds the client. `FederatedTokenConfig`, `AddressLookupController` and `AddressLookupProperties` are found by scanning, so each repeats `@Profile({"dev", "local"})`. Without it, scanning would register them in every profile, and the properties would fail validation where no values are set.
- The controller must be a scanned `@RestController`: Spring MVC registers only `@Controller` stereotypes as handlers.
- `AddressLookupClient` is a plain class made only by `AddressLookupConfig`, so scanning cannot register it twice.
- `NoWebSecurityIT` guards the `spring.autoconfigure.exclude` list in `application.yml`. The OAuth2 client puts `spring-security-config` on the classpath. Without every exclusion, Boot adds a `SecurityFilterChain` and a generated-password user, and existing endpoints demand credentials. The test sets an OAuth2 registration because Boot adds its chain only once a registration exists.
- The pom uses the bare `spring-security-oauth2-client`, not the starter, which would switch on Boot's default HTTP Basic.

### Token chain

- AWS STS `GetWebIdentityToken` mints a JWT. Entra `client_credentials` takes it as the `client_assertion` and returns an access token. No client secret is stored.
- Spring Security does not know STS. A parameters converter on the client-credentials token response client is the hook that adds the assertion. The authorized-client manager caches and renews the Entra token, so STS runs once per token, not once per search.
- One application-scoped principal is used for every call, so Spring Security caches one Entra token rather than one per user.
- The Entra `RestClient` reuses the shared builder for the proxy-aware request factory and trace propagation, but replaces its message converters. Without `OAuth2AccessTokenResponseHttpMessageConverter` the token response never becomes an `OAuth2AccessTokenResponse`, and Spring fails with "accessToken cannot be null".
- The lookup `RestClient` carries `OAuth2ClientHttpRequestInterceptor`, which attaches the bearer token. A second interceptor, registered after it, logs the authorization scheme and token length, never the token. A token that was obtained does not prove it reached the gateway.
- With an STS endpoint override, the client points at the stub's simulator, which does not check signatures. The SDK still signs, so placeholder credentials let a native run work with no AWS set-up. Without an override, the default credentials provider is set explicitly: in CDP the container credentials make the assertion's subject the service's own role ARN.

### Logging

- At start-up the client logs its identifiers (API URL, tenant, client id, scope). In dev the likeliest failure is configuration, and a wrong value shows up as a refusal that looks like something else. No token, assertion or secret is logged.
- An API Management `validate-jwt` policy matches `aud` and `iss` exactly and refuses with only "missing or invalid", so the access token's claims are logged. The token is not.
- The STS assertion is not logged. Its expiry is, to show the hop ran and honoured the requested duration.
- The Entra call logs once each time it runs. It runs only when the cached token has expired, so its absence from the logs shows that a search reused a token.
- A refusal logs the status, the `WWW-Authenticate` header and the first 500 characters of the body. The status alone cannot tell a rejected token from a missing subscription key; the gateway's words in the body can. An API gateway can answer with a whole HTML page, hence the limit.
- An STS failure answers `STS_FAILED` rather than a 500. A native run with no AWS credentials is the usual cause.

### Retry on 401

- Spring renews the token when it expires, but a token can stop being accepted before then, for example after a registration is revoked. The cached token would then be refused until it expires, for up to an hour.
- On a 401 the client evicts the cached token and retries once. A second 401 means the token is not the problem: a wrong audience answers the same way. The lookup is shared across the estate at 300 requests a minute, so the client does not retry again.

### Asking for both postcode and find

A call can be one search or the other. A request with both answers 400 rather than quietly running the postcode search. The controller handles its own `ResponseStatusException`; otherwise `GlobalExceptionHandler`'s `RuntimeException` handler would turn the 400 into a 500.

### Timings and metrics

- The lookup time is measured around the whole exchange. On a cold search that includes the STS and Entra hops, which Spring Security runs inside it. `LookupTimings` takes them back off, so `lookupMs` is the gateway's own time on a cold or a warm search.
- `stsMs` and `entraMs` are null on a warm search, so a cold search and a cached one have different shapes.
- Metrics reach CloudWatch through `EmfMetricsPublisher`, for Grafana dashboards in `Playground/trade-imports-ins-backend-monitoring`. That publisher writes a meter's name and value only: it drops tags, and publishes every measurement of a meter under the meter's name. So every metric is a counter with its dimension in its name, such as `addressLookup.outcome.results`.
- Durations are published as running totals, because a counter survives being summed across a minute's flush and across instances. Mean lookup time is `addressLookup.durationMs.lookup` divided by `addressLookup.searches`. The publisher cannot carry a distribution, so there are no percentiles.
- Every counter is registered at zero up front. A counter created by its first increment is first scraped above zero, and a Prometheus `increase()` cannot see that first rise.

## Notification dates

- `arrivalDate` is a calendar date: a `LocalDate` on the document and in the API, `YYYY-MM-DD` on the wire, and a string in Mongo. The pair of converters in `LocalDateStringConverters`, registered in `MongoConfig`, stores it as that string. Without them Spring Data writes a BSON date at midnight in the JVM's zone, and the stored day then depends on where the service runs. `YYYY-MM-DD` strings sort in date order, so the `arrivalDate` sort and the `status_sort_fields` index work on the string.
- The GBN-AG event carries the arrival as an instant, `scheduledOccurrenceDateTime`, because PIMS needs one. The animals backend writes it at the start of the chosen day in UTC, so `NotificationUpsertService` takes the day back in UTC, whatever the JVM's zone.
- `lastUpdated` is a moment: an `Instant`, RFC 3339 with a `Z` on the wire, and a BSON date in Mongo.

## Outbound proxy

- CDP sends all egress except database traffic through its proxy. `ProxyConfig` sets the JVM proxy properties and the default `ProxySelector` from `HTTP_PROXY`, so every Java HTTP client uses it.
- A malformed proxy URL is logged with its user info replaced by `***`, and without the parse exception, because that exception's message quotes the whole URL, credentials included.

## Error responses

`GlobalExceptionHandler` handles only application exceptions. Spring's own exceptions, such as a 404 for an unknown path, keep Spring's responses.

## TLS

`TrustStoreConfiguration` builds an `SSLContext` that tries the JVM's default trust store first and then the CDP root CA from `cdp.certificate`. It runs at highest precedence so the context exists before the Mongo client and HTTP clients are made.
