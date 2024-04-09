#### Create namespace
```shell
kubectl create namespace fin-processor
```
#### Delete namespace
```shell
kubectl delete namespace fin-processor
```
### Install chart
```shell
helm install fin-processor ./fin-processor \
  -n fin-processor \
  --values ./fin-processor/values.yaml
```
### Show manifest
```shell
helm get manifest -n fin-processor fin-processor
```
### Verify installation
```shell
kubectl get all -n fin-processor
```
### Get pod logs
```shell
kubectl logs -n fin-processor fin-processor-<ID>
```
### Describe pod
```shell
kubectl describe -n fin-processor pod/fin-processor-<ID>
```
### Get pod events
```shell
kubectl events -n fin-processor fin-processor-<ID>
```
### Verify external access
```shell
nc -vz $(minikube ip) 8080
```
### Get Persistent Volumes
```shell
kubectl get pvc -n fin-processor
```
### Stop finance processor
```shell
kubectl -n fin-processor scale rs fin-processor-<ID> --replicas 0
```
### Uninstall chart
```shell
helm uninstall -n fin-processor fin-processor
```
