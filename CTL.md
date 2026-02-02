## 修复历史原因缺失的环境变量

```shell
kubectl patch deployment the-deployment \
  -n the-namespace \
  --type='strategic' \
  -p='{
    "spec": {
      "template": {
        "spec": {
          "containers": [
            {
              "name": "main",
              "env": [
                {
                  "name": "kubenamespace",
                  "valueFrom": {
                    "fieldRef": {
                      "fieldPath": "metadata.namespace"
                    }
                  }
                },
                {
                  "name": "kubename",
                  "valueFrom": {
                    "fieldRef": {
                      "fieldPath": "metadata.name"
                    }
                  }
                },
                {
                  "name": "kubeuid",
                  "valueFrom": {
                    "fieldRef": {
                      "fieldPath": "metadata.uid"
                    }
                  }
                }
              ]
            }
          ]
        }
      }
    }
  }'

```