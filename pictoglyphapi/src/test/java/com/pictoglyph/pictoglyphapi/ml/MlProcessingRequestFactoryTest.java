package com.pictoglyph.pictoglyphapi.ml;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pictoglyph.pictoglyphapi.constants;
import com.pictoglyph.pictoglyphapi.entities.core.Language;
import com.pictoglyph.pictoglyphapi.entities.core.Symbol;
import com.pictoglyph.pictoglyphapi.entities.ml.MlProcessingJob;
import com.pictoglyph.pictoglyphapi.entities.ml.MlProcessingTaskType;
import com.pictoglyph.pictoglyphapi.ml.api.MlApiContractVersions;
import com.pictoglyph.pictoglyphapi.ml.api.MlProcessingRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class MlProcessingRequestFactoryTest {

	private MlProcessingRequestFactory factory;

	@BeforeEach
	void setUp() {
		factory = new MlProcessingRequestFactory();
	}

	@Test
	void shouldCreateRequestFromJobAndSymbol() {
		Language language = Language.builder()
				.id(1L)
				.name("Ancient Egyptian")
				.build();

		Symbol symbol = Symbol.builder()
				.id(42L)
				.language(language)
				.symbolCode("A1")
				.imagePath(constants.PICTOGLYPH_TEST_FILE_LOCATION)
				.meta(new ObjectMapper()
						.createObjectNode().put("sourceType", "API"))
				.build();

		MlProcessingJob job = MlProcessingJob.builder()
				.id(25L)
				.symbolId(42L)
				.taskType(MlProcessingTaskType.GENERATE_IMAGE_EMBEDDING)
				.modelProfile("SIGLIP_BASELINE_V1")
				.inputChecksum("abc123checksum")
				.build();

		MlProcessingRequest request = factory.create(job, symbol);

		assertThat(request.contractVersion()).isEqualTo(MlApiContractVersions.V1);
		assertThat(request.modelProfile()).isEqualTo("SIGLIP_BASELINE_V1");
		assertThat(request.imagePath()).isEqualTo(constants.PICTOGLYPH_TEST_FILE_LOCATION);
		assertThat(request.inputChecksum()).isEqualTo("abc123checksum");
		assertThat(request.symbolCode()).isEqualTo("A1");
		assertThat(request.languageId()).isEqualTo(1L);
		assertThat(request.metadata().path("sourceType").asText()).isEqualTo("API");
	}

	@Test
	void shouldRejectDifferentSymbol() {
		MlProcessingJob job = MlProcessingJob.builder()
				.id(25L)
				.symbolId(42L)
				.build();

		Symbol symbol = Symbol.builder()
				.id(99L)
				.build();

		assertThatThrownBy(() ->
				factory.create(job, symbol))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("does not match");
	}

	@Test
	void shouldRejectMissingImagePath() {
		MlProcessingJob job = MlProcessingJob.builder()
				.id(25L)
				.symbolId(42L)
				.inputChecksum("abc123")
				.build();

		Symbol symbol = Symbol.builder()
				.id(42L)
				.imagePath(" ")
				.build();

		assertThatThrownBy(() ->
				factory.create(job, symbol))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("image path");
	}
}
