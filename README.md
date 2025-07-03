# STEDI Web

These commands assume that redis and kafka are running locally on ports 6379 and 9092 respectively.
You will also need to install Maven before running the project.

To start this project, run the following commands:

`mvn clean package --quiet`

`java -jar StepTimerWebsocket-1.0-SNAPSHOT.jar`

## Getting Started

Create a `.env` file in the root directory using the provided `.env.example` as a template.

```bash
cp .env.example .env
```

Run docker-compose to start the redis and kafka containers:

```bash
docker-compose up -d
```

Use Bruno Http Client to test the API endpoints.
