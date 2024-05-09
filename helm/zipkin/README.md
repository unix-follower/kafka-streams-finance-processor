#### Add repository
```shell
helm repo add zipkin https://zipkin.io/zipkin-helm
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
helm install zipkin zipkin/zipkin \
  --namespace observability \
  --values values.yaml
```
### Upgrade chart
```shell
helm upgrade zipkin zipkin-community/zipkin \
  --namespace observability \
  --values values.yaml
```
### Show manifest
```shell
helm get manifest -n observability zipkin
```
### Verify installation
```shell
kubectl get all -n observability
```
### Get pod logs
```shell
kubectl logs -n observability pod/zipkin-<POD-ID>
```
### Add external access
```shell
kubectl -n observability patch service zipkin \
  -p '{"spec": {"externalIPs": ["192.168.105.6"]}}'
```
### Verify external access
```shell
nc -vz $(minikube ip) 9411
```
### Scale zipkin
```shell
kubectl -n observability scale deployment zipkin --replicas 0
```
### Uninstall chart
```shell
helm uninstall -n observability zipkin
```
