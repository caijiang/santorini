## 功能

### 简便管理ingress

通过`ktor`技术开放一个特定`namespace`的`ingress`的管理,对外输出输入只有`domain`也就是字符串。
这些`ingress`自动装备预设的`yaml`模板 (来自环境配置)。

功能开放取决于是否设置了该特定`namespace`(环境变量`EASY_DOMAIN_NAMESPACE`)。

### 定时或者生成后触发证书同步

所有`ingress`的host如果解析目标在环境变量预设的范围之内 (包括 A 记录和 CNAME记录)，证书成功生成后则会按照环境变量配置的阿里云RAM账号同步到阿里云的
ALB证书池中。

功能开放取决于是否设置了相关的阿里云RAM账号、`EASY_DOMAIN_ALIYUN_REGION` 与
`EASY_DOMAIN_ALIYUN_ALB_LISTENER_ID` —— **与`namespace`无关**，
它扫描的是 **集群内全部 namespace** 的`ingress`。

#### 范围判定怎么查 DNS

- A 记录：`InetAddress.getAllByName`（系统解析器）。
- CNAME：JDK 自带的 JNDI DNS provider 查 **第一跳**，再由 `DnsScopeMatcher` 沿链比对后缀，
  上限 8 跳并且断环。 **不要**用 `InetAddress.canonicalHostName` 当 CNAME——它拿到的是地址反解，
  没有 PTR 时直接退化成 IP 字符串（平台相关，macOS 上必错）。
- 查询走 Pod 自己的 resolver（即 CoreDNS），不硬编码公网 DNS。判定用的是 **集群内的解析视图**：
  证书本来就是给集群里的 ingress 用的，公网解析到哪不影响这个结论。

#### 接入点（公网 / VPC）

ALB 与 CAS 两个 client 都会自己挑接入点：能读到时从实例元数据拿到 `region-id` 与
`network-type`， **在 VPC 内且 region 与 `EASY_DOMAIN_ALIYUN_REGION` 一致**就走 VPC 接入点
（`alb-vpc.<region>.aliyuncs.com` / `cas-vpc.<region>.aliyuncs.com`），否则走各自的公网接入点。

"同 region"这个条件不能省：VPC 接入点是 **地域级**的，杭州的 VPC 接入点从上海的 VPC 里
根本解析不出来——选错是连不上，不是慢一点。

CDN 是特例：官方只在上海（`cn-shanghai`）、上海金融云、深圳金融云提供
`cdn.vpc-proxy.aliyuncs.com`—— **不带 region 的中央代理**；其余地域（包括杭州、北京、深圳）
根本没有 VPC 接入点，进程即使在 VPC 内也只能走 `cdn.aliyuncs.com`。
SDK 的 `endpointType=vpc` 对 CDN **不能用**：它的 endpointRule 是 `central`，
会拼出不存在的 `cdn-vpc.aliyuncs.com`，所以这里必须显式 override，且查表回退。

元数据服务（`100.100.100.200`）读不到时（本机调试、非阿里云集群、被 NetworkPolicy 挡住）
退回公网并在日志里留一条 INFO。要强制指定就设 `EASY_DOMAIN_ALIYUN_NETWORK=vpc|public`，
默认 `auto`（即探测）；配了不认识的值会直接启动失败，避免静默跑到错接入点上。

探测结果在进程内缓存，重复装配不会重复探测。

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
