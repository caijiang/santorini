import Module from '@private-everest/module';
import mainRoutes from './mainRoutes';
import { tokenApi } from './apis/token';
import { commonApi } from './apis/kubernetes/common';
import { envApi } from './apis/env';
import { useMainNav } from './menu';
import { serviceApi } from './apis/service';
import { listenerMiddleware } from './module-private';
import { kubeServiceApi } from './apis/kubernetes/service';
import { ingressApi } from './apis/kubernetes/ingress';
import { hostApi } from './apis/host';
import { miscApi, useAppNameQuery } from './apis/misc';
import { userApi } from './apis/user';
import { kubePodsApi } from './apis/kubernetes/pods';
import { deploymentApi } from './apis/deployment';
import { envWikiApi } from './apis/envWiki';
import publicRoutes from './publicRoutes';

export { useAppNameQuery };

export default {
  middlewares: [
    tokenApi.middleware,
    commonApi.middleware,
    kubeServiceApi.middleware,
    ingressApi.middleware,
    deploymentApi.middleware,
    envApi.middleware,
    envWikiApi.middleware,
    kubePodsApi.middleware,
    serviceApi.middleware,
    hostApi.middleware,
    miscApi.middleware,
    userApi.middleware,
    listenerMiddleware.middleware,
  ],
  reducers: {
    [tokenApi.reducerPath]: tokenApi.reducer,
    [commonApi.reducerPath]: commonApi.reducer,
    [kubeServiceApi.reducerPath]: kubeServiceApi.reducer,
    [ingressApi.reducerPath]: ingressApi.reducer,
    [deploymentApi.reducerPath]: deploymentApi.reducer,
    [envApi.reducerPath]: envApi.reducer,
    [envWikiApi.reducerPath]: envWikiApi.reducer,
    [kubePodsApi.reducerPath]: kubePodsApi.reducer,
    [serviceApi.reducerPath]: serviceApi.reducer,
    [hostApi.reducerPath]: hostApi.reducer,
    [miscApi.reducerPath]: miscApi.reducer,
    [userApi.reducerPath]: userApi.reducer,
  },
  routesInRoot: publicRoutes,
  routesInMainNav: mainRoutes,
  mainMenuHookGenerator: () => useMainNav,
} as Module;
