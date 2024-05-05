#### Add repository
```shell
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo update
```
#### Create namespace
```shell
kubectl create namespace observability
```
#### Delete namespace
```shell
kubectl delete namespace observability
```
### Install chart
```shell
helm install prometheus prometheus-community/prometheus \
  --namespace observability \
  --values values.yaml
```
### Upgrade chart
```shell
helm upgrade prometheus prometheus-community/prometheus \
  --namespace observability \
  --values values.yaml
```
### Show manifest
```shell
helm get manifest -n observability prometheus
```
### Verify installation
```shell
kubectl get all -n observability
```
### Get pod logs
```shell
kubectl logs -n observability pod/prometheus-server-<POD-ID>
```
### Verify external access
```shell
nc -vz $(minikube ip) 9000
```
### Scale Prometheus
```shell
replicas=0
kubectl -n observability scale deployment prometheus-server --replicas $replicas
kubectl -n observability scale deployment prometheus-kube-state-metrics --replicas $replicas
kubectl -n observability scale deployment prometheus-prometheus-pushgateway --replicas $replicas
kubectl -n observability scale sts prometheus-alertmanager --replicas $replicas
```
### Uninstall chart
```shell
helm uninstall -n observability prometheus
```
