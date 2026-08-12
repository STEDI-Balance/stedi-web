# STEDI Web

These commands assume that redis and kafka are running locally on ports 6379 and 9092 respectively.
You will also need to install Maven before running the project.

To start this project, run the following commands:

`mvn clean package --quiet`

`java -jar StepTimerWebsocket-1.0-SNAPSHOT.jar`

## Kubernetes Image Pull Secret

The dev and prod Kubernetes manifests expect a docker-registry secret named gcr-pull-secret in the target namespace so the cluster can pull the application image from gcr.io.

Create or rotate this secret as part of cluster bootstrap and ongoing operations whenever registry credentials or pull access change.
