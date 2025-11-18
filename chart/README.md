## 开发，构建和发布

安装插件支持

```shell
helm plugin install https://github.com/helm-unittest/helm-unittest.git
```

```
helm unittest .
```

## 使用方向

是整体集群安装的。所以 namespace 需要独立。

- 自动证书签发
- 为各个常用站点添加 ingress-nginx 访问入口。
- 内置 santorini, santorini 服务端需要 mysql 可选择
    1. (不设置 santorini databasename)使用内置 mysql,自动创建 santorini 数据库
    2. 反之那就是使用已存在的数据库

## 其他

毕竟首次开发 chart 给整个流程定义一下几个阶段:

### print

打印阶段

通过dry-run 进行 install

### preview

预览阶段

通过单元测试生成的模板，直接应用在集群中，然后确认功能性。

### staging

实际安装 chart 实施校验。

