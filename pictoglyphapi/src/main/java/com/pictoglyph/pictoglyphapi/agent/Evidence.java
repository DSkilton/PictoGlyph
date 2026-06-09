package com.pictoglyph.pictoglyphapi.agent;

import lombok.Builder;

@Builder
public record Evidence(String source, String description, double confidence) {

}
