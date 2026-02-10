// noinspection HttpUrlsUsage

import { IIngress } from 'kubernetes-models/networking.k8s.io/v1';
import { HostSummary, useSyncHostMutation } from '../../../apis/host';
import { CUEnv } from '../../../apis/env';
import {
  useEditIngressMutation,
  useIngressesQuery,
  useRemoveIngressMutation,
} from '../../../apis/kubernetes/ingress';
import { useEffect, useMemo, useState } from 'react';
import _ from 'lodash';
import { useEnvContext } from '../../../layouts/EnvLayout';
import {
  Alert,
  App,
  Button,
  Collapse,
  Empty,
  Popconfirm,
  Skeleton,
  Typography,
} from 'antd';
import { ProCard, ProList } from '@ant-design/pro-components';
import { IngressPath, ingressPathKey, toHttpPaths } from './df';
import Backend from './Backend';
import IngressAnnotation from './IngressAnnotation';
import PathEditor from './PathEditor';
import {
  DeleteOutlined,
  EditOutlined,
  LinkOutlined,
  PlusOutlined,
} from '@ant-design/icons';
import yamlGenerator from '../../../apis/kubernetes/yamlGenerator';
import PreAuthorize from '../../../tor/PreAuthorize';

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
  reason?: string;
  /**
   * 所有域名
   */
  allHostNames: string[] | undefined;
  /**
   * 有证书的
   */
  hostNamesWithTl: string[] | undefined;
} {
  const { data: ingresses } = useIngressesQuery(env);
  const [api] = useSyncHostMutation();
  const [reason, setReason] = useState<string>();
  const mResult = useMemo(() => {
    setReason(undefined);
    try {
      return distinct(ingresses);
      // @ts-ignore
    } catch (e: Error) {
      setReason(e.message);
      return undefined;
    }
  }, [ingresses]);
  // 不止是支持 没有签证的，也支持没有 tls的
  // const { allHost, hostsWithTls:hostList }
  const hostList = mResult?.hostsSyncWithServer;
  // console.debug('hostsWithTls:', hostList);
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
    allHostNames: mResult?.allHostNames,
    hostNamesWithTl: mResult?.hostNamesWithTl,
  };
}

function distinct(ingresses: IIngress[] | undefined) {
  // 这里找到一个处理口子
  // 形成 hostname->pair[issuerName,secretName]
  if (!ingresses) return undefined;
  const hostSummaries = ingresses.map(toHostSummary);
  const allHosts = hostSummaries;

  const grouped = _.groupBy(hostSummaries, (it) => it.hostname);
  const hostsSyncWithServer = _.keys(grouped).map((name) => {
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
  return {
    allHostNames: _.uniq(allHosts.map((it) => it.hostname)),
    hostNamesWithTl: _.uniq(
      allHosts.filter((it) => it.secretName).map((it) => it.hostname)
    ),
    hostsSyncWithServer: hostsSyncWithServer,
  };
}

export default () => {
  const { data } = useEnvContext();
  const { message } = App.useApp();
  const [editApi] = useEditIngressMutation();
  const [removeApi] = useRemoveIngressMutation();
  const { ingresses, reason, allHostNames, hostNamesWithTl } =
    useIngresses(data);
  return (
    <>
      {reason && <Alert type={'error'} message={reason} />}
      <ProCard
        collapsible
        defaultCollapsed
        title={'Ingress'}
        loading={!ingresses}
        extra={
          <PreAuthorize haveAnyRole={['ingress', 'root']}>
            <PathEditor
              key={'create'}
              title={'新增路径'}
              trigger={
                <Button title={'点击新增路径'}>
                  <PlusOutlined />
                </Button>
              }
            />
          </PreAuthorize>
        }
      >
        {(!ingresses || !allHostNames) && <Skeleton />}
        {ingresses && allHostNames && allHostNames.length == 0 && <Empty />}
        {ingresses && allHostNames && allHostNames.length > 0 && (
          <Collapse
            items={allHostNames.map((hostName) => {
              const listData = ingresses
                .filter((it) =>
                  it.spec?.rules?.some((that) => that.host == hostName)
                )
                .flatMap(toHttpPaths); // 把不同 path的平铺开
              const tlsHost = hostNamesWithTl?.includes(hostName);

              return {
                key: hostName,
                label: (
                  <Typography>
                    <Typography.Text copyable>{hostName}</Typography.Text>
                    {/*{看看没有证书}*/}
                    {(tlsHost && (
                      <Typography.Link
                        target={'_blank'}
                        href={`https://${hostName}`}
                      >
                        <LinkOutlined />
                      </Typography.Link>
                    )) || (
                      <Typography.Link
                        target={'_blank'}
                        href={`http://${hostName}`}
                      >
                        <LinkOutlined />
                      </Typography.Link>
                    )}
                  </Typography>
                ),
                children: (
                  <ProList<IngressPath>
                    dataSource={listData}
                    rowKey={ingressPathKey}
                    // expandable={{
                    //   rowExpandable: () => true,
                    // }}
                    metas={{
                      title: {
                        dataIndex: ['path', 'path'],
                      },
                      subTitle: {
                        dataIndex: ['path', 'pathType'],
                      },
                      description: {
                        render: (_, e) => (
                          <IngressAnnotation data={e.instance} />
                        ),
                      },
                      content: {
                        render: (_, e) => <Backend data={e} />,
                      },
                      actions: {
                        render: (_, e) => [
                          <PreAuthorize
                            key={'edit'}
                            haveAnyRole={['ingress', 'root']}
                          >
                            <PathEditor
                              data={e}
                              title={'编辑路径'}
                              trigger={
                                <Button>
                                  <EditOutlined />
                                </Button>
                              }
                            />
                          </PreAuthorize>,
                          <PreAuthorize
                            key={'delete2'}
                            haveAnyRole={['ingress', 'root']}
                          >
                            <Popconfirm
                              title={'确认要删除这条路由规则么'}
                              onConfirm={async () => {
                                const yaml = yamlGenerator.deleteIngress(
                                  e,
                                  data
                                );
                                try {
                                  if (yaml) {
                                    await editApi({
                                      namespace: data.id,
                                      name: e.instance.metadata?.name,
                                      jsonObject: yaml.toJSON(),
                                    }).unwrap();
                                  } else {
                                    await removeApi({
                                      namespace: data.id,
                                      name: e.instance.metadata?.name,
                                    }).unwrap();
                                  }
                                } catch (e) {
                                  message.error(`移除流量失败，原因:${e}`);
                                }
                              }}
                            >
                              <Button danger>
                                <DeleteOutlined />
                              </Button>
                            </Popconfirm>
                          </PreAuthorize>,
                        ],
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
