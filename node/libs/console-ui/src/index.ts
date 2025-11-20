import Module from '@private-everest/module';
import mainRoutes from './mainRoutes';
import { tokenApi } from './apis/token';
import { commonApi } from './apis/common';
import { envApi } from './apis/env';
import { useEnvs } from './hooks/common';

function useMainNav() {
  // const store = useStore()
  // console.warn('store: ',store)
  const env = useEnvs();
  return [
    // {
    //   name: '首页2',
    //   path: '/home',
    // }
    ...(env ?? []).map((it) => ({
      name: it.name,
      path: `/envFor/${it.id}`,
    })),
  ];
}

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
