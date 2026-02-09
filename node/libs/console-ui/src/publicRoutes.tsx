import { lazy } from 'react';

const OneEnvWiki = lazy(() => import('./pages/global/OneEnvWiki'));
const ServerSideApplyDrawerPreviewPage = lazy(
  () => import('./components/drawer/ServerSideApplyDrawerPreviewPage')
);

export default [
  ...(import.meta.env.DEV
    ? [
        {
          path: '/ServerSideApplyDrawerPreviewPage',
          element: <ServerSideApplyDrawerPreviewPage />,
        },
      ]
    : []),
  {
    path: 'gwk/:envId/:title',
    element: <OneEnvWiki />,
  },
];
