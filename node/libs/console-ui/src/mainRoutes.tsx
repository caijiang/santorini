import {lazy} from 'react';

const HomePage = lazy(() => import('./pages/HomePage'));

export default [
  {
    path: 'home',
    element: <HomePage/>
  }
];
