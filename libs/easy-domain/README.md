## 功能

### 简便管理ingress

通过`ktor`技术开放一个特定`namespace`的`ingress`的管理,对外输出输入只有`domain`也就是字符串。
这些`ingress`自动装备预设的`yaml`模板 (来自环境配置)。

功能开放取决于是否设置了该特定`namespace`(环境变量`EASY_DOMAIN_NAMESPACE`)。

### 定时或者生成后触发证书同步

所有`ingress`的host如果解析目标在环境变量预设的范围之内 (包括 A 记录和 CNAME记录)，证书成功生成后则会按照环境变量配置的阿里云RAM账号同步到阿里云的
ALB证书池中。

功能开放取决于是否设置了相关的阿里云RAM账号以及`endpoint`。

