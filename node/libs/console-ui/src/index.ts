import Module from '@private-everest/module';
import mainRoutes from './mainRoutes';
import { tokenApi } from './apis/token';
import { commonApi } from './apis/common';
// import subscribeSlice from "./subscribeSlice";

export default {
  middlewares: [tokenApi.middleware, commonApi.middleware],
  reducers: {
    [tokenApi.reducerPath]: tokenApi.reducer,
    [commonApi.reducerPath]: commonApi.reducer,
    // 'alphaSubscribe': subscribeSlice,
  },
  routesInMainNav: mainRoutes,
} as Module;
