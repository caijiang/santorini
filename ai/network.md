Kubernetes 里**最快、最省事、零依赖**的网络发包测试方法如下。
按你的需求（TCP/UDP、DNS、HTTP、Port）我给你最精简的命令。

---

# 🚀 **1. 最快捷：用 busybox 发包（推荐）**

无需安装任何工具，K8s 几乎所有集群都有 busybox 或可以临时跑一个。

```bash
kubectl run net --rm -it --image=busybox -- sh
```

进去后：

### **测试 TCP 端口连通性**

```sh
nc -zv <IP-or-host> <port>
```

### **测试 UDP**

```sh
nc -u -zv <IP> <port>
```

### **测试 HTTP**

```sh
wget -O- http://<host>:<port>
```

### **测试 DNS 解析**

```sh
nslookup kubernetes.default
```

这是最快、最无脑、干净的网络测试方式。

---

# 🚀 **2. 最好用：`k8s-debug` 或 `praqma/network-multitool`**

如果你需要更强（curl + dig + ping + traceroute + nc）：

```bash
kubectl run nettool \
  --rm -it \
  --image=praqma/network-multitool \
  -- bash
```

然后你可以：

* `curl`
* `ping`
* `traceroute`
* `dig`
* `nc`
* `wget`

示例：

```sh
curl -v http://service:8080
dig service.default.svc.cluster.local
```

---

# 🚀 **3. 单行：直接在目标 Pod 里执行**

如果你的 Pod 已经有 shell（如 alpine、debian、distroless exclude）：

```bash
kubectl exec -it <pod> -- sh -c "nc -zv 10.0.0.5 3306"
```

---

# 🚀 **4. 临时创建 Alpine，含 curl + ping（很干净）**

```bash
kubectl run alp --rm -it --image=alpine -- sh
apk add curl
```

---

# 🚀 **5. 超简单：`kubectl debug`**

Kubernetes 1.18+：

```bash
kubectl debug -it <pod> --image=busybox --target=<container>
```

不会破坏原始容器。

---

# 📌 **如果你告诉我你想测：**

* Pod → Pod
* Pod → Service
* Pod → 外网
* NodePort
* Ingress
* ClusterIP
* 跨 namespace
* 跨节点网络问题（CNI 问题）

我可以直接给你最适合你的命令。
