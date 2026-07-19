package com.tsegabrehan.einvoicing.domain;

/**
 * A trading party as it appears on an invoice (seller or buyer).
 * EN 16931 requires identification for both parties (SRS FR-4).
 *
 * @param name        legal/registered name
 * @param vatId       VAT identification number (e.g. "DE123456789"); required for
 *                    the seller, and for the buyer where relevant to VAT treatment
 * @param addressLine street + house number
 * @param postalCode  postal code
 * @param city        city
 * @param countryCode ISO 3166-1 alpha-2 country code (e.g. "DE")
 */
public record Party(
        String name,
        String vatId,
        String addressLine,
        String postalCode,
        String city,
        String countryCode
) {
}
