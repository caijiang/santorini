kubernetes 推荐的方式并不是通过 serviceAccount 对应人类用户，而是完全将认证委托给第三方，并不会存储用户信息，这跟我们可能较为细致的权限管理冲突，所以只好用不推荐的方式进行。

https://github.com/jcmoraisjr/ingress/blob/master/docs/annotations.md?utm_source=chatgpt.com

可能可以通过定制 cookie ,然后修改反向代理 强行传递给应用。

非常棒 👍
这是一个非常实用的问题：

> 你希望 **Kubernetes Dashboard** 通过 **Dex（一个开源 OIDC 身份提供者）** 来实现登录认证。

也就是说：
✅ 用户 → Dex 登录（GitHub/LDAP/Google 等） → 获取 Token → Dashboard 用该 Token 访问 API Server。

下面我来帮你完整理一遍这个流程，附上关键 YAML 示例。

---

## 🧩 一、体系结构

```
+-------------------+
|  Human User       |
|  (Browser)        |
+--------+----------+
         |
         v
+--------+----------+
|  Kubernetes Dashboard  |
|  -- OIDC Login Flow -->|
+--------+----------+
         |
         v
+--------+----------+
|   Dex (OIDC IdP)  |
|   (GitHub, LDAP..)|
+--------+----------+
         |
         v
+--------+----------+
|  Kubernetes API Server |
|  (oidc flags enabled)  |
+------------------------+
```

> ✅ Dashboard 不直接验证用户密码，而是通过 Dex 的 OIDC 登录，Dex 再颁发 ID Token（JWT）给 Dashboard，然后 Dashboard 用它去访问
> API Server。

---

## ⚙️ 二、前置条件

1. 你有一个正常工作的 **Dex 服务**（可以是外部的或集群内的）；
2. Kubernetes API Server 启用了 OIDC 支持；
3. Dashboard 使用的 Token 登录模式被打开。

---

## 🧱 三、步骤详解

### ✅ Step 1️⃣：配置 Kubernetes API Server 支持 OIDC

在 API Server 启动参数中添加以下 flags（可以通过 kubeadm、k3s、microk8s 或手动配置）：

```bash
--oidc-issuer-url=https://dex.example.com
--oidc-client-id=kubernetes-dashboard
--oidc-username-claim=email
--oidc-groups-claim=groups
```

可选项（视 Dex 配置而定）：

```bash
--oidc-ca-file=/etc/kubernetes/pki/dex-ca.crt
```

> 这些参数告诉 API Server：
> 「任何携带来自 `dex.example.com` 的 JWT，并且 `aud` 是 `kubernetes-dashboard` 的请求，都算有效身份。」

---

### ✅ Step 2️⃣：配置 Dex

Dex 是一个通用 OIDC 提供者，它可以连接多种后端（GitHub、LDAP、SAML、静态用户等）。

一个典型 Dex 配置片段（`config.yaml`）：

```yaml
issuer: https://dex.example.com
storage:
  type: kubernetes
  config:
    inCluster: true

web:
  http: 0.0.0.0:5556

connectors:
  - type: github
    id: github
    name: GitHub
    config:
      clientID: GITHUB_CLIENT_ID
      clientSecret: GITHUB_CLIENT_SECRET
      orgs:
        - name: your-org

staticClients:
  - id: kubernetes-dashboard
    name: Kubernetes Dashboard
    redirectURIs:
      - https://dashboard.example.com/oauth2/callback
    secret: dashboard-secret
```

> Dex 会在 `/auth`、`/callback` 等端点提供标准 OIDC 流程。

---

### ✅ Step 3️⃣：部署 Dashboard 并启用 OIDC 登录

安装时启用 `--enable-insecure-login`（或者新版支持 OIDC 登录配置）：

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: kubernetes-dashboard
  namespace: kubernetes-dashboard
spec:
  template:
    spec:
      containers:
        - name: kubernetes-dashboard
          image: kubernetesui/dashboard:v3.0.0
          args:
            - --namespace=kubernetes-dashboard
            - --enable-skip-login=false
            - --enable-insecure-login=true
            - --authentication-mode=oidc
            - --oidc-issuer-url=https://dex.example.com
            - --oidc-client-id=kubernetes-dashboard
            - --oidc-ca-file=/certs/dex-ca.crt
            - --oidc-redirect-uri=https://dashboard.example.com/oauth2/callback
          volumeMounts:
            - name: dex-ca
              mountPath: /certs
      volumes:
        - name: dex-ca
          secret:
            secretName: dex-ca
```

> ⚙️ Dashboard 在启动时会显示 “Login with OIDC” 按钮。
> 点击后会跳转至 Dex 登录页面。

---

### ✅ Step 4️⃣：配置 RBAC（授权登录的用户）

Dashboard 登录后，Kubernetes 会用 Token 中的 `email` 或 `groups` 去匹配 RBAC。

例如：
如果用户邮箱是 `alice@example.com`，那么你可以创建：

```yaml
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRoleBinding
metadata:
  name: dashboard-user
subjects:
  - kind: User
    name: "alice@example.com"
    apiGroup: rbac.authorization.k8s.io
roleRef:
  kind: ClusterRole
  name: view
  apiGroup: rbac.authorization.k8s.io
```

> ✅ 这样 Dex 登录的用户就能通过 OIDC Token 在集群中执行授权操作。

---

### ✅ Step 5️⃣：访问 Dashboard

1. 浏览器打开：

   ```
   https://dashboard.example.com/
   ```
2. 点击 “Sign in with Dex”；
3. Dex 跳转到 GitHub（或 LDAP）登录；
4. 登录成功后返回 Dashboard；
5. Dashboard 获取 Dex 返回的 OIDC Token；
6. Dashboard 将该 Token 发送给 API Server；
7. API Server 验证通过，返回数据。

---

## 🧩 六、验证成功标志

登录后执行：

```bash
kubectl -n kubernetes-dashboard logs deploy/kubernetes-dashboard
```

看到类似：

```
OIDC authentication successful for user: alice@example.com
```

并且 Dashboard 页面能加载 Pods/Deployments。

---

## 🧠 七、常见坑

| 问题                           | 原因                     | 解决方案                           |
|------------------------------|------------------------|--------------------------------|
| Dashboard 报 401 Unauthorized | API Server 未启用 OIDC 参数 | 确认 `--oidc-*` 参数               |
| 登录后白屏或循环跳转                   | redirect URI 不匹配       | 确保 Dex 与 Dashboard 配置的回调地址完全一致 |
| Dex 证书不受信任                   | 缺少 CA 文件               | 配置 `--oidc-ca-file`            |
| 用户登录成功但无权限                   | 未绑定 RBAC               | 给该用户 email 绑定 ClusterRole      |

---

## ✅ 八、一句话总结

> 🧩 Dashboard + Dex + OIDC 流程：
> 1️⃣ Dex 做身份验证；
> 2️⃣ Dashboard 获取 OIDC Token；
> 3️⃣ API Server 验证该 Token；
> 4️⃣ RBAC 控制权限。

整个过程不需要 ServiceAccount，
用户使用 **Dex 身份（OIDC Claim）** 直接被 Kubernetes 授权。

---

是否希望我帮你生成一份 **完整可部署的 Dex + Dashboard + RBAC YAML 样例**？
可以一键应用到测试集群（例如 kind/k3d/k3s）验证 OIDC 登录流程。
