import Module from '@private-everest/module';
import mainRoutes from './mainRoutes';
import { tokenApi } from './apis/token';
import { commonApi } from './apis/common';
import { envApi } from './apis/env';

export default {
  middlewares: [tokenApi.middleware, commonApi.middleware, envApi.middleware],
  reducers: {
    [tokenApi.reducerPath]: tokenApi.reducer,
    [commonApi.reducerPath]: commonApi.reducer,
    [envApi.reducerPath]: envApi.reducer,
  },
  routesInMainNav: mainRoutes,
} as Module;
