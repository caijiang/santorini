import * as React from 'react';
import { IIngress } from 'kubernetes-models/networking.k8s.io/v1';
import _ from 'lodash';
import { Space, Tag } from 'antd';

interface IngressAnnotationProps {
  data: IIngress;
}

/**
 * 不包含nginx.ingress.kubernetes.io的部分
 */
export interface NginxIngressAnnotation {
  name: string;
  value: string;
}

export function readNginxIngressAnnotations(
  data: IIngress
): NginxIngressAnnotation[] {
  const as = data.metadata?.annotations;

  if (!as) return [];
  return _.keys(as)
    .filter((it) => it.startsWith('nginx.ingress.kubernetes.io/'))
    .map((it) => ({
      name: it.substring('nginx.ingress.kubernetes.io/'.length),
      value: as[it],
    }));
}

const IngressAnnotation: React.FC<IngressAnnotationProps> = ({ data }) => {
  const list = readNginxIngressAnnotations(data);
  if (list.length == 0) return undefined;
  return (
    <Space>
      {list.map((it) => (
        <Tag key={it.name}>
          {it.name}:{it.value}
        </Tag>
      ))}
    </Space>
  );
};

export default IngressAnnotation;
