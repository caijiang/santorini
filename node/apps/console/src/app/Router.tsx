// import {ModuleRouteObject} from "@private-everest/module";
import Root from '../layouts/Root';
import modules from './modules';
import {
  Redirect,
  routesInMainNav,
  routesInRoot,
  updateRouterType,
} from '@private-everest/app-support';
import Main from '../layouts/Main';
import { lazy } from 'react';
import { createBrowserRouter, RouterProvider } from 'react-router-dom';

const Login = lazy(() => import('../pages/Login'));

const router = createBrowserRouter([
  {
    path: '/',
    element: <Root />,
    children: [
      ...routesInRoot(modules),
      {
        path: 'login',
        element: <Login />,
      },
      {
        path: 'about',
        element: <div>About</div>,
      },
      {
        element: <Main />,
        children: [
          ...routesInMainNav(modules),
          // {
          //   path: 'home',
          //   // element:<p>home</p>
          //   element: <PageContainer title={'首页'}></PageContainer>
          // },
          {
            index: true,
            element: <Redirect to="/home" />,
          },
        ],
      },
    ],
  },
]);

updateRouterType('browser');

export default () => <RouterProvider router={router}/>
