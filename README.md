# PictoGlyph
PictoGlyph is an experimental platform for modelling pictographic writing systems and, eventually, supporting research into dead or undeciphered languages.

The long-term aim is to help explore how symbols, language families, places, time periods, trade routes and cultural context may relate to one another. The project is not intended to provide a simple automatic translation. Instead, it aims to support human investigation by producing evidence-based suggestions, comparisons and possible interpretations.

Good data is central to the project. Before any meaningful AI or ML work can happen, PictoGlyph needs a strong foundation for storing languages, symbols, inscriptions, places, routes, metadata and model outputs in a structured and traceable way.

## Status
Work in progress - working towards MVP

## Project Details
- **Java (Spring Boot)** – core API, domain model, data ingestion
- **Python (FastAPI + ML)** – image embeddings and language prediction
- **Postgres** – storage for languages, symbols, places, trade routes, and model outputs

The long-term vision is to link symbol appearance with historical context (place, time, trade routes, goods) and use modern ML (CNNs, transformers/attention) to explore relationships between known and unknown scripts.

The project is especially interested in whether known pictographic scripts can provide useful context for investigating symbols from unknown, damaged or poorly understood writing systems.

## MVP Goals
The initial MVP focuses on a very small, robust pipeline:

1. **Register languages and symbols**
   - Create languages with metadata (name, script, date range, notes)
   - Register symbols tied to languages, with `image_path` references
   - Prepare for symbol variants where the same symbol appears in different visual forms

2. **Ingest images**
   - From local folders
   - From remote URLs (later)
   - Store enough source information to make the data traceable

3. **Generate simple embeddings**
   - Python service loads images, preprocesses them, and produces numeric feature vectors
   - Embeddings allow symbols to be compared by visual similarity

4. **Get “most similar language” predictions**
   - Java calls the Python ML API
   - Python returns the top candidate languages for a symbol
   - Java stores these in `prediction` along with a `model_version`
   - Predictions are treated as hypotheses, not final answers

## Tech Stack
**Backend (API)**
- Java 21
- Spring Boot (Web, JPA, Postgres, Jackson)
- Gradle
- Postgres 16

**ML Service**
- Python 3.11+
- FastAPI + Uvicorn
- NumPy, scikit-learn
- Pillow, OpenCV
- Later: PyTorch / transformer-based models

**Database**
- Postgres schema based on:
  - `language`, `symbol`
  - `place`, `port`, `lang_place`
  - `trade_route`, `route_stop`, `trade_good`, `route_good`
  - `model_version`, `prediction`

## High-Level Architecture (MVP)
1. **Java API**
   - Exposes REST endpoints to:
     - Create/read `language`
     - Create/read `symbol`
     - Trigger language prediction for a symbol
     - View stored predictions

2. **Python ML Service**
   - Exposes REST endpoint:
     - `POST /predict/language` – given an `image_path`, returns top-k language candidates
   - Loads a saved model and embedding index on startup so no retraining on each request

3. **Postgres**
   - Stores all domain data and prediction outputs
   - `model_version` table tracks which model produced which predictions
   - Historical and geographical metadata can be linked to languages and symbols

## Getting Started (planned workflow)
> This is a *work in progress*. Commands and paths will evolve as the codebase grows.

### 1. Clone the repo
git clone https://github.com/dskilton/pictoglpyh.git<br>
cd PictoGlyph

### 2. Create the database
`CREATE DATABASE pictoglyph;`

### 3. Java backend
`cd pictoglyphapi`

#### Planned endpoints (MVP):
`POST /languages`<br>
`GET /languages/{id}`<br>
`POST /symbols`<br>
`GET /symbols/{id}`<br>
`POST /symbols/{id}/predict-language`<br>
`GET /symbols/{id}/predictions`<br>

### 4. Python ML service
`cd pictoglypmll`<br>
`python -m venv .venv`<br>
`.\.venv\Scripts\activate`<br>
`pip install -r requirements.txt`<br>
`uvicorn main:app --reload --port 8000`<br>

## Planned Repo Structure
.<br> 
├── pictoglyphapi/            # Java Spring Boot service<br>
│   ├── build.gradle<br>
│   ├── src/main/java/...<br>
│   └── src/main/resources/...<br>
├── pictoglypmll/             # Python ML service<br> 
│   ├── main.py<br>
│   ├── requirements.txt<br>
│   └── ml/...<br>
├── db/<br>
│   └── schema.sql             # Postgres <br>
├── docs/<br>
│   ├── erd/                   # ERD, UML, Design notes<br>
│   └── notes.md<br>
├── data/<br>
│   ├── images/                # Raw Symbol Images<br>
│   └── samples/               # Sample scripts/ datasets<br>
└── README.md<br>

## Future Developments
After the MVP is working end-to-end:
- Add attention/transformer-based models to explore symbol sequences, context and relationships between scripts
- - Use dimensionality reduction techniques such as PCA, t-SNE and UMAP to visualise embedding spaces and explore whether symbols, scripts or languages form meaningful clusters
- Add support for symbol variants and multiple possible interpretations
- Explore vector search for visually or semantically similar symbols
- Introduce Kafka for asynchronous ingestion and prediction
- Containerise services with Docker and optionally Kubernetes
- Add CI/CD with Jenkins and quality checks with SonarQube
- Integrate richer datasets, such as cuneiform, Egyptian hieroglyphs, Maya glyphs, oracle bone script and other relevant writing systems
