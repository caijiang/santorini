import Module from '@private-everest/module';
import mainRoutes from './mainRoutes';
import { tokenApi } from './apis/token';
import { commonApi } from './apis/kubernetes/common';
import { envApi } from './apis/env';
import { useMainNav } from './menu';
import { serviceApi } from './apis/service';
import { listenerMiddleware } from './module-private';
import { kubeServiceApi } from './apis/kubernetes/service';

export default {
  middlewares: [
    tokenApi.middleware,
    commonApi.middleware,
    kubeServiceApi.middleware,
    envApi.middleware,
    serviceApi.middleware,
    listenerMiddleware.middleware,
  ],
  reducers: {
    [tokenApi.reducerPath]: tokenApi.reducer,
    [commonApi.reducerPath]: commonApi.reducer,
    [kubeServiceApi.reducerPath]: kubeServiceApi.reducer,
    [envApi.reducerPath]: envApi.reducer,
    [serviceApi.reducerPath]: serviceApi.reducer,
  },
  routesInMainNav: mainRoutes,
  mainMenuHookGenerator: () => useMainNav,
} as Module;
