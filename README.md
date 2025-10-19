# **Web Search Engine**

A full-stack web search engine built from scratch. It allows users to crawl websites, index their content, and perform word-based searches. The project demonstrates a modern microservices architecture using Spring Boot, Kafka, Redis, and Elasticsearch, with a React frontend. The application is deployed using Docker.

## **Quick Links**

* **Live Demo**: \[INSERT YOUR LIVE DEMO URL HERE\]  
* **API Documentation (Swagger)**: \[INSERT YOUR SWAGGER URL HERE\]  
* **Backend Repository**: [elad9219](https://github.com/elad9219/searchengine)  
* **Frontend Repository**: [[elad9219](https://github.com/elad9219/searchengine)](https://github.com/elad9219/searchengine-frontend)

## **Table of Contents**

* [Features](https://www.google.com/search?q=%23features)  
* [Technologies](https://www.google.com/search?q=%23technologies)  
* [Screenshots](https://www.google.com/search?q=%23screenshots)  
* [Installation](https://www.google.com/search?q=%23installation)  
* [Usage](https://www.google.com/search?q=%23usage)  
* [Project Structure](https://www.google.com/search?q=%23project-structure)  
* [Contributing](https://www.google.com/search?q=%23contributing)  
* [License](https://www.google.com/search?q=%23license)  
* [Contact](https://www.google.com/search?q=%23contact)

## **Features**

* **Web Crawler**: A robust crawler that recursively scans websites based on user-defined parameters (URL, distance, max pages, timeout).  
* **Distributed System**: Utilizes a Kafka-based microservices architecture for efficient and scalable processing.  
* **Real-time Status**: Tracks and displays the crawl status in real-time on the frontend using Redis.  
* **Content Indexing**: Indexes crawled web page content into Elasticsearch for fast and relevant search results.  
* **Search Functionality**: Allows users to search for keywords and retrieves relevant web pages from the indexed data.  
* **User-Friendly Interface**: A responsive React frontend for managing crawls and viewing search results.  
* **Dockerized Deployment**: Packaged as a Docker image for easy deployment and portability.

## **Technologies**

* **Backend**: Java 11, Spring Boot, Maven  
* **Frontend**: React, TypeScript, Node.js  
* **Messaging**: Apache Kafka  
* **Databases**: Redis (for crawl status), Elasticsearch (for search indexing)  
* **Containerization**: Docker  
* **Documentation**: Swagger  
* **Version Control**: Git, GitHub

## **Screenshots**


### **Crawl**


<img width="2560" height="1440" alt="image" src="https://github.com/user-attachments/assets/25b0c483-625a-4ac4-b0e2-1fe69a764770" />


### **Advanced Crawl**


<img width="2560" height="1440" alt="image" src="https://github.com/user-attachments/assets/b277150c-4b4b-42d0-a20e-8968bf36ed2f" />


### **Search**


<img width="2560" height="1440" alt="image" src="https://github.com/user-attachments/assets/3dbdac2b-77a8-4499-9ba5-db216aa24489" />



\--\>

## **Installation**

### **Prerequisites**

* Java 11  
* Docker  
* Git  
* Redis, Kafka, and Elasticsearch instances (or use provided remote instances)

### **Build and Run**

1. **Clone the repositories**:  
   git clone \[INSERT YOUR GITHUB BACKEND REPO URL HERE\]  
   git clone \[INSERT YOUR GITHUB FRONTEND REPO URL HERE\]

2. Build the Docker image:  
   This project is configured to run using Docker. Simply build the image from the project's root directory.  
   docker build \--platform linux/amd64 \-t elad9219/your-project-name:tag .

3. **Run the container**:  
   docker run \-p 8080:8080 elad9219/your-project-name:tag

4. Access the application at http://localhost:8080.

## **Usage**

* **Start Crawl**: Enter a URL and crawling parameters to initiate a distributed crawl.  
* **View Status**: Monitor the crawl progress in real-time.  
* **Search**: Enter keywords to find relevant pages from the indexed content.

## **Project Structure**

### **Backend (your-backend-repo-name)**

your-backend-repo-name/  
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

### **Frontend (your-frontend-repo-name)**

your-frontend-repo-name/  
├── src/  
│   ├── components/  
│   ├── utils/  
│   │   ├── globals.ts  
│   ├── App.tsx  
├── public/  
├── package.json  
├── tsconfig.json

## **Contributing**

1. Fork the repository.  
2. Create a branch: git checkout \-b feature-name.  
3. Commit: git commit \-m 'Add feature'.  
4. Push: git push origin feature-name.  
5. Open a pull request.

## **License**

MIT License \- see LICENSE file.

## **Contact**

* **Author**: Elad Tennenboim  
* **GitHub**: [elad9219](https://github.com/elad9219)  
* **Email**: elad9219@gmail.com  
* **LinkedIn**: [https://www.linkedin.com/in/elad-tennenboim/](https://www.linkedin.com/in/elad-tennenboim/)
