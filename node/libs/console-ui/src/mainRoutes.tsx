import { lazy } from 'react';

const HomePage = lazy(() => import('./pages/HomePage'));
const AddService = lazy(() => import('./pages/service/AddService'));
const EditService = lazy(() => import('./pages/service/EditService'));
const Deploy = lazy(() => import('./pages/deploy'));

const EnvLayout = lazy(() => import('./layouts/EnvLayout'));
const EnvIndex = lazy(() => import('./pages/EnvIndexPage'));
const AddResource = lazy(() => import('./pages/envFor/AddResource'));
const ResourceDetail = lazy(() => import('./pages/envFor/ResourceDetail'));

const LoongCollector = lazy(() => import('./pages/support/LoongCollector'));

export default [
  {
    path: 'home',
    element: <HomePage />,
  },
  {
    path: 'addService',
    element: <AddService />,
  },
  {
    path: 'services/:id',
    element: <EditService />,
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
        path: 'addResource',
        element: <AddResource />,
      },
      {
        path: 'resources/:name',
        element: <ResourceDetail />,
      },
      {
        index: true,
        element: <EnvIndex />,
      },
    ],
  },
  {
    path: 'loongCollector',
    element: <LoongCollector />,
  },
];
