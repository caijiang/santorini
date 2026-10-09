import { describe, expect, it } from 'vitest';
import type { DomainSyncInfo } from '../../apis/easyDomain';
import { certPathText, syncScopeText } from './syncNotice';

const info = (override: Partial<DomainSyncInfo> = {}): DomainSyncInfo => ({
  dnsCidrs: [],
  dnsCnameSuffixes: [],
  aliyunRegion: null,
  aliyunAlbListenerId: null,
  certSyncEnabled: false,
  ...override,
});

describe('证书去向文案', () => {
  it('同步未启用时不描述链路，而是直说不启用', () => {
    const text = certPathText(info());
    expect(text).toContain('未启用');
    // 链路都没建立起来就不能出现终点，否则会被读成"证书已经挂到 ALB 了"
    expect(text).not.toContain('ALB 监听');
  });

  it('启用后给出链路终点：监听 + region', () => {
    const text = certPathText(
      info({
        certSyncEnabled: true,
        aliyunAlbListenerId: 'lsn-1',
        aliyunRegion: 'cn-hangzhou',
      })
    );
    expect(text).toContain('ALB 监听「lsn-1」（cn-hangzhou）');
  });

  it('没配 region 时不打出空括号', () => {
    const text = certPathText(
      info({
        certSyncEnabled: true,
        aliyunAlbListenerId: 'lsn-1',
        aliyunRegion: null,
      })
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
    ).toBe('A 记录解析到 10.0.0.0/8，或 CNAME 指向 example.com 的域名');
  });

  it('只配了 CIDR 时不出现空的 CNAME 片段', () => {
    expect(syncScopeText(info({ dnsCidrs: ['10.0.0.0/8'] }))).toBe(
      'A 记录解析到 10.0.0.0/8 的域名'
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
