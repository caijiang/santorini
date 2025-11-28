import { IIngress } from 'kubernetes-models/networking.k8s.io/v1';
import { HostSummary, useSyncHostMutation } from '../../../apis/host';
import { CUEnv } from '../../../apis/env';
import { useIngressesQuery } from '../../../apis/kubernetes/ingress';
import { useEffect, useMemo, useState } from 'react';
import _ from 'lodash';
import { useEnvContext } from '../../../layouts/EnvLayout';
import { Alert, Collapse, Empty, Skeleton } from 'antd';
import { ProCard, ProList } from '@ant-design/pro-components';
import { IngressPath, ingressPathKey, toHttpPaths } from './df';
import Backend from './Backend';

function toHostSummary(ingress: IIngress): HostSummary {
  const name = ingress.spec?.rules!![0].host!!;
  return {
    hostname: name,
    issuerName:
      ingress.metadata?.annotations?.['cert-manager.io/cluster-issuer'],
    secretName: ingress.spec?.tls?.find((it) => it.hosts?.includes(name))
      ?.secretName,
  };
}

// 其实通过 hook 也能做到。。。
function useIngresses(env: CUEnv): {
  ingresses: IIngress[] | undefined;
  hostList: HostSummary[] | undefined;
  reason?: string;
} {
  const { data: ingresses } = useIngressesQuery(env);
  const [api] = useSyncHostMutation();
  const [reason, setReason] = useState<string>();
  const hostList = useMemo(() => {
    setReason(undefined);
    try {
      return distinct(ingresses);
      // @ts-ignore
    } catch (e: Error) {
      setReason(e.message);
      return undefined;
    }
  }, [ingresses]);
  useEffect(() => {
    if (hostList) {
      api(hostList)
        .unwrap()
        .catch((e) => {
          console.error('e:', e);
          setReason('服务端也拒绝了');
        });
    }
  }, [hostList]);
  return {
    ingresses,
    reason,
    hostList,
  };
}

function distinct(ingresses: IIngress[] | undefined) {
  // 这里找到一个处理口子
  // 形成 hostname->pair[issuerName,secretName]
  if (!ingresses) return undefined;
  const hosts = ingresses
    .map(toHostSummary)
    .filter((it) => it.issuerName && it.secretName);

  const grouped = _.groupBy(hosts, (it) => it.hostname);
  return _.keys(grouped).map((name) => {
    const list = grouped[name];
    const list2 = _.uniqBy(list, (item) =>
      JSON.stringify([item.issuerName, item.secretName])
    );
    if (list2.length != 1) {
      throw new Error(
        name + '具备不同的多个issuerName或者secretName,需要系统管理员对此调整'
      );
    }
    return list2[0];
  });
}

export default () => {
  const { data } = useEnvContext();
  const { ingresses, reason, hostList } = useIngresses(data);
  return (
    <>
      {reason && <Alert type={'error'} message={reason} />}
      <ProCard title={'Ingress'} loading={!ingresses}>
        {(!ingresses || !hostList) && <Skeleton />}
        {ingresses && hostList && hostList.length == 0 && <Empty />}
        {ingresses && hostList && hostList.length > 0 && (
          <Collapse
            items={hostList.map((host) => {
              const listData = ingresses
                .filter((it) =>
                  it.spec?.rules?.some((that) => that.host == host.hostname)
                )
                .flatMap(toHttpPaths); // 把不同 path的平铺开

              return {
                key: host.hostname,
                label: host.hostname,
                children: (
                  <ProList<IngressPath>
                    dataSource={listData}
                    rowKey={ingressPathKey}
                    metas={{
                      title: {
                        dataIndex: ['path', 'path'],
                      },
                      subTitle: {
                        dataIndex: ['path', 'pathType'],
                      },
                      description: {
                        render: () => 'desc',
                        // dataIndex:
                      },
                      content: {
                        render: (_, e) => <Backend data={e} />,
                        // dataIndex:
                      },
                    }}
                  ></ProList>
                ),
              };
            })}
          ></Collapse>
        )}
      </ProCard>
    </>
  );
};
