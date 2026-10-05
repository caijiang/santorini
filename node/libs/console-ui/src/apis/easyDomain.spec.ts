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
 * 拦下请求并记录，统一用 200 + JSON 应答（这里只关心请求长什么样）
 */
function stubHttp(body: unknown = null): CaughtRequest[] {
  const caught: CaughtRequest[] = [];
  vi.stubGlobal(
    'fetch',
    vi.fn(async (request: BrowserLikeRequest) => {
      const url = new URL(request.url);
      caught.push({ path: url.pathname + url.search, method: request.method });
      return new Response(JSON.stringify(body), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      });
    })
  );
  vi.stubGlobal('Request', BrowserLikeRequest);
  return caught;
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

  it('新增：POST /api/domains/{domain}', async () => {
    const caught = stubHttp('new.example.com');
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

  it('删除：DELETE /api/domains/{domain}', async () => {
    const caught = stubHttp();
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

    expect(caught[0]!.path).toBe('/api/domains/*.example.com');
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
