# Command Reference

Use these commands as a checklist and adapt names, namespace, and environment to the target deployment.

## Confirm Target

```sh
gcloud config list
gcloud container clusters list
gcloud container clusters get-credentials CLUSTER_NAME --region REGION --project PROJECT_ID
kubectl config current-context
kubectl get ns
```

## Rollout And Deployment Health

```sh
kubectl get deployments
kubectl describe deployment DEPLOYMENT_NAME
kubectl rollout status deployment/DEPLOYMENT_NAME
kubectl rollout history deployment/DEPLOYMENT_NAME
kubectl get rs
```

## Pod Health

```sh
kubectl get pods -o wide
kubectl describe pod POD_NAME
kubectl logs POD_NAME --all-containers --tail=200
kubectl logs POD_NAME --previous --all-containers --tail=200
kubectl top pod POD_NAME
kubectl get events --sort-by=.metadata.creationTimestamp
```

## Service And Ingress Health

```sh
kubectl get svc
kubectl describe svc SERVICE_NAME
kubectl get ingress
kubectl describe ingress INGRESS_NAME
kubectl get managedcertificate
kubectl describe managedcertificate CERT_NAME
```

## Drift Checks

```sh
kubectl get deployment DEPLOYMENT_NAME -o yaml
kubectl get pod POD_NAME -o yaml
kubectl describe node NODE_NAME
```

## Operational Guidance

- Prefer read-only commands first.
- Use live mutation only when the user explicitly requests or approves a break-glass action.
- Reconcile any live emergency action back into repository artifacts immediately after stabilization.