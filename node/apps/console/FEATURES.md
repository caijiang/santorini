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
ConfigMap-SantoriniEnvSPConfig
Secret-SantoriniEnvSPConfig
