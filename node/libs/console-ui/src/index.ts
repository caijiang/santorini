import Module from "@private-everest/module";
import mainRoutes from "./mainRoutes";
import {tokenApi} from './apis/token'
// import subscribeSlice from "./subscribeSlice";

export default {
  middlewares: [tokenApi.middleware],
  reducers: {
    [tokenApi.reducerPath]: tokenApi.reducer,
    // 'alphaSubscribe': subscribeSlice,
  },
    routesInMainNav: mainRoutes,
} as Module;
