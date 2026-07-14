FROM eclipse-temurin:11

WORKDIR /usr/src

COPY ca.pem ca.pem

COPY target/searchengine-*.jar searchengine.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "searchengine.jar"]