// noinspection HttpUrlsUsage

import { IIngress } from 'kubernetes-models/networking.k8s.io/v1';
import { HostSummary } from '../../../apis/host';
import { CUEnv } from '../../../apis/env';
import {
  useEditIngressMutation,
  useIngressesQuery,
  useRemoveIngressMutation,
} from '../../../apis/kubernetes/ingress';
import { useMemo, useState } from 'react';
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
import { useVisitableQuery } from '../../../apis/service';

function toHostSummary(ingress: IIngress): HostSummary {
  const name = ingress.spec?.rules?.[0].host || '';
  return {
    hostname: name,
    issuerName:
      ingress.metadata?.annotations?.['cert-manager.io/cluster-issuer'],
    secretName: ingress.spec?.tls?.find((it) => it.hosts?.includes(name))
      ?.secretName,
  };
}

/**
 * 还应该做到，过滤掉当前用户不肯见的服务
 * @param env
 */
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
  const { data: ingresses0 } = useIngressesQuery(env);
  // 我需要知道我能够访问到实例
  const { data: serviceIds } = useVisitableQuery(env.id);
  const ingresses = useMemo(() => {
    if (!ingresses0) return undefined;
    if (!serviceIds) return undefined;
    return ingresses0.filter((it) =>
      it.spec?.rules?.some((r) =>
        r.http?.paths?.some((path) => {
          const name = path.backend?.service?.name;
          return name && serviceIds.includes(name);
        })
      )
    );
  }, [ingresses0, serviceIds]);
  const [reason, setReason] = useState<string>();
  const mResult = useMemo(() => {
    setReason(undefined);
    try {
      return distinct(ingresses);
      // eslint-disable-next-line
    } catch (e: any) {
      setReason(e.message);
      return undefined;
    }
  }, [ingresses]);
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
  const allHosts = ingresses.map(toHostSummary);

  return {
    allHostNames: _.uniq(allHosts.map((it) => it.hostname)),
    hostNamesWithTl: _.uniq(
      allHosts.filter((it) => it.secretName).map((it) => it.hostname)
    ),
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
        {ingresses && allHostNames && allHostNames.length === 0 && <Empty />}
        {ingresses && allHostNames && allHostNames.length > 0 && (
          <Collapse
            items={allHostNames.map((hostName) => {
              const listData = ingresses
                .filter((it) =>
                  it.spec?.rules?.some((that) => that.host === hostName)
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
