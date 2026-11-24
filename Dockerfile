FROM eclipse-temurin:21-jre
EXPOSE 5000

COPY build/libs/app.jar app.jar
#COPY build/resources/main/keystore.jks .
CMD ["java", "-jar", "app.jar"]
