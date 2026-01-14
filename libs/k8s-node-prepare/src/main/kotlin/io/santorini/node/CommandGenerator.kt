package io.santorini.node

/**
 * 默认阿里云设备
 * ```shell
 * kubectl -n kube-system rollout restart deployment coredns
 * kubectl -n kube-system rollout restart daemonset loongcollector-ds
 * # 看看还有其他什么 daemonset
 * kubectl get daemonset -A
 * ```
 *
 * @author CJ
 */
class CommandGenerator(
    /**
     * 加入集群的指令
     */
    private val joinCommand: String,
    private val privateImageRegistry: PrivateImageRegistry? = null,
    private val sandboxImage: String? = privateImageRegistry?.toSandboxImage(),
    private val containerVersion: String = "1.6.32",
    private val kubernetesVersion: String = "1.34.3",
    private val aliyunVpc: Boolean = true,
) {

    init {
        if (!kubernetesVersion.contains("1.34")) {
            throw IllegalArgumentException("kubernetesVersion $kubernetesVersion is not supported")
        }
    }

    // 阶段，一批指令
    fun generateCommands(): List<Phase> {
        @Suppress("HttpUrlsUsage")
        val aliyunMirrorSite = if (aliyunVpc) "http://mirrors.cloud.aliyuncs.com" else "https://mirrors.aliyun.com"
        val containerConfigLines = listOf(
            "disabled_plugins = []",
            "version = 2",
            "[plugins.\"io.containerd.grpc.v1.cri\".registry]",
            "   config_path = \"/etc/containerd/certs.d\"",
        ) + (sandboxImage?.let { listOf("[plugins.\"io.containerd.grpc.v1.cri\"]", "  sandbox_image = \"$it\"") }
            ?: emptyList())

        return listOfNotNull(
            Phase(
                "初始化",
                listOf(
                    Command("swapoff -a"),
                    Command("sed -i '/ swap / s/^/#/' /etc/fstab"),
                    Command("modprobe overlay"),
                    Command("modprobe br_netfilter"),
                    Command(
                        "cat <<EOF | tee /etc/sysctl.d/kubernetes.conf\n" +
                                "net.bridge.bridge-nf-call-iptables = 1\n" +
                                "net.bridge.bridge-nf-call-ip6tables = 1\n" +
                                "net.ipv4.ip_forward = 1\n" +
                                "EOF"
                    ) {
                        it.executeCommand("cat /etc/sysctl.d/kubernetes.conf").exitCode == 1
                    },
                    Command("sysctl --system"),
                    Command("setenforce 0", setOf(0, 1)),
                    Command("sed -i 's/^SELINUX=enforcing\$/SELINUX=permissive/' /etc/selinux/config"),
                )
            ),
            Phase(
                "安装 Containerd",
                listOf(
                    Command("dnf config-manager --add-repo $aliyunMirrorSite/docker-ce/linux/centos/docker-ce.repo") {
                        it.executeCommand("dnf repolist|grep docker-ce-stable").exitCode == 1
                    },
                    Command("dnf install -y containerd.io-$containerVersion"),
                    Command("systemctl enable --now containerd")
                )
            ),
            Phase(
                "安装 kubernetes",
                listOf(
                    // 华为云
//                    Command(
//                        "cat <<EOF > /etc/yum.repos.d/kubernetes.repo\n" +
//                                "[kubernetes]\n" +
//                                "name=Kubernetes\n" +
//                                "baseurl=https://mirrors.huaweicloud.com/kubernetes/yum/repos/kubernetes-el7-`uname -m`\n" +
//                                "enabled=1\n" +
//                                "gpgcheck=1\n" +
//                                "repo_gpgcheck=0\n" +
//                                "gpgkey=https://mirrors.huaweicloud.com/kubernetes/yum/doc/yum-key.gpg https://mirrors.huaweicloud.com/kubernetes/yum/doc/rpm-package-key.gpg\n" +
//                                "EOF"
//                    ) {
//                        it.executeCommand("dnf repolist|grep kubernetes").exitCode == 1
//                    },
                    Command(
                        "cat <<EOF > /etc/yum.repos.d/kubernetes.repo\n" +
                                "[kubernetes]\n" +
                                "name=Kubernetes\n" +
                                "baseurl=$aliyunMirrorSite/kubernetes-new/core/stable/v1.34/rpm/\n" +
                                "enabled=1\n" +
                                "gpgcheck=0\n" +
                                "EOF"
                    ) {
                        it.executeCommand("dnf repolist|grep kubernetes").exitCode == 1
                    },
                    Command("dnf install -y kubelet-${kubernetesVersion} kubeadm-${kubernetesVersion}"),
                    Command("systemctl enable --now kubelet")
                )
            ),
            privateImageRegistry?.let { registry ->
                Phase(
                    "配置私仓",
                    listOf(
                        Command("sed -i '1i${registry.ip} ${registry.host}' /etc/hosts") {
                            it.executeCommand("cat /etc/hosts|grep ${registry.host}").exitCode == 1
                        },
                        Command("mkdir -p /etc/containerd/certs.d/${registry.host}:${registry.port}"),
                        Command("cat <<EOF > /etc/containerd/certs.d/${registry.host}:${registry.port}/ca.crt\n" + registry.caCertificate + "\nEOF") {
                            it.executeCommand("cat /etc/containerd/certs.d/${registry.host}:${registry.port}/ca.crt").exitCode == 1
                        }
                    )
                )
            },
            Phase(
                "配置 Containerd", listOf(
                    Command("cat <<EOF > /etc/containerd/config.toml\n" + containerConfigLines.joinToString("\n") + "\nEOF"),
                    Command("systemctl restart containerd")
                )
            ),
            sandboxImage?.let {
                Phase(
                    "测试 Containerd", listOf(
                        Command("crictl pull $it")
                    )
                )
            },
            Phase(
                "安全补丁", listOf(
                    Command("dnf upgrade-minimal --security -y")
                )
            ),
            Phase(
                "加入集群",
                listOf(
                    Command(joinCommand)
                )
            )
        )
    }


}