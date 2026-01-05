import { lazy } from 'react';

const OneEnvWiki = lazy(() => import('./pages/global/OneEnvWiki'));

export default [
  {
    path: 'gwk/:envId/:title',
    element: <OneEnvWiki />,
  },
];
