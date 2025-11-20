/// <reference types='vitest' />
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig(() => ({
  root: __dirname,
  cacheDir: '../../node_modules/.vite/apps/santorini',
  server: {
    proxy: {
      '/kubernetes': 'https://op.k8s.mingshz.com',
      '/': {
        target: 'https://op.k8s.mingshz.com',
        changeOrigin: true,
        // undefined -> 继续转发
        bypass: (req) => {
          // if (req.url?.startsWith('/kubernetes')) return req.url;
          if (
            req.headers['x-everest'] == '1' ||
            req.headers['X-EVEREST'] == '1'
          )
            return undefined;
          return req.url;
        },
      },
    },
    port: 4200,
    host: 'localhost',
  },
  preview: {
    port: 4300,
    host: 'localhost',
  },
  plugins: [react()],
  // Uncomment this if you are using workers.
  // worker: {
  //  plugins: [ nxViteTsPaths() ],
  // },
  build: {
    outDir: './dist',
    emptyOutDir: true,
    reportCompressedSize: true,
    commonjsOptions: {
      transformMixedEsModules: true,
    },
  },
  test: {
    watch: false,
    globals: true,
    environment: 'jsdom',
    include: ['{src,tests}/**/*.{test,spec}.{js,mjs,cjs,ts,mts,cts,jsx,tsx}'],
    reporters: ['default'],
    coverage: {
      reportsDirectory: './test-output/vitest/coverage',
      provider: 'v8' as const,
    },
  },
}));
