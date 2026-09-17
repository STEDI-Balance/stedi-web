# STEDI Web

These commands assume that redis and kafka are running locally on ports 6379 and 9092 respectively.
You will also need to install Maven before running the project.

To start this project, run the following commands:

`mvn clean package --quiet`

`java -jar StepTimerWebsocket-1.0-SNAPSHOT.jar`

## Simulation Mode

The timer page includes a toggle that starts and stops simulated customer data. Starting simulation is disabled by default. Set `ENABLE_SIMULATION_MODE=true` before starting the application to allow `POST /simulation` to generate events on the configured `kafka.riskTopic` (`customer-risk` by default). Redis and Kafka must be reachable for event generation.

For example:

`ENABLE_SIMULATION_MODE=true java -jar StepTimerWebsocket-1.0-SNAPSHOT.jar`
