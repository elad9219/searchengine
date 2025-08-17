Web Search EngineA full-stack web search engine built from scratch. It allows users to crawl websites, index their content, and perform word-based searches. The project demonstrates a modern microservices architecture using Spring Boot, Kafka, Redis, and Elasticsearch, with a React frontend. The application is deployed using Docker.Quick LinksLive Demo: [INSERT YOUR LIVE DEMO URL HERE]API Documentation (Swagger): [INSERT YOUR SWAGGER URL HERE]Backend Repository: [INSERT YOUR GITHUB REPO URL HERE]Frontend Repository: [INSERT YOUR GITHUB REPO URL HERE]Table of ContentsFeaturesTechnologiesScreenshotsInstallationUsageProject StructureContributingLicenseContactFeaturesWeb Crawler: A robust crawler that recursively scans websites based on user-defined parameters (URL, distance, max pages, timeout).Distributed System: Utilizes a Kafka-based microservices architecture for efficient and scalable processing.Real-time Status: Tracks and displays the crawl status in real-time on the frontend using Redis.Content Indexing: Indexes crawled web page content into Elasticsearch for fast and relevant search results.Search Functionality: Allows users to search for keywords and retrieves relevant web pages from the indexed data.User-Friendly Interface: A responsive React frontend for managing crawls and viewing search results.Dockerized Deployment: Packaged as a Docker image for easy deployment and portability.TechnologiesBackend: Java 11, Spring Boot, MavenFrontend: React, TypeScript, Node.jsMessaging: Apache KafkaDatabases: Redis (for crawl status), Elasticsearch (for search indexing)Containerization: DockerDocumentation: SwaggerVersion Control: Git, GitHubScreenshots<!-- Add your screenshots here. Example:Search PageResults Page-->InstallationPrerequisitesJava 11DockerGitRedis, Kafka, and Elasticsearch instances (or use provided remote instances)Build and RunClone the repositories:git clone [INSERT YOUR GITHUB BACKEND REPO URL HERE]
git clone [INSERT YOUR GITHUB FRONTEND REPO URL HERE]
Build the Docker image:This project is configured to run using Docker. Simply build the image from the project's root directory.docker build --platform linux/amd64 -t elad9219/your-project-name:tag .
Run the container:docker run -p 8080:8080 elad9219/your-project-name:tag
Access the application at http://localhost:8080.UsageStart Crawl: Enter a URL and crawling parameters to initiate a distributed crawl.View Status: Monitor the crawl progress in real-time.Search: Enter keywords to find relevant pages from the indexed content.Project StructureBackend (your-backend-repo-name)your-backend-repo-name/
├── src/
│   ├── main/
│   │   ├── java/com/handson/searchengine/
│   │   │   ├── crawler/
│   │   │   ├── kafka/
│   │   │   ├── model/
│   │   │   ├── util/
│   │   ├── resources/
│   │   │   ├── static/
│   │   │   ├── application.properties
├── pom.xml
├── Dockerfile
Frontend (your-frontend-repo-name)your-frontend-repo-name/
├── src/
│   ├── components/
│   ├── utils/
│   │   ├── globals.ts
│   ├── App.tsx
├── public/
├── package.json
├── tsconfig.json
ContributingFork the repository.Create a branch: git checkout -b feature-name.Commit: git commit -m 'Add feature'.Push: git push origin feature-name.Open a pull request.LicenseMIT License - see LICENSE file.ContactAuthor: Elad TennenboimGitHub: elad9219Email: elad9219@gmail.comLinkedIn: https://www.linkedin.com/in/elad-tennenboim/
