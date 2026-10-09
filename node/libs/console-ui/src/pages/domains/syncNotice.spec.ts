import { isValidElement, type ReactNode } from 'react';
import { describe, expect, it } from 'vitest';
import type { DomainSyncInfo } from '../../apis/easyDomain';
import { certPathText, syncScopeText } from './syncNotice';

const info = (override: Partial<DomainSyncInfo> = {}): DomainSyncInfo => ({
  dnsCidrs: [],
  dnsCnameSuffixes: [],
  aliyunRegion: null,
  aliyunAlbListenerId: null,
  aliyunLoadBalancerId: null,
  certSyncEnabled: false,
  ...override,
});

/**
 * `certPathText` 返回 ReactNode —— 未启用时是字符串，启用后是 `Typography.Paragraph`。
 *
 * 断言走"把节点树摊成文本"，而不是渲染成 DOM 再读 `textContent`：
 * 本仓的 `tsconfig.base.json` 只开了 `lib: ["es2022"]`（没有 DOM 类型），
 * 整个 workspace 的 spec 都不碰 DOM API。
 */
const textOf = (node: ReactNode): string => {
  if (typeof node === 'string' || typeof node === 'number') return String(node);
  if (Array.isArray(node)) return node.map(textOf).join('');
  if (isValidElement(node)) {
    return textOf((node.props as { children?: ReactNode }).children);
  }
  // null / undefined / boolean，以及没有文字的元素（如复制按钮里的图标）
  return '';
};

/** 节点树里第一个 href；`Typography.Link` 会渲染成 `<a href>` */
const hrefOf = (node: ReactNode): string | undefined => {
  if (Array.isArray(node)) {
    for (const child of node) {
      const href = hrefOf(child);
      if (href) return href;
    }
    return undefined;
  }
  if (isValidElement(node)) {
    const props = node.props as { href?: string; children?: ReactNode };
    return props.href ?? hrefOf(props.children);
  }
  return undefined;
};

describe('证书去向文案', () => {
  it('同步未启用时：纯文本，不画链路，也没有链接', () => {
    const node = certPathText(info());

    // 链路都没建立起来，终点就不该被包成元素渲染出来
    expect(isValidElement(node)).toBe(false);
    expect(textOf(node)).toContain('未启用');
    expect(textOf(node)).not.toContain('ALB 监听');
    expect(hrefOf(node)).toBeUndefined();
  });

  it('启用后：元素形态，给出监听与 region，并链到阿里云控制台的证书页', () => {
    const node = certPathText(
      info({
        certSyncEnabled: true,
        aliyunLoadBalancerId: 'alb-1',
        aliyunAlbListenerId: 'lsn-1',
        // 注意：URL 里那一段 region 目前是写死的 cn-hangzhou。这个 fixture 跟着写死，
        // 只是为了不把一条已知问题伪装成一条失败用例
        aliyunRegion: 'cn-hangzhou',
      })
    );

    expect(isValidElement(node)).toBe(true);

    const text = textOf(node);
    // 监听 id 外面套着 `「」`，region 跟在后面
    expect(text).toContain('ALB 监听「lsn-1」');
    expect(text).toContain('（cn-hangzhou）');
    // 链接要落在"这个负载均衡的这个监听"的证书列表页上
    expect(hrefOf(node)).toContain('/albs/alb-1/listeners/lsn-1/certs');
  });

  it('没配 region 时不渲染空括号', () => {
    const text = textOf(
      certPathText(
        info({
          certSyncEnabled: true,
          aliyunAlbListenerId: 'lsn-1',
          aliyunRegion: null,
        })
      )
    );

    expect(text).toContain('ALB 监听「lsn-1」');
    expect(text).not.toContain('（）');
  });
});

describe('同步范围文案', () => {
  it('CIDR 与 CNAME 后缀是「或」关系，两者都列出', () => {
    expect(
      syncScopeText(
        info({ dnsCidrs: ['10.0.0.0/8'], dnsCnameSuffixes: ['example.com'] })
      )
    ).toBe('解析到 10.0.0.0/8，或 CNAME 指向 example.com 的域名');
  });

  it('只配了 CIDR 时不出现空的 CNAME 片段', () => {
    expect(syncScopeText(info({ dnsCidrs: ['10.0.0.0/8'] }))).toBe(
      '解析到 10.0.0.0/8 的域名'
    );
  });

  /**
   * 范围一个都没配时服务端是**静默空转**（每个 host 都匹配不上），
   * 这里必须说出来，而不是渲染成"同步范围：（空）"这种看不出所以然的描述。
   */
  it('范围一个都没配时明确说不会有域名被同步', () => {
    expect(syncScopeText(info())).toBe('未配置任何范围，当前不会有域名被同步');
  });
});
