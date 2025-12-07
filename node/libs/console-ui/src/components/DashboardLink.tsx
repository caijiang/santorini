import * as React from 'react';
import { AnchorHTMLAttributes, useEffect, useState } from 'react';
import { useKubernetesJWTTokenQuery } from '../apis/token';
import { Spin } from 'antd';
import { useDashboardHostQuery } from '../apis/misc';

interface DashboardLinkProps {
  /**
   * 到了 dashboard 以后的 path
   */
  path?: string;
}

function arrayBufferToBase64(buffer: ArrayBuffer) {
  const bytes = new Uint8Array(buffer);
  let binary = '';
  for (let i = 0; i < bytes.length; i++) {
    binary += String.fromCharCode(bytes[i]);
  }
  return btoa(binary);
}

function arrayBufferToBase64URL(buffer: ArrayBuffer) {
  return arrayBufferToBase64(buffer)
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/, '');
}

function concatUint8Arrays(arrays: Uint8Array<ArrayBuffer>[]) {
  // 计算总长度
  let totalLength = arrays.reduce((sum, arr) => sum + arr.length, 0);

  // 新建目标 Buffer
  let result = new Uint8Array(totalLength);

  // 按顺序 Copy
  let offset = 0;
  for (const arr of arrays) {
    result.set(arr, offset);
    offset += arr.length;
  }

  return result;
}

export async function encryptString(input: string) {
  const key = await crypto.subtle.importKey(
    'jwk',
    {
      key_ops: ['encrypt', 'decrypt'],
      ext: true,
      kty: 'oct',
      k: 'hbKxQGIWbR3ptS64UOSDCQMYRu8-3jJybhyZslBVQs4',
      alg: 'A256GCM',
    },
    { name: 'AES-GCM', length: 256 },
    true,
    ['encrypt', 'decrypt']
  );

  const iv = crypto.getRandomValues(new Uint8Array(12));

  const encoded = new TextEncoder().encode(input);
  const ciphertext = await crypto.subtle.encrypt(
    { name: 'AES-GCM', iv },
    key,
    encoded
  );

  const result = concatUint8Arrays([iv, new Uint8Array(ciphertext)]);
  return arrayBufferToBase64URL(result.buffer);
}

const DashboardLink: React.FC<
  DashboardLinkProps & Exclude<AnchorHTMLAttributes<HTMLAnchorElement>, 'href'>
> = ({ children, path, ...props }) => {
  const { data } = useKubernetesJWTTokenQuery(undefined);
  const { data: host } = useDashboardHostQuery(undefined);
  const [url, setUrl] = useState<string>();
  useEffect(() => {
    if (data && host) {
      const target = new URL(`https://${host}/`);
      encryptString(data).then((t) => {
        target.searchParams.set('t', t);
        target.hash = '';
        target.pathname = '/tokenForKubernetesDashboard';
        if (path) target.searchParams.set('path', path);
        setUrl(target.toString());
      });
    } else {
      setUrl(undefined);
    }
  }, [data, path, setUrl, host]);
  if (!url) return <Spin />;
  return (
    <a href={url} target={'_blank'} {...props}>
      {children}
    </a>
  );
};

export default DashboardLink;
