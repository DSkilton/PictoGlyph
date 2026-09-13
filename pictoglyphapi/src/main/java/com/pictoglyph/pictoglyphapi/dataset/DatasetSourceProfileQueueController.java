package com.pictoglyph.pictoglyphapi.dataset;

import com.pictoglyph.pictoglyphapi.dataset.api.DatasetSourceProfileQueueRequest;
import com.pictoglyph.pictoglyphapi.dataset.api.DatasetSourceProfileQueueResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/datasets/profile-source-queues")
@RequiredArgsConstructor
public class DatasetSourceProfileQueueController {

	private final DatasetSourceProfileQueueService service;

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	private DatasetSourceProfileQueueResponse run(@Valid @RequestBody DatasetSourceProfileQueueRequest request) {
		return service.run(request);
	}
}
