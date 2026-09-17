package uk.gov.defra.trade.imports.ins.backend.addresslookup;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.annotation.Nullable;

/**
 * The {@code {statusCode, message}} error body shape (addressLookup_21.yaml,
 * {@code Response_Error_Singleton}) — used only to detect the rejected-postcode 400
 * ("Requested postcode…"), which marine-licensing's observed behaviour treats as no results.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record AddressLookupErrorResponse(@Nullable Integer statusCode, @Nullable String message) {
}
