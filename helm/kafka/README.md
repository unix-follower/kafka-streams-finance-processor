#### Add repository
```shell
helm repo add bitnami https://charts.bitnami.com/bitnami
helm repo update
```
#### Create namespace
```shell
kubectl create namespace kafka
```
#### Delete namespace
```shell
kubectl delete namespace kafka
```
### Install chart
```shell
helm install kafka bitnami/kafka \
  --namespace kafka \
  --values values.yaml
```
### Show manifest
```shell
helm get manifest -n kafka kafka
```
### Verify installation
```shell
kubectl get all -n kafka
```
### Get pod logs
```shell
kubectl logs -n kafka kafka-controller-0
```
### Get pod events
```shell
kubectl events -n kafka kafka-controller-0
```
### Verify external access
```shell
nc -vz $(minikube ip) 9094
```
### Get Persistent Volumes
```shell
kubectl get pv -n kafka
kubectl get pvc -n kafka
```
### Stop Kafka
```shell
kubectl -n kafka scale sts kafka-controller --replicas 0
```
### Uninstall chart
```shell
helm uninstall kafka -n kafka
```
### Delete Persistent Volumes
#### Edit manifest disable protection
```shell
kubectl edit pv <NAME>
```
Erase this line.
```yaml
finalizers:
 - kubernetes.io/pv-protection
```
Or
```shell
kubectl patch pv <PV_NAME> -p '{"metadata":{"finalizers":null}}'
```
#### Delete Persistent Volumes
```shell
kubectl delete pv <PV_NAME>
```
