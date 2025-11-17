package preview;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.rbac.Role;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.santorini.kubernetes.Utils;

import java.util.Map;


/**
 * @author CJ
 */
@SuppressWarnings({"unused"})
public class Demos {
    public static void ccc() {
        KubernetesClient client = new KubernetesClientBuilder().build();
        System.out.println(Utils.currentPod(client));
    }

    public static void case0() {
        String roleName = "santorini-manager";
        KubernetesClient client = new KubernetesClientBuilder().build();
        System.out.println(Utils.rootOwner(Utils.currentPod(client), client));
    }

    public static void case1() {
        KubernetesClient client = new KubernetesClientBuilder().build();
        HasMetadata root = Utils.rootOwner(Utils.currentPod(client), client);
        System.out.println(Utils.findRole(root, "manager", client));
        System.out.println(Utils.findRole(root, "user", client));
        System.out.println(Utils.findRole(root, "u2", client));
    }

    public static void case2() {
        KubernetesClient client = new KubernetesClientBuilder().build();
        HasMetadata root = Utils.rootOwner(Utils.currentPod(client), client);
        Role role = Utils.findRole(root, "user", client);
        Utils.findOrCreateServiceAccountAndAssignRoles(client, root, Map.of("foo", "bar"), role);
        Utils.findOrCreateServiceAccountAndAssignRoles(client, root, Map.of("foo", "bar2"), Utils.findRole(root, "manager", client));
    }

    public static void case3() {
        KubernetesClient client = new KubernetesClientBuilder().build();
        Utils.createTokenForServiceAccount(client, "user-account79rx6", client.getNamespace())
                .join();
    }
}
