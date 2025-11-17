import Module from "@private-everest/module";
import mainRoutes from "./mainRoutes";
// import {alphaApi} from "./api";
// import subscribeSlice from "./subscribeSlice";

export default {
    // middlewares: [alphaApi.middleware],
    // reducers: {
    //   [alphaApi.reducerPath]: alphaApi.reducer,
    //   'alphaSubscribe': subscribeSlice,
    // },
    routesInMainNav: mainRoutes,
} as Module;
