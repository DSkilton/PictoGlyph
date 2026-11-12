package com.pictoglyph.pictoglyphapi.ingestion.research.discovery;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pictoglyph.pictoglyphapi.ingestion.api.SourceFieldMapping;
import com.pictoglyph.pictoglyphapi.ingestion.mapping.FieldMatch;
import com.pictoglyph.pictoglyphapi.ingestion.mapping.MappingCompleteness;
import com.pictoglyph.pictoglyphapi.ingestion.mapping.SourceFieldDiscoveryService;
import com.pictoglyph.pictoglyphapi.ingestion.mapping.SourceMappingTarget;
import com.pictoglyph.pictoglyphapi.ingestion.mapping.SourceMappingValidationResult;
import com.pictoglyph.pictoglyphapi.ingestion.mapping.SourceMappingValidator;
import com.pictoglyph.pictoglyphapi.ingestion.mapping.SourceSample;
import com.pictoglyph.pictoglyphapi.ingestion.mapping.strategy.SourceMappingContext;
import com.pictoglyph.pictoglyphapi.ingestion.mapping.strategy.SourceMappingPipeline;
import com.pictoglyph.pictoglyphapi.ingestion.research.ResearchImageAssetRequest;
import com.pictoglyph.pictoglyphapi.ingestion.research.ResearchRecordRequest;
import com.pictoglyph.pictoglyphapi.ingestion.research.ResearchRecordResponse;
import com.pictoglyph.pictoglyphapi.ingestion.research.ResearchRecordService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ResearchDiscoveryService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ResearchDiscoveryService.class);
    private final Map<String, ResearchDiscoveryAdapter> adapters;
    private final ResearchRecordService recordService;
    private final SourceMappingPipeline mappingPipeline;
    private final SourceFieldDiscoveryService fieldDiscovery;
    private final SourceMappingValidator mappingValidator;
    private final ObjectMapper mapper;

    public ResearchDiscoveryService(List<ResearchDiscoveryAdapter> adapters, ResearchRecordService recordService,
            SourceMappingPipeline mappingPipeline, SourceFieldDiscoveryService fieldDiscovery,
            SourceMappingValidator mappingValidator, ObjectMapper mapper) {
        this.adapters = adapters.stream().collect(Collectors.toUnmodifiableMap(ResearchDiscoveryAdapter::sourceId, Function.identity()));
        this.recordService = recordService;
        this.mappingPipeline = mappingPipeline;
        this.fieldDiscovery = fieldDiscovery;
        this.mappingValidator = mappingValidator;
        this.mapper = mapper;
    }

    public ResearchDiscoveryPreview preview(String sourceId, String query, int limit, int offset) {
        ResearchDiscoveryAdapter adapter = adapters.get(sourceId);
        if (adapter == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown research API source");
        ResearchDiscoveryPreview result = adapter.preview(query, limit, offset);
        LOGGER.info("event=research_discovery_preview sourceId={} candidates={} limit={} offset={}", sourceId, result.candidates().size(), limit, offset);
        return result;
    }

    public ResearchDiscoveryImportResult importPage(String sourceId, String query, int limit, int offset) {
        ResearchDiscoveryPreview page = preview(sourceId, query, limit, offset);
        int created = 0;
        int reused = 0;
        int imagesCreated = 0;
        int skipped = 0;
        int signProposals = 0;

        for (ResearchDiscoveryPreview.Candidate candidate : page.candidates()) {
            if (candidate.sourceRecordId() == null || candidate.sourceRecordId().isBlank()) {
                skipped++;
                LOGGER.warn("event=research_discovery_record_skipped sourceId={} reason=MISSING_SOURCE_ID", sourceId);
                continue;
            }
            if (candidate.rawRecord() == null || !candidate.rawRecord().isObject()) {
                skipped++;
                LOGGER.warn("event=research_discovery_record_skipped sourceId={} sourceRecordId={} reason=INVALID_RAW_RECORD", sourceId, candidate.sourceRecordId());
                continue;
            }

            ObjectNode provenance = mapper.createObjectNode();
            provenance.put("acquisitionMode", "API_METADATA_REFERENCE");
            provenance.put("discoveryUrl", page.requestUrl());
            provenance.put("sourceRecordId", candidate.sourceRecordId());
            provenance.put("rightsStatement", candidate.rights() == null ? "NOT_STATED" : candidate.rights());
            provenance.put("trainingDataApproved", false);

            if (candidate.explicitSignEvidence()) {
                ObjectNode assessment = assessSignEvidence(page, candidate);
                provenance.set("signMappingAssessment", assessment);
                if ("PROPOSED_VALID".equals(assessment.path("status").asText())) signProposals++;
            } else {
                provenance.put("signMappingStatus", "NOT_APPLICABLE_ARTEFACT");
            }

            ObjectNode context = mapper.createObjectNode();
            context.put("title", candidate.title() == null ? "" : candidate.title());
            context.put("interpretationStatus", "UNVERIFIED");
            context.put("sourceObjectId", candidate.sourceRecordId());

            List<ResearchImageAssetRequest> images = imageReferences(candidate);
            String recordUrl = candidate.objectUrl() == null || candidate.objectUrl().isBlank() ? page.requestUrl() : candidate.objectUrl();
            ResearchRecordRequest request = new ResearchRecordRequest(sourceId, "object:" + candidate.sourceRecordId(),
                    candidate.explicitSignEvidence() ? "SIGN_CANDIDATE" : "ARTEFACT", recordUrl,
                    context, candidate.rawRecord().deepCopy(), provenance, images);

            ResearchRecordResponse response = recordService.importRecord(request);
            if (response.created()) created++; else reused++;
            imagesCreated += response.imageAssetsCreated();
            LOGGER.info("event=research_discovery_record_imported sourceId={} recordKey={} created={} imagesCreated={}",
                    sourceId, request.recordKey(), response.created(), response.imageAssetsCreated());
        }
        return new ResearchDiscoveryImportResult(sourceId, page.requestUrl(), page.candidates().size(), created, reused,
                imagesCreated, skipped, signProposals);
    }

    private ObjectNode assessSignEvidence(ResearchDiscoveryPreview page, ResearchDiscoveryPreview.Candidate candidate) {
        SourceSample sample = new SourceSample(null, List.of(candidate.rawRecord()));
        Map<SourceMappingTarget, FieldMatch> matches = mappingPipeline.resolve(new SourceMappingContext(
                page.sourceId(), page.requestUrl(), sample, fieldDiscovery.discoverFields(sample.sampleItems())));

        ObjectNode result = mapper.createObjectNode();
        result.put("status", "NEEDS_REVIEW");
        result.set("proposedMappings", mapper.valueToTree(matches));
        if (!MappingCompleteness.hasRequiredMappings(matches)) return result;

        SourceFieldMapping mapping = new SourceFieldMapping(null, field(matches, SourceMappingTarget.SYMBOL_CODE),
                field(matches, SourceMappingTarget.IMAGE_PATH), field(matches, SourceMappingTarget.TITLE),
                field(matches, SourceMappingTarget.DESCRIPTION), field(matches, SourceMappingTarget.PLACE),
                field(matches, SourceMappingTarget.PERIOD), field(matches, SourceMappingTarget.DATE_START),
                field(matches, SourceMappingTarget.DATE_END));
        SourceMappingValidationResult validation = mappingValidator.validate(mapping, sample.sampleItems());
        result.set("validationErrors", mapper.valueToTree(validation.errors()));
        result.set("validationWarnings", mapper.valueToTree(validation.warnings()));
        if (validation.valid() && validation.warnings().isEmpty()) result.put("status", "PROPOSED_VALID");
        return result;
    }

    private static String field(Map<SourceMappingTarget, FieldMatch> matches, SourceMappingTarget target) {
        FieldMatch match = matches.get(target);
        return match == null ? null : match.sourceField();
    }

    private List<ResearchImageAssetRequest> imageReferences(ResearchDiscoveryPreview.Candidate candidate) {
        Map<String, ResearchImageAssetRequest> images = new LinkedHashMap<>();
        if (candidate.imageUrls() == null) return List.of();
        for (String url : candidate.imageUrls()) {
            if (url == null || url.length() > 4000) continue;
            URI uri;
            try { uri = URI.create(url); } catch (IllegalArgumentException exception) { continue; }
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null) continue;
            String key = "image:" + sha256(url);
            ObjectNode analysis = mapper.createObjectNode();
            analysis.put("evaluationStatus", "NOT_EVALUATED");
            analysis.put("downloadStatus", "REMOTE_REFERENCE_ONLY");
            analysis.put("trainingDataApproved", false);
            images.putIfAbsent(key, new ResearchImageAssetRequest(key, url, "REMOTE_MUSEUM_IMAGE_REFERENCE",
                    candidate.rights(), null, analysis));
        }
        return List.copyOf(images.values());
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
