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

const Users = lazy(() => import('./pages/users'));

const EnvServiceLayout = lazy(() => import('./layouts/EnvServiceLayout'));
const Pods = lazy(() => import('./pages/envFor/service/Pods'));
const Pod = lazy(() => import('./pages/envFor/service/Pod'));
const EnvDeploymentHistory = lazy(
  () => import('./pages/envFor/service/DeploymentHistory')
);

const ServiceLayout = lazy(() => import('./layouts/ServiceLayout'));
const DeploymentHistory = lazy(
  () => import('./pages/service/DeploymentHistory')
);

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
    path: 'services/:serviceId',
    element: <ServiceLayout />,
    children: [
      {
        path: 'edit',
        element: <EditService />,
      },
      {
        path: 'history',
        element: <DeploymentHistory />,
      },
    ],
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
        path: 'services/:serviceId',
        element: <EnvServiceLayout />,
        children: [
          {
            path: 'history',
            element: <EnvDeploymentHistory />,
          },
          {
            path: 'pods',
            element: <Pods />,
            children: [
              {
                path: ':podId',
                element: <Pod />,
              },
            ],
          },
        ],
      },
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
  {
    path: 'users',
    element: <Users />,
  },
];
