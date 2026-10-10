import type { DomainSyncInfo } from '../../apis/easyDomain';
import { ReactNode } from 'react';
import { Typography } from 'antd';

/**
 * 域名管理页「证书去向」说明的文案构造。
 *
 * 抽成不依赖 React / antd 的纯函数，是为了让**边界**可测：范围一个都没配时
 * 服务端不会同步任何域名（`EasyDomainConfig.dnsScopeConfigured`），这句话必须
 * 说清楚，而不是打出一对空括号。
 */

/**
 * 证书从签发到落地的链路。
 *
 * 同步未启用时**不描述链路** —— 那时链路是不存在的，写出来只会让人以为证书已经上去了。
 */
export function certPathText(info: DomainSyncInfo): ReactNode {
  if (!info.certSyncEnabled) {
    return '证书同步未启用：域名只会生成集群内的 Ingress，证书不会上传到阿里云。';
  }
  const region = info.aliyunRegion ? `（${info.aliyunRegion}）` : '';
  return (
    <Typography.Paragraph>
      域名 → Ingress → cert-manager 签发（集群
      Secret）→阿里云数字证书管理服务（CAS）证书池 → ALB 监听「
      {<Typography.Text copyable>{info.aliyunAlbListenerId}</Typography.Text>}」
      {
        <Typography.Link
          href={`https://slb.console.aliyun.com/alb/${
            info.aliyunRegion ?? 'cn-hangzhou'
          }/albs/${info.aliyunLoadBalancerId}/listeners/${
            info.aliyunAlbListenerId
          }/certs`}
        >
          证书
        </Typography.Link>
      }
      {region}
    </Typography.Paragraph>
  );
}

/**
 * 哪些域名会被同步：A 记录命中 CIDR，**或** CNAME 命中后缀（两者取或，与服务端
 * `DnsScopeMatcher.matches` 的语义一致）。
 */
export function syncScopeText(info: DomainSyncInfo): string {
  const byCidr = info.dnsCidrs.length
    ? `解析到 ${info.dnsCidrs.join('、')}`
    : '';
  const byCname = info.dnsCnameSuffixes.length
    ? `CNAME 指向 ${info.dnsCnameSuffixes.join('、')}`
    : '';

  const scopes = [byCidr, byCname].filter(Boolean);
  return scopes.length
    ? `${scopes.join('，或 ')} 的域名`
    : '未配置任何范围，当前不会有域名被同步';
}
