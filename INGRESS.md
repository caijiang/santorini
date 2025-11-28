1. 逐个 ingress 管理, 其中有一个 label 表示所属 host,允许我们统一修改（域名同一管理)
2. 逐个管理的好处 在于我们甚至可以将来将 ingress 作为工单进行管理
3. ingress 是属于环境的;
4. 可以从 namespace 中获取缺省 default-cluster-issuer; 高级编辑模式可以直接编辑
5. 必须支持导出导入