```shell
kubectl auth whoami --token "..." --kubeconfig=/root/.kube/config_without_token
```

```shell
curl -vk -H "Authorization: Bearer ..." https://172.16.208.142:6443/api
```