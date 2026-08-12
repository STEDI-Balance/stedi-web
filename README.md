# STEDI Web

## Infrastructure Change Policy

All infrastructure changes must be made by updating version-controlled artifacts in this repository and pushing those changes to GitHub.

Do not make manual infrastructure changes directly in cloud consoles or through ad hoc CLI commands. This includes Kubernetes resources, build and deploy configuration, networking, certificates, and other environment-level configuration.

Infrastructure changes must be represented in repository artifacts such as Kubernetes manifests, Cloud Build configuration, Docker configuration, and related deployment files before they are applied.

If an emergency manual change is unavoidable, treat it as break-glass only and reconcile the change back into this repository immediately in the next commit.

These commands assume that redis and kafka are running locally on ports 6379 and 9092 respectively.
You will also need to install Maven before running the project.

To start this project, run the following commands:

`mvn clean package --quiet`

`java -jar StepTimerWebsocket-1.0-SNAPSHOT.jar`
