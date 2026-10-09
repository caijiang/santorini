import { configureStore } from '@reduxjs/toolkit';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { easyDomainApi } from './easyDomain';
import { toDomainRule } from '../common/ktor';

/**
 * baseQuery 的 baseUrl 是 "/"，而 jsdom 既没有 fetch、Node 的 undici `Request`
 * 又不接受相对 URL。这里补一个浏览器语义的替身，让请求能真的走完一趟，
 * 从而观察到实际发出的 路径+method —— 也就是 `/api` 前缀这一环。
 */
class BrowserLikeRequest {
  readonly url: string;
  readonly method: string;
  readonly headers: unknown;

  constructor(input: string, init?: RequestInit) {
    this.url = new URL(input, 'http://localhost').toString();
    this.method = String(init?.method ?? 'GET').toUpperCase();
    this.headers = init?.headers;
  }

  clone() {
    return this;
  }
}

interface CaughtRequest {
  path: string;
  method: string;
}

function createStore() {
  return configureStore({
    reducer: { [easyDomainApi.reducerPath]: easyDomainApi.reducer },
    middleware: (getDefault) => getDefault().concat(easyDomainApi.middleware),
  });
}

/**
 * 拦下请求并记录，用给定的 responder 应答
 */
function stubFetch(respond: (hit: CaughtRequest) => Response): CaughtRequest[] {
  const caught: CaughtRequest[] = [];
  vi.stubGlobal(
    'fetch',
    vi.fn(async (request: BrowserLikeRequest) => {
      const url = new URL(request.url);
      const hit = { path: url.pathname + url.search, method: request.method };
      caught.push(hit);
      return respond(hit);
    })
  );
  vi.stubGlobal('Request', BrowserLikeRequest);
  return caught;
}

/**
 * 统一用 JSON 应答（这里大多只关心请求长什么样）。
 *
 * `body` 传 `null` 表示**不带 body** —— 服务端新增成功就是 201 + 空 body，
 * 别再用一个 JSON 字符串去假装它，假契约会让真问题溜过去。
 */
function stubHttp(
  body: unknown = null,
  init: ResponseInit = {}
): CaughtRequest[] {
  return stubFetch(
    () =>
      new Response(body === null ? null : JSON.stringify(body), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
        ...init,
      })
  );
}

describe('easy-domain 域名管理接口', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('列表：GET /api/domains', async () => {
    const caught = stubHttp(['a.example.com']);
    const store = createStore();

    const data = await store
      .dispatch(easyDomainApi.endpoints.domains.initiate(undefined))
      .unwrap();

    expect(data).toEqual(['a.example.com']);
    expect(caught).toEqual([{ path: '/api/domains', method: 'GET' }]);
  });

  it('同步信息：GET /api/domains/syncInfo', async () => {
    const caught = stubHttp({
      dnsCidrs: ['10.0.0.0/8'],
      dnsCnameSuffixes: ['example.com'],
      aliyunRegion: 'cn-hangzhou',
      aliyunAlbListenerId: 'lsn-1',
      certSyncEnabled: true,
    });
    const store = createStore();

    const info = await store
      .dispatch(easyDomainApi.endpoints.syncInfo.initiate(undefined))
      .unwrap();

    expect(info.aliyunAlbListenerId).toBe('lsn-1');
    // 路径钉死：服务端把 syncInfo 挂到别处时，这条会红
    expect(caught).toEqual([{ path: '/api/domains/syncInfo', method: 'GET' }]);
  });

  it('新增：POST /api/domains/{domain}，201 且无 body 即成功', async () => {
    const caught = stubHttp(null, { status: 201 });
    const store = createStore();

    await store
      .dispatch(
        easyDomainApi.endpoints.createDomain.initiate('new.example.com')
      )
      .unwrap();

    expect(caught).toEqual([
      { path: '/api/domains/new.example.com', method: 'POST' },
    ]);
  });

  /**
   * 上一轮的现场复现：后端那时把 201 的 body 写成了裸字符串
   * （Ktor 的 `respond(String)` 不发 JSON，而是发 `text/plain`）。
   *
   * 代价有两份：一是前端按 JSON 解析失败 → `PARSING_ERROR`；
   * 二是共享的 `apiBase` 是 `retry(..., { maxRetries: 1 })`，而它的默认重试条件
   * 只看次数、**不看错误类型**，于是同一个 POST 又被原样发了一遍 ——
   * 对一个非幂等的写接口，这是白送的一次副作用。
   *
   * 这条用例把"响应体不是 JSON = 一次多余的写请求"钉住；服务端改契约前先看它。
   */
  it('响应体不是 JSON 时：POST 失败，并因 baseQuery 重试被发出两次', async () => {
    const caught = stubFetch(
      () =>
        new Response('new.example.com', {
          status: 201,
          headers: { 'Content-Type': 'text/plain; charset=UTF-8' },
        })
    );
    const store = createStore();

    await expect(
      store
        .dispatch(
          easyDomainApi.endpoints.createDomain.initiate('new.example.com')
        )
        .unwrap()
    ).rejects.toMatchObject({ status: 'PARSING_ERROR', originalStatus: 201 });

    expect(caught).toEqual([
      { path: '/api/domains/new.example.com', method: 'POST' },
      { path: '/api/domains/new.example.com', method: 'POST' },
    ]);
  });

  it('删除：DELETE /api/domains/{domain}', async () => {
    const caught = stubHttp(null, { status: 204 });
    const store = createStore();

    await store
      .dispatch(
        easyDomainApi.endpoints.deleteDomain.initiate('old.example.com')
      )
      .unwrap();

    expect(caught).toEqual([
      { path: '/api/domains/old.example.com', method: 'DELETE' },
    ]);
  });

  it('通配符域名整段进路径，`*` 无需转义', async () => {
    const caught = stubHttp('*.example.com');
    const store = createStore();

    await store
      .dispatch(easyDomainApi.endpoints.createDomain.initiate('*.example.com'))
      .unwrap();

    expect(caught[0]?.path).toBe('/api/domains/*.example.com');
  });

  it('新增成功后，正在订阅的列表会自动重新拉取（Domains tag 失效）', async () => {
    const caught = stubHttp(['a.example.com']);
    const store = createStore();
    // 必须保持订阅，否则 tag 失效不会触发重新拉取
    const subscription = store.dispatch(
      easyDomainApi.endpoints.domains.initiate(undefined)
    );
    await subscription.unwrap();
    expect(caught).toHaveLength(1);

    await store
      .dispatch(easyDomainApi.endpoints.createDomain.initiate('b.example.com'))
      .unwrap();

    await vi.waitFor(() => {
      expect(caught.filter((it) => it.method === 'GET')).toHaveLength(2);
    });
    subscription.unsubscribe();
  });
});

describe('域名校验规则（服务端 validateDomain 的前端镜像）', () => {
  const { pattern } = toDomainRule();

  it.each([
    'example.com',
    'a.b.example.com',
    'admin.sit.xunxi1688.com',
    'my-host.example.com',
    '*.example.com',
  ])('接受 %s', (domain) => {
    expect(pattern.test(domain)).toBe(true);
  });

  it.each([
    ['大写字母', 'Example.com'],
    ['下划线', 'a_b.example.com'],
    ['裸主机名', 'localhost'],
    ['以 - 结尾的段', 'a-.example.com'],
    ['空字符串', ''],
    ['带协议', 'https://example.com'],
    ['带路径', 'example.com/path'],
  ])('拒绝 %s', (_, domain) => {
    expect(pattern.test(domain)).toBe(false);
  });
});
