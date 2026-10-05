# Web Search Engine — Distributed Crawler

A full-stack web search engine built from scratch with Spring Boot, Kafka, Redis, Elasticsearch, and React. Users can start a crawl, monitor its progress, index discovered content, and search the indexed pages from a web interface.

## Quick Links

- **Live Project:** [esearchengine.vercel.app](https://esearchengine.vercel.app/)
- **API Documentation (Swagger):** [search.runmydocker-app.com/swagger-ui.html](https://search.runmydocker-app.com/swagger-ui.html)
- **Backend Repository:** [github.com/elad9219/searchengine](https://github.com/elad9219/searchengine)
- **Frontend Repository:** [github.com/elad9219/searchengine-frontend](https://github.com/elad9219/searchengine-frontend)

## Highlights

- **Distributed crawling:** Uses Kafka to decouple crawl processing and support asynchronous work.
- **Configurable crawler:** Accepts crawl parameters such as URL, distance, maximum pages, and timeout.
- **Real-time crawl status:** Stores and exposes crawl progress through Redis.
- **Search indexing:** Stores crawled page content in Elasticsearch for fast text retrieval.
- **Full-stack workflow:** React frontend for starting crawls, monitoring status, and searching indexed pages.
- **Containerized backend:** Docker support for portable deployment.

## Architecture

```text
React Frontend
      |
      v
Spring Boot REST API
      |
      +----> Kafka --------> Crawl Processing
      |
      +----> Redis --------> Real-Time Crawl Status
      |
      +----> Elasticsearch -> Indexed Content / Search
```

## Technologies

- **Backend:** Java 11, Spring Boot, Maven
- **Frontend:** React, TypeScript, Node.js
- **Messaging:** Apache Kafka
- **Data Stores:** Redis, Elasticsearch
- **Containerization:** Docker
- **API Documentation:** Swagger
- **Version Control:** Git, GitHub

## Screenshots

### Crawl

<img width="2560" height="1440" alt="Crawler screen" src="https://github.com/user-attachments/assets/25b0c483-625a-4ac4-b0e2-1fe69a764770" />

### Advanced Crawl

<img width="2560" height="1440" alt="Advanced crawler screen" src="https://github.com/user-attachments/assets/b277150c-4b4b-42d0-a20e-8968bf36ed2f" />

### Search

<img width="2560" height="1440" alt="Search results screen" src="https://github.com/user-attachments/assets/3dbdac2b-77a8-4499-9ba5-db216aa24489" />

## Usage

- **Start Crawl:** Enter a URL and crawl parameters to begin indexing.
- **View Status:** Monitor crawl progress from the frontend.
- **Search:** Enter keywords to retrieve matching indexed pages.

## Local Setup

### Prerequisites

- Java 11
- Maven
- Node.js and npm
- Kafka
- Redis
- Elasticsearch
- Docker (optional)
- Git

### Backend

```bash
git clone https://github.com/elad9219/searchengine.git
cd searchengine
mvn clean install
mvn spring-boot:run
```

Configure your own Kafka, Redis, and Elasticsearch connection settings before starting the backend.

### Frontend

```bash
git clone https://github.com/elad9219/searchengine-frontend.git
cd searchengine-frontend
npm install
npm start
```

### Docker

After configuring the required external services:

```bash
docker build -t searchengine-backend .
docker run -p 8080:8080 searchengine-backend
```

## Project Structure

### Backend

```text
src/main/java/com/handson/searchengine/
├── crawler/
├── kafka/
├── model/
└── util/
```

### Frontend

```text
src/
├── components/
├── utils/
└── App.tsx
```

## License

MIT License — see the repository license file for details.

## Contact

- **Elad Tennenboim**
- **GitHub:** [elad9219](https://github.com/elad9219)
- **LinkedIn:** [linkedin.com/in/elad-tennenboim](https://www.linkedin.com/in/elad-tennenboim/)
- **Email:** elad9219@gmail.com
