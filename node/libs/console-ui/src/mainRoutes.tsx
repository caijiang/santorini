import { lazy } from 'react';

const HomePage = lazy(() => import('./pages/HomePage'));
const AddService = lazy(() => import('./pages/service/AddService'));

export default [
  {
    path: 'home',
    element: <HomePage />,
  },
  {
    path: 'addService',
    element: <AddService />,
  },
];
