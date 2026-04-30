FROM eclipse-temurin:11
COPY target/searchengine-*.jar /usr/src/searchengine.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/usr/src/searchengine.jar"]