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
