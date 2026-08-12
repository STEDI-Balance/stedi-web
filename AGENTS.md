# Repository Rules

## Infrastructure Changes

- Make infrastructure changes only by editing version-controlled artifacts in this repository and pushing those changes to GitHub.
- Do not make manual infrastructure changes in cloud consoles or with ad hoc CLI commands.
- Treat Kubernetes manifests, build and deploy configuration, Docker configuration, networking configuration, certificate configuration, and similar operational artifacts as the source of truth.
- If a break-glass manual change is unavoidable, record the equivalent artifact change in the repository immediately in the next commit.

## Deployment Troubleshooting

- Use the `gke-deployment-troubleshooting` skill when asked to troubleshoot or monitor pods, deployments, rollouts, or cluster health, especially after pull request changes or other deployment-affecting changes.
- Use `gcloud` and `kubectl` for inspection, monitoring, and diagnosis.
- When a fix is needed, implement the durable change through repository artifacts and GitHub rather than by making an untracked live configuration change.