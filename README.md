# PictoGlyph
PictoGlyph is an experimental platform for modelling and, eventually, helping to decode pictographic writing systems.

## Status
Work in progress - working towards MVP

## Project Details
- **Java (Spring Boot)** – core API, domain model, data ingestion
- **Python (FastAPI + ML)** – image embeddings and language prediction
- **MySQL** – storage for languages, symbols, places, trade routes, and model outputs

The long-term vision is to link symbol appearance with historical context (place, time, trade routes, goods) and use modern ML (CNNs, transformers/attention) to explore relationships between known and unknown scripts.

## MVP Goals
The initial MVP focuses on a very small, robust pipeline:

1. **Register languages and symbols**
   - Create languages with metadata (name, script, date range, notes)
   - Register symbols tied to languages, with `image_path` references

2. **Ingest images**
   - From local folders
   - From remote URLs (later)

3. **Generate simple embeddings**
   - Python service loads images, preprocesses them, and produces numeric feature vectors

4. **Get “most similar language” predictions**
   - Java calls the Python ML API
   - Python returns the top candidate languages for a symbol
   - Java stores these in `prediction` along with a `model_version`

No Docker, Kafka, or heavy infra required for the MVP – just local Java, Python, and MySQL.

## Tech Stack
**Backend (API)**
- Java 21
- Spring Boot (Web, JPA, MySQL, Jackson)
- Gradle
- MySQL 8

**ML Service**
- Python 3.11+
- FastAPI + Uvicorn
- NumPy, scikit-learn
- Pillow, OpenCV
- Later: PyTorch / transformer-based models

**Database**
- MySQL schema based on:
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

3. **MySQL**
   - Stores all domain data and prediction outputs
   - `model_version` table tracks which model produced which predictions

## Getting Started (planned workflow)
> This is a *work in progress*. Commands and paths will evolve as the codebase grows.

### 1. Clone the repo
git clone https://github.com/your-username/your-repo-name.git<br>
cd your-repo-name

### 2. Create the database
`CREATE DATABASE pictoglyph CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;`

### 3. Java backend
`cd pictoglyph-api`

#### Planned endpoints (MVP):
`POST /languages`<br>
`GET /languages/{id}`<br>
`POST /symbols`<br>
`GET /symbols/{id}`<br>
`POST /symbols/{id}/predict-language`<br>
`GET /symbols/{id}/predictions`<br>

### 4. Python ML service
`cd pictoglyph-ml`<br>
`python -m venv .venv`<br>
`.\.venv\Scripts\activate`<br>
`pip install -r requirements.txt`<br>
`uvicorn main:app --reload --port 8000`<br>

## Planned Repo Structure
.<br> 
├── pictoglyph-api/            # Java Spring Boot service<br>
│   ├── build.gradle<br>
│   ├── src/main/java/...<br>
│   └── src/main/resources/...<br>
├── pictoglyph-ml/             # Python ML service<br> 
│   ├── main.py<br>
│   ├── requirements.txt<br>
│   └── ml/...<br>
├── db/<br>
│   └── schema.sql             # MySQL <br>
├── docs/<br>
│   ├── erd/                   # ERD, UML, Design notes<br>
│   └── notes.md<br>
├── data/<br>
│   ├── images/                # Raw Symbol Images<br>
│   └── samples/               # Sample scripts/ datasets<br>
└── README.md<br>

## Future Developments
After the MVP is working end-to-end:
- Add attention/transformer-based models to capture symbol sequences and text sentiment
- Introduce Kafka for asynchronous ingestion and prediction
- Containerise services with Docker and optionally Kubernetes
- Add CI/CD with Jenkins and quality checks with SonarQube
- Integrate richer datasets (cuneiform, hieroglyphs, oracle bone scripts, etc.)
