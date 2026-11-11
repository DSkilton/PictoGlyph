# PictoGlyph
PictoGlyph is an experimental platform for modelling pictographic writing systems and, eventually, supporting research into dead or undeciphered languages.

The long-term aim is to help explore how symbols, language families, places, time periods, trade routes and cultural context may relate to one another. The project is not intended to provide a simple automatic translation. Instead, it aims to support human investigation by producing evidence-based suggestions, comparisons and possible interpretations.

Good data is central to the project. Before any meaningful AI or ML work can happen, PictoGlyph needs a strong foundation for storing languages, symbols, inscriptions, places, routes, metadata and model outputs in a structured and traceable way.

## Status
**Active development — progressing towards an end-to-end research MVP.**

PictoGlyph currently includes a Java/Spring Boot API, PostgreSQL persistence, local and remote image ingestion, source mapping and validation, dataset preparation, provenance metadata, and a Python/FastAPI ML service.

Initial image embedding support uses SigLIP2. Ongoing work focuses on scaling dataset acquisition, evaluating visual similarity, incorporating contextual metadata, and conducting reproducible experiments. The system is experimental; it does not automatically decipher or translate ancient scripts.

## Project Details
- **Java (Spring Boot)** – core API, domain model, ingestion, source profiles, dataset validation, and ML job orchestration
- **Python (FastAPI + ML)** – image preprocessing and SigLIP2 embeddings; similarity retrieval and language prediction are research goals
- **PostgreSQL** – storage for languages, symbols, places, trade routes, provenance, and model outputs

The long-term vision is to link symbol appearance with historical context (place, time, trade routes, goods) and use modern ML (CNNs, transformers/attention) to explore relationships between known and unknown scripts.

The project is especially interested in whether known pictographic scripts can provide useful context for investigating symbols from unknown, damaged or poorly understood writing systems.

## MVP Goals
The initial MVP focuses on a small, robust pipeline:

1. **Register languages and symbols**
   - Create languages with metadata (name, script, date range, notes)
   - Register symbols tied to languages, with `image_path` references
   - Support symbol variants where the same symbol appears in different visual forms

2. **Ingest images**
   - Import from local folders and remote APIs
   - Discover and propose field mappings, validate source profiles, and identify records requiring review
   - Retain source information and checksums so imported material remains traceable

3. **Generate image embeddings**
   - The Python service loads and preprocesses images, then produces numeric feature vectors using SigLIP2
   - Embeddings provide a foundation for visual similarity comparisons

4. **Evaluate similarity and contextual predictions (in progress)**
   - Java coordinates ML jobs and records processing evidence
   - Real similarity ranking and candidate-language predictions remain research and implementation goals
   - Predictions will be treated as hypotheses, not final answers


## Data Ingestion and Provenance

PictoGlyph uses a metadata-first ingestion pipeline to collect research material from local files and external sources. It supports field discovery, mapping proposals, source profile validation, ingestion jobs, review of problematic records, and provenance recording.

Dataset preparation is separate from ML execution. Releasing a dataset to ML requires it to be ready and verifies that eligible, labelled sign crops have valid image checksums before queuing embedding jobs. An approved source mapping does not automatically mean an image is approved for ML.

Further automation, including LLM-assisted source integration, is being explored; it should not be confused with completed capabilities on `main`.

## Tech Stack
**Backend (API)**
- Java 21
- Spring Boot (Web, JPA, PostgreSQL, Jackson)
- Gradle
- PostgreSQL

**ML Service**
- Python 3.11+
- FastAPI + Uvicorn
- Pillow, PyTorch, and Hugging Face Transformers
- SigLIP2 image encoder (`google/siglip2-base-patch16-naflex`) using CUDA
- Additional research tooling for preprocessing, analysis, and evaluation as experiments develop

**Database**
- Domain concepts include:
  - `language`, `symbol`, inscriptions, and symbol variants
  - `place`, `port`, `lang_place`, trade routes, stops, and trade goods
  - source profiles, ingestion jobs, dataset preparation, provenance metadata
  - `model_version`, `prediction`, and ML processing jobs

## High-Level Architecture (MVP)
1. **Java API**
   - Provides domain persistence, source mapping and ingestion, dataset preparation, and ML job coordination
   - Existing endpoint examples:
     - `POST /ingestion/folders`
     - `POST /ingestion/apis`
     - `POST /ingestion/mappings/propose`
     - `POST /ingestion/api-profiles/{profileId}/approve`
     - `POST /datasets/preparations/{datasetPreparationId}/release-to-ml`

2. **Python ML Service**
   - `GET /health` – service health check
   - `POST /v1/process` – processes image embedding requests using a configured model profile
   - `POST /symbols/{symbol_id}/similar` – currently returns **mock** matches; real retrieval remains to be implemented
   - The SigLIP2 implementation uses a CUDA-capable GPU for image feature extraction

3. **PostgreSQL**
   - Stores domain records, source/ingestion information, dataset state, processing jobs, and model outputs
   - Historical and geographical metadata can be linked to languages and symbols


## Research Approach

PictoGlyph follows a Design Science Research approach, combining iterative system development with planned quantitative evaluation.

The research investigates whether combining visual representations of ancient symbols with archaeological and historical metadata improves retrieval and contextual analysis. Planned comparisons include visual-only, metadata-only, and combined approaches, using reproducible datasets, baseline methods, and suitable evaluation metrics.

Results are intended to support further expert investigation, not establish definitive meanings or origins for individual symbols.

## Getting Started (local development)
> Local development instructions. The system is still evolving, and some research features are not yet end-to-end.

### 1. Clone the repo

```bash
git clone https://github.com/DSkilton/PictoGlyph.git
cd PictoGlyph
```

### 2. Create the database

Create a PostgreSQL database named `pictoglyph`, for example:

```sql
CREATE DATABASE pictoglyph;
```

Ensure the local database credentials match the settings in `pictoglyphapi/src/main/resources/application.properties`. The checked-in credentials are development placeholders, not production defaults.

### 3. Java backend

From the repository root:

```bash
cd pictoglyphapi
./gradlew bootRun
```

Run API tests with `./gradlew test` (some integration tests require Docker/Testcontainers).

### 4. Python ML service

In a separate terminal, from the repository root (Linux/WSL):

```bash
cd pictoglyphml
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
cd service
uvicorn app.main:app --reload --port 8001
```

A compatible installation of **PyTorch with CUDA support** is also required to run the SigLIP2 embedding model. PyTorch is not currently pinned in `pictoglyphml/requirements.txt`; use the [official PyTorch installation guide](https://pytorch.org/get-started/locally/) for the machine's environment. The ML health endpoint is `http://localhost:8001/health`.

## Repository Structure
Key service paths (not an exhaustive directory tree):

```text
.
├── pictoglyphapi/
│   ├── build.gradle
│   └── src/
├── pictoglyphml/
│   ├── requirements.txt
│   └── service/
│       └── app/
└── README.md
```

## Future Developments
After the research MVP is working end-to-end:

- Scale dataset acquisition and improve field mapping automation, source validation, and labelled sign-crop quality
- Establish real visual-similarity retrieval and evaluate SigLIP2 against suitable baselines
- Compare visual-only, metadata-only, and combined approaches, including chronology, location, and provenance
- Improve explainability, reproducibility, uncertainty reporting, and expert review workflows
- Explore PCA, t-SNE, and UMAP to visualise embedding spaces and investigate possible clusters
- Investigate sequence modelling, attention/transformers, symbol variants, and multiple possible interpretations
- Integrate richer datasets, including cuneiform, Egyptian hieroglyphs, Maya glyphs, oracle bone script, and other relevant writing systems
- Consider Kafka, Docker/Kubernetes, CI/CD, and additional quality tooling where they help reliability or scale
