# 服务

服务的定义:

1. 是一组可以执行的容器
2. 具备特定的服务端口(集群内开放)
3. 内置了一些类型,比如 JVM 服务(有一个 init 可以很聪明地把环境变量转变成 jvm 服务友好的 JAVA_OPTS)

这样我们就可以推导出服务新增方式: 导入和草稿
id,type
自由的 yaml 编辑,执行前服务端会对此润色:

- 标准标注 主要必要引入 helm 的,santorini 管理的资源跟 helm 管理的资源无关。
- 我们把什么都在外面了，那

[放心环境变量可以直接引用 pod状态/资源](https://kubernetes.io/zh-cn/docs/reference/kubernetes-api/workload-resources/pod-v1/#环境变量)

默认容器: main

服务是有几个区域配置共同组成的:

- meta 直接从 kubernetes 元数据上
- db 保存在 santorini 数据库上
- fragment 所有的 fragment 组成一个整体的 yaml 保存在 santorini 数据库上

字段解析一下:

- id 服务 id -meta,db
- name 服务名称 -db
- 服务类型 santorini.io/service-type -meta
- 资源  `.spec.template.spec.containers[0].resources`  -fragment
- image 镜像 `.spec.template.spec.containers[0].image` -fragment
- ports 服务端口  `.spec.template.spec.containers[0].ports` -fragment
- 环境资源组 描述这个服务/Deployment/Pod 需要外部的什么(怎么提供，又怎么表达使用?)
    - mysql
    - redis
    - mq
    - 数据卷
- imagePullSecrets 从服务器拉取 secret 然后选择(只有部署到具体环境的时候 才需要选择)

