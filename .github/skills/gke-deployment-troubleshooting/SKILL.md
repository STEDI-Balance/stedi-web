---
name: gke-deployment-troubleshooting
description: 'Troubleshoot and monitor GKE pods and deployments with gcloud and kubectl. Use when asked to fix deployment issues, inspect pods, check rollout health, investigate failing deployments, review cluster events, or verify behavior after pull request changes or other deployment-affecting changes.'
argument-hint: 'Describe the deployment issue, environment, or rollout to inspect'
---

# GKE Deployment Troubleshooting

Use this skill to inspect, monitor, and troubleshoot Kubernetes deployments for this repository with `gcloud` and `kubectl`.

## When to Use

- A pull request changes deployment manifests, Cloud Build files, Docker configuration, or runtime configuration.
- A deployment, rollout, pod, ingress, service, or certificate appears unhealthy.
- The user asks to fix or troubleshoot deployment issues.
- You need to confirm whether a repository change reached the cluster and whether the workload stabilized.

## Required Guardrails

- Treat repository artifacts as the source of truth for infrastructure changes.
- Use `gcloud` and `kubectl` for observation, diagnosis, and monitoring by default.
- If a durable fix is required, make the change in repository artifacts and push it to GitHub instead of relying on an untracked live change.
- If an emergency break-glass live change is explicitly required, minimize scope and record the equivalent repository change immediately afterward.

## Repository-Specific Starting Points

- Start from deployment artifacts such as `app.yaml`, `prod.yaml`, `cloudbuild.yaml`, and `cloudbuild-prod.yaml`.
- Infer the target cluster, location, namespace, deployment name, service name, and image policy from those artifacts before running commands.
- In this repository, Cloud Build currently points at the `autopilot-cluster-1` cluster in `us-central1`.

## Procedure

1. Identify the target environment and the artifact change that likely affected deployment behavior.
2. Review the relevant repository artifacts first so live inspection is anchored to the intended configuration.
3. Use `gcloud` to verify the active project, cluster, and credentials.
4. Use `kubectl` to inspect rollout status, pod health, events, logs, services, ingress, and related resources.
5. Distinguish between application failure, image/build failure, configuration drift, scheduling/resource failure, and networking or certificate failure.
6. If the issue can be fixed durably, change the repository artifact, explain the operational impact, and validate with focused monitoring commands.
7. After a pull request or deployment-affecting change, monitor rollout completion and pod stabilization until the system is healthy or the next blocking failure is identified.

## Checks to Run

- Confirm the current kubectl context and namespace.
- Check deployment status and rollout history.
- Inspect pods, restarts, readiness, and recent events.
- Read logs from the failing container or the newest unhealthy pod.
- Verify service, ingress, load balancer, and certificate state when traffic is affected.
- Compare live image, environment, and replica state against repository artifacts.

See [command reference](./references/command-reference.md) for a concrete checklist.

## Output Expectations

- State the failing symptom.
- State the most likely cause with evidence from commands or artifacts.
- State whether the fix is observational only, a repository change, or an explicitly approved break-glass action.
- If you changed repository artifacts, name the files and the validation commands used afterward.