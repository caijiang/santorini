/kubernetes -> 移除 /kubernetes 后转发给控制台
静态资源,Sec-Fetch-Mode: navigate(存在例外),转贷给 前端 app
都转发给后端

1. 通过标准接口获取当前身份，失败时;向服务端询问登录建议
2. 通过 kubernetes 获取服务配置(其实是 deployment,statefulSet)再根据这些获取额外信息
3. 查看日志(打开 dashboard)
4. 新增服务--这个做不到,但是管理员可以配置
5. 配置角色，管理服务
6. 发布计划->版本迭代

环境配置
都是键值对。
ConfigMap-SantoriniEnvConfig
Secret-SantoriniEnvConfig

服务的定义:

1. 是一组可以执行的容器
2. 具备特定的服务端口(集群内开放)
3. 内置了一些类型,比如 JVM 服务(有一个 init 可以很聪明地把环境变量转变成 jvm 服务友好的 JAVA_OPTS)

这样我们就可以推导出服务新增方式: 导入和草稿
id,type
自由的 yaml 编辑,执行前服务端会对此润色:

- 标准标注 主要必要引入 helm 的,santorini 管理的资源跟 helm 管理的资源无关。
- 我们把什么都在外面了，那
