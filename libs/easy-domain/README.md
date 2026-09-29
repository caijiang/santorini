## 功能

### 简便管理ingress

通过`ktor`技术开放一个特定`namespace`的`ingress`的管理,对外输出输入只有`domain`也就是字符串。
这些`ingress`自动装备预设的`yaml`模板 (来自环境配置)。

功能开放取决于是否设置了该特定`namespace`(环境变量`EASY_DOMAIN_NAMESPACE`)。

### 定时或者生成后触发证书同步

所有`ingress`的host如果解析目标在环境变量预设的范围之内 (包括 A 记录和 CNAME记录)，证书成功生成后则会按照环境变量配置的阿里云RAM账号同步到阿里云的
ALB证书池中。

功能开放取决于是否设置了相关的阿里云RAM账号以及`endpoint`—— **与`namespace`无关**，
它扫描的是 **集群内全部 namespace** 的`ingress`。

#### 触发时机

同步由 **宿主应用**驱动，不由本模块自动触发，也不由"新增域名"触发：

- 证书是 cert-manager 异步签发的，"用户刚新增域名"的那一刻证书还不存在；
- K8s 侧没有便宜的"某张证书刚签发完成"订阅点——唯一能观测 `tls.crt` 变化的办法是
  集群级 watch 全部 `Secret`，为此扩张 RBAC 不划算。

因此建议在调度任务里周期性调用（例如每 5 分钟）：

```kotlin
val certSync = certSyncService(config, kubernetesClientService)   // 装配一次，复用
// 调度任务
runCatching { certSync?.syncEligibleCerts() }
```

同步是 **幂等**的：以证书 SHA-256 指纹与 CAS 侧比对，同一张证书只上传一次，
重复调用只是多一次`ingress`列举与若干次比对。

#### 权限与冲突

- 需要 **集群级** `ingress` 与 `secret` 读取权限（跨 namespace 扫描）。
- 同一`hostname`若在多个`namespace`都有入口，只同步其中一个（按 namespace、secretName 排序取首个）
  并打出 WARN：一个域名在集群里只会被一个`ingress`生效，往同一个域名上传两张不同证书
  只会让 ALB 侧变成随机命中。
