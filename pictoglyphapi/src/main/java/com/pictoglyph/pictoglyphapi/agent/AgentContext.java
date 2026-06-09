package com.pictoglyph.pictoglyphapi.agent;

import lombok.Builder;

/**
 * Context supplied to the PictoGlyph Agent.
 *
 * This object contains all information required for an
 * investigation and is passed between agent components.
 *
 * Future additions may include:
 * - inscriptionId
 * - imageId
 * - routeId
 * - material
 * - period
 * - date range
 * - source dataset
 */
@Builder
public record AgentContext(Long symbolId, Long languageId, Long placeId, String question) {


}