FROM maven:3.6.3-adoptopenjdk-14 AS MAVEN_BUILD

WORKDIR /opt/stedi

COPY . ./

RUN mvn clean package

FROM adoptopenjdk/openjdk14

COPY --from=MAVEN_BUILD /opt/stedi/target/StepTimerWebsocket-1.0-SNAPSHOT.jar /stedi.jar

ENTRYPOINT ["java", "-jar", "/stedi.jar"]

EXPOSE 4567

