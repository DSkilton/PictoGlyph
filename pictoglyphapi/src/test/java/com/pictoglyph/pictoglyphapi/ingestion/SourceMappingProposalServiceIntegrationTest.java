package com.pictoglyph.pictoglyphapi.ingestion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pictoglyph.pictoglyphapi.ingestion.api.SourceFieldMapping;
import com.pictoglyph.pictoglyphapi.ingestion.api.SourceMappingProposalRequest;
import com.pictoglyph.pictoglyphapi.ingestion.api.SourceMappingProposalResponse;
import com.pictoglyph.pictoglyphapi.ingestion.mapping.SourceFieldDiscoveryService;
import com.pictoglyph.pictoglyphapi.ingestion.mapping.SourceFieldMatcher;
import com.pictoglyph.pictoglyphapi.ingestion.mapping.SourceMappingConfidenceCalculator;
import com.pictoglyph.pictoglyphapi.ingestion.mapping.SourceSampleReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class SourceMappingProposalServiceIntegrationTest {

	private static final String API_URL = "http://localhost:9000/sample-api-symbols.json";

	private MockRestServiceServer mockServer;
	private SourceMappingProposalService service;

	@BeforeEach
	void setUp() {
		RestTemplate restTemplate = new RestTemplate();
		ObjectMapper objectMapper = new ObjectMapper();

		mockServer = MockRestServiceServer.bindTo(restTemplate).build();

		SourceSampleReader sampleReader = new SourceSampleReader(restTemplate,objectMapper);

		SourceFieldDiscoveryService discoveryService = new SourceFieldDiscoveryService();

		SourceFieldMatcher fieldMatcher = new SourceFieldMatcher();

		SourceMappingConfidenceCalculator confidenceCalculator = new SourceMappingConfidenceCalculator();

		service = new SourceMappingProposalService(sampleReader, discoveryService, fieldMatcher, confidenceCalculator
		);
	}

	@Test
	void shouldProposeCorrectMappingsAndRejectUnsupportedOptionalMappings() {
		String responseJson = """
				{
				  "symbols": [
				    {
				      "symbolCode": "A1",
				      "imageUrl": "https://example.org/images/a1.png",
				      "label": "Seated man"
				    },
				    {
				      "symbolCode": "D21",
				      "imageUrl": "https://example.org/images/d21.png",
				      "label": "Mouth"
				    },
				    {
				      "symbolCode": "G17",
				      "imageUrl": "https://example.org/images/g17.png",
				      "label": "Owl"
				    }
				  ]
				}
				""";

		mockServer.expect(once(), requestTo(API_URL))
				.andExpect(method(GET))
				.andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON));

		SourceMappingProposalRequest request = new SourceMappingProposalRequest("Local mock symbol API", API_URL, null);
		SourceMappingProposalResponse response = service.proposeMapping(request);
		SourceFieldMapping mapping = response.proposedMapping();

		assertThat(response.sourceName()).isEqualTo("Local mock symbol API");
		assertThat(response.apiUrl()).isEqualTo(API_URL);
		assertThat(response.sampledItemCount()).isEqualTo(3);
		assertThat(response.discoveredFields()).containsExactlyInAnyOrder("symbolCode", "imageUrl", "label");
		assertThat(mapping.itemArrayField()).isEqualTo("symbols");
		assertThat(mapping.symbolCodeField()).isEqualTo("symbolCode");
		assertThat(mapping.imagePathField()).isEqualTo("imageUrl");
		assertThat(mapping.titleField()).isEqualTo("label");

		assertThat(mapping.descriptionField()).isNull();
		assertThat(mapping.placeField()).isNull();
		assertThat(mapping.periodField()).isNull();
		assertThat(mapping.dateStartField()).isNull();
		assertThat(mapping.dateEndField()).isNull();

		assertThat(response.overallConfidence()).isGreaterThanOrEqualTo(0.85);

		assertThat(response.evidence()).anySatisfy(item -> {
			assertThat(item.targetField()).isEqualTo("symbolCodeField");
			assertThat(item.sourceField()).isEqualTo("symbolCode");
			assertThat(item.confidence()).isGreaterThanOrEqualTo(0.85);});
		mockServer.verify();

		assertThat(response.evidence()).anySatisfy(item -> {
			assertThat(item.targetField()).isEqualTo("imagePathField");
			assertThat(item.sourceField()).isEqualTo("imageUrl");
			assertThat(item.confidence()).isGreaterThanOrEqualTo(0.90);});
			mockServer.verify();
		}

	@Test
	void shouldDiscoverAndMapNestedFields() {
		String responseJson = """
				{
				  "results": [
				    {
				      "object_number": "EA123",
				      "display": {
				        "title": "Hieroglyphic sign"
				      },
				      "media": {
				        "image_url": "https://example.org/images/ea123.jpg"
				      },
				      "production": {
				        "period": "New Kingdom"
				      },
				      "findspot": {
				        "place": "Thebes"
				      }
				    }
				  ]
				}
				""";

		mockServer.expect(once(), requestTo(API_URL))
				.andExpect(method(GET))
				.andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON));

		SourceMappingProposalResponse response = service.proposeMapping(new SourceMappingProposalRequest("Nested museum API", API_URL, null		));

		SourceFieldMapping mapping = response.proposedMapping();

		assertThat(mapping.itemArrayField()).isEqualTo("results");

		assertThat(mapping.symbolCodeField()).isEqualTo("object_number");

		assertThat(mapping.imagePathField()).isEqualTo("media.image_url");

		assertThat(mapping.titleField()).isEqualTo("display.title");

		assertThat(mapping.periodField()).isEqualTo("production.period");

		assertThat(mapping.placeField()).isEqualTo("findspot.place");

		assertThat(mapping.dateStartField()).isNull();
		assertThat(mapping.dateEndField()).isNull();

		mockServer.verify();
	}

	@Test
	void shouldNotMapMisleadingValuesToUnrelatedTargets() {
		String responseJson = """
				{
				  "symbols": [
				    {
				      "symbolCode": "A9000",
				      "imageUrl": "http://localhost:9000/images/a1.png",
				      "label": "Memphis"
				    }
				  ]
				}
				""";

		mockServer.expect(once(), requestTo(API_URL))
				.andExpect(method(GET))
				.andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON));

		SourceMappingProposalResponse response = service.proposeMapping(new SourceMappingProposalRequest("Misleading source", API_URL, null		));

		SourceFieldMapping mapping = response.proposedMapping();

		assertThat(mapping.symbolCodeField()).isEqualTo("symbolCode");
		assertThat(mapping.imagePathField()).isEqualTo("imageUrl");

		assertThat(mapping.placeField()).isNull();
		assertThat(mapping.dateStartField()).isNull();
		assertThat(mapping.dateEndField()).isNull();

		mockServer.verify();
	}

	@Test
	void shouldReturnLowConfidenceWhenRequiredMappingsAreMissing() {
		String responseJson = """
				{
				  "items": [
				    {
				      "title": "Unknown artefact",
				      "description": "No code or image available"
				    }
				  ]
				}
				""";

		mockServer.expect(once(), requestTo(API_URL))
				.andExpect(method(GET))
				.andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON));

		SourceMappingProposalResponse response = service.proposeMapping(new SourceMappingProposalRequest("Incomplete source", API_URL, null));

		SourceFieldMapping mapping = response.proposedMapping();

		assertThat(mapping.symbolCodeField()).isNull();
		assertThat(mapping.imagePathField()).isNull();

		assertThat(mapping.titleField()).isEqualTo("title");
		assertThat(mapping.descriptionField()).isEqualTo("description");

		assertThat(response.overallConfidence()).isLessThan(0.50);

		mockServer.verify();
	}

	@Test
	void shouldNotReuseOneSourceFieldForMultipleMappingTargets() {
		String responseJson = """
			{
			  "items": [
			    {
			      "id": "A1",
			      "image": "https://example.org/images/a1.png",
			      "name": "Seated man"
			    }
			  ]
			}
			""";

		mockServer.expect(once(), requestTo(API_URL))
				.andExpect(method(GET))
				.andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON));

		SourceMappingProposalResponse response = service.proposeMapping(
				new SourceMappingProposalRequest(
						"Field reuse source",
						API_URL,
						null
				)
		);

		SourceFieldMapping mapping = response.proposedMapping();

		assertThat(mapping.symbolCodeField()).isEqualTo("id");
		assertThat(mapping.imagePathField()).isEqualTo("image");
		assertThat(mapping.titleField()).isEqualTo("name");

		assertThat(response.proposedMapping())
				.extracting(
						SourceFieldMapping::symbolCodeField,
						SourceFieldMapping::imagePathField,
						SourceFieldMapping::titleField
				)
				.doesNotHaveDuplicates();

		mockServer.verify();
	}
}