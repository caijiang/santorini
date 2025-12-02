import { lazy } from 'react';

const HomePage = lazy(() => import('./pages/HomePage'));
const AddService = lazy(() => import('./pages/service/AddService'));
const Deploy = lazy(() => import('./pages/deploy'));

const EnvLayout = lazy(() => import('./layouts/EnvLayout'));
const EnvIndex = lazy(() => import('./pages/EnvIndexPage'));

export default [
  {
    path: 'home',
    element: <HomePage />,
  },
  {
    path: 'addService',
    element: <AddService />,
  },
  // 部署一个新的
  {
    path: 'deploy/:env/:service',
    element: <Deploy />,
  },
  {
    path: 'envFor/:env',
    element: <EnvLayout />,
    children: [
      {
        index: true,
        element: <EnvIndex />,
      },
    ],
  },
];
