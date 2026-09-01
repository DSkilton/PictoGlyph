package com.pictoglyph.pictoglyphapi.dataset;

import com.pictoglyph.pictoglyphapi.dataset.api.DatasetSourceQueueRequest;
import com.pictoglyph.pictoglyphapi.dataset.api.DatasetSourceQueueResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/datasets/source-queues")
@RequiredArgsConstructor
public class DatasetSourceQueueController {

	private final DatasetSourceQueueService service;

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public DatasetSourceQueueResponse run(@RequestBody DatasetSourceQueueRequest request) {
		return service.run(request);
	}

}
