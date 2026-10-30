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

## Simulator (demo data)

STEDI includes a simulator that creates test customers and generates balance-test data for them, so you can see data flow without real users or devices. It is off by default, and the server must opt in before anyone can start it: set `ENABLE_SIMULATION_MODE=true` to allow starting it from the timer page or the API, or `SIMULATION=true` to start it automatically when STEDI starts (which also allows the timer page and the API).

> **Using an older copy of STEDI?** The startup flag, the timer page toggle, signup events, and `KAFKA_STEDI_EVENTS_TOPIC` were added in October 2026. If you copied this repository before then, download it again and rebuild your image.

### Turn it on at startup

Set these environment variables on the STEDI container (in Kubernetes, add them under `env:` in your deployment manifest, commit, and deploy):

| Variable | Default | What it does |
| --- | --- | --- |
| `SIMULATION` | off | `true` starts the simulator when STEDI starts, and allows starting it again from the timer page or the API. (`-Dsimulation=true` on the `java` command line does the same.) |
| `ENABLE_SIMULATION_MODE` | `false` | `true` allows starting the simulator from the timer page or the API without starting it at startup. The dev and prod manifests set this to `false`. |
| `SIMULATION_CUSTOMERS` | `30` | How many test customers to create (the simulator creates one fewer than this number). |
| `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD`, `REDIS_DB` | | The Redis where customers, signups, and tests are written. |
| `KAFKA_BROKER` | `localhost:9092` | Kafka bootstrap server for risk events, for example `kafka.<your-namespace>.svc.cluster.local:9092`. |
| `KAFKA_STEDI_EVENTS_TOPIC` | `stedi-events` | The Kafka topic risk events are published to. Create the topic first. |

For example, in a Kubernetes deployment manifest:

```yaml
          env:
            - name: SIMULATION
              value: "true"
            - name: KAFKA_BROKER
              value: "kafka.<your-namespace>.svc.cluster.local:9092"
            - name: KAFKA_STEDI_EVENTS_TOPIC
              value: "<your-topic>"
```

### Start and stop it while STEDI is running

On the timer page (`/timer.html`), use the **Activate Simulated User Data** toggle in the top-right corner. If the server has not opted in, the toggle switches back off and shows "Simulation mode is disabled on this server."

Or use the API:

```bash
curl -X POST   http://<stedi-host>:4567/simulation   # start (30 customers); 403 unless the server opted in
curl           http://<stedi-host>:4567/simulation   # true while it is running
curl -X DELETE http://<stedi-host>:4567/simulation   # stop
```

Stopping takes effect when the simulator finishes its current pass through the customers, which can take up to about a minute; until then `GET /simulation` still returns `true`.

In Kubernetes, run these from inside the cluster or after `kubectl port-forward deployment/stedi 4567:4567 -n <your-namespace>` (then use `localhost` as the host, and open `http://localhost:4567/timer.html` for the toggle).

### What it produces

1. **Signup events in Redis**, one per test customer, written once when the simulation starts. The key is `signup:<email>` and the value is JSON:

   ```json
   {"user_id":"Edward.Jones.3@test.com","customer_name":"Edward Jones","email":"Edward.Jones.3@test.com","phone":"+447400123003","region":"GB","country_dial_code":"44","birth_year":1962,"signup_time":1793331495739}
   ```

   Customers come from 15 countries (US, MX, BR, GB, FR, DE, ES, IN, JP, KR, PH, NG, KE, ZA, EG). `country_dial_code` matches the `Dial` column of the [country-codes dataset](https://github.com/datasets/country-codes), so signups can be joined to country data.

2. **Risk events in Kafka.** Every 2 seconds the simulator records a balance test for the next customer, scores that customer's fall risk, and publishes the score to `KAFKA_STEDI_EVENTS_TOPIC`. With 30 customers, each customer gets a new score about once a minute:

   ```json
   {"customer":"Sarah.Jones.0@test.com","score":-16.0,"riskDate":"2026-10-30T03:48:01.066Z","birthYear":1965}
   ```

   A negative score means the customer's balance got worse; a positive score means it improved.

The simulator does not need Kafka to write signup events. Risk events need Kafka and a Redis that includes the RedisJSON module: use `redis:8` or `redis/redis-stack-server`. On plain `redis:7`, signups are still written, but scoring fails with `ERR unknown command 'JSON.GET'` and no risk events are sent.

Test customers keep the same names, emails, and phone numbers every run, so restarting the simulator reuses them instead of creating duplicates.

### Check that it is working

```bash
# signup events
kubectl exec -it <redis-pod> -n <your-namespace> -- redis-cli --scan --pattern 'signup:*'
kubectl exec -it <redis-pod> -n <your-namespace> -- redis-cli get 'signup:Sarah.Jones.0@test.com'

# risk events
kubectl exec -it kafka-controller-0 -n <your-namespace> -- \
  kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic <your-topic> --from-beginning

# STEDI logs: "Starting simulation at startup" and "Sending to Kafka Topic: <your-topic>"
kubectl logs deployment/stedi -n <your-namespace>
```

Turn the simulator off when you are not using it, because it keeps generating data: use the timer page toggle, call `curl -X DELETE .../simulation`, or remove `SIMULATION` (or set it to `false`) and redeploy.

## Kubernetes Image Pull Authentication

The dev and prod Kubernetes manifests use IAM-based image pull authentication (node identity) for `gcr.io` images.

Do not depend on short-lived docker-registry access-token secrets for ongoing pulls.

## Why Pulls Fail And How We Prevent Repeats

Recent pull failures typically happen when one of these drifts from the repo state or from the cluster state:

- An expiring docker-registry token secret was used for image pulls
- The deployment no longer runs with the expected service account
- Registry IAM no longer grants pull access to the identity used to fetch images

Durable protections now in this repo:

- `app.yaml` and `prod.yaml` keep explicit `serviceAccountName` wiring and avoid `imagePullSecrets`
- `.github/workflows/k8s-manifest-guardrails.yml` fails PRs if pull-secret dependency is reintroduced or HPA targets are removed

Operational checks before/after deploy:

- Verify deployment references: `kubectl get deploy stedi stedi-prod -o yaml | grep -E "serviceAccountName|imagePullSecrets"`
- Verify IAM bindings for image pull identities include `roles/storage.objectViewer` (or equivalent)
- If image pulls fail, inspect events immediately: `kubectl describe pod <pod-name>`
