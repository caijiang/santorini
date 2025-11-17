import {Provider} from 'react-redux';
import {configureStore} from "@reduxjs/toolkit";
import modules from "./modules";
import {middlewares, reducers, storeCallback} from "@private-everest/app-support";
import Router from "./Router";

// 作为最终分发组织者，应当决定如何授权登录
// 1 登录入口应该高度统一,比如确定同一个路由作为登录,场景参数适当保留 /login 就不错
// /login 路由的功能则自行组织
const store = configureStore({
    middleware: (getDefaultMiddleware) =>
        getDefaultMiddleware({})
            .concat(middlewares(modules))
    // .concat(usernamePasswordAuthenticationProviderApi.middleware)
    ,
    reducer: {
        ...reducers(modules),
        // [usernamePasswordAuthenticationProviderApi.reducerPath]: usernamePasswordAuthenticationProviderApi.reducer
    }
});

storeCallback(modules, store)

export default () => {
    return <Provider store={store}><Router/></Provider>;
};
