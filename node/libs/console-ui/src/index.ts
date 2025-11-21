import Module from '@private-everest/module';
import mainRoutes from './mainRoutes';
import { tokenApi } from './apis/token';
import { commonApi } from './apis/common';
import { envApi } from './apis/env';
import { useMainNav } from './menu';

export default {
  middlewares: [tokenApi.middleware, commonApi.middleware, envApi.middleware],
  reducers: {
    [tokenApi.reducerPath]: tokenApi.reducer,
    [commonApi.reducerPath]: commonApi.reducer,
    [envApi.reducerPath]: envApi.reducer,
  },
  routesInMainNav: mainRoutes,
  mainMenuHookGenerator: () => useMainNav,
  // useMainNav,
  // mainNav: (u,store) => {
  //   const s = store.getState();
  //   // console.log(store.getState());
  //   console.log('state:',s)
  //   return [];
  // },
} as Module;
