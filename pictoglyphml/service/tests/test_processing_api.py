from fastapi.testclient import TestClient
from app.main import app

client = TestClient(app)

def valid_request() -> dict:
    return {
        "contractVersion": "1.0",
        "jobId": 25,
        "symbolId": 42,
        "taskType": "GENERATE_IMAGE_EMBEDDING",
        "modelProfile": "SIGLIP_BASELINE_V1",
        "imagePath": "/imgs/A1.png",
        "inputChecksum": "abc123checksum",
        "symbolCode": "A1",
        "languageId": 1,
        "metadata": {
            "sourceType": "API",
        },
    }


def test_should_process_image_embedding_request() -> None:
    response = client.post(
        "/v1/process",
        json=valid_request(),
    )

    assert response.status_code == 200, response.json()

    body = response.json()

    assert body["contractVersion"] == "1.0"
    assert body["jobId"] == 25
    assert body["symbolId"] == 42
    assert body["status"] == "COMPLETED", body
    assert body["errorMessage"] is None

    assert len(body["modelResults"]) == 1

    result = body["modelResults"][0]

    assert result["modelName"] == "siglip2"
    assert result["modelVersion"] == "mock-v1"
    assert result["embeddingDimension"] == 8
    assert len(result["embedding"]) == 8
    assert result["preprocessing"]["mock"] is True


def test_should_return_repeatable_embedding() -> None:
    first = client.post(
        "/v1/process",
        json=valid_request(),
    )

    second = client.post(
        "/v1/process",
        json=valid_request(),
    )

    assert first.json()["modelResults"][0]["embedding"] \
    == second.json()["modelResults"][0]["embedding"]


def test_should_report_unknown_model_profile() -> None:
    request = valid_request()
    request["modelProfile"] = "UNKNOWN_PROFILE"

    response = client.post(
        "/v1/process",
        json=request,
    )

    assert response.status_code == 200, response.json()
    assert response.json()["status"] == "FAILED"
    assert "Unknown model profile" in response.json()["errorMessage"]


def test_should_report_unsupported_contract_version() -> None:
    request = valid_request()
    request["contractVersion"] = "99.0"

    response = client.post(
        "/v1/process",
        json=request,
    )

    assert response.status_code == 200, response.json()
    assert response.json()["status"] == "FAILED"

    assert ("Unsupported ML contract version" in response.json()["errorMessage"])


def test_should_reject_invalid_request_shape() -> None:
    request = valid_request()
    del request["jobId"]

    response = client.post(
        "/v1/process",
        json=request,
    )

    assert response.status_code == 422
