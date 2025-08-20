FROM openjdk:11-slim


COPY target/searchengine-*.jar /usr/src/searchengine.jar

COPY src/main/resources/application.properties /opt/conf/application.properties

EXPOSE 8080

CMD ["java", "-jar", "/usr/src/searchengine.jar", "--spring.config.location=file:/opt/conf/application.properties"]
