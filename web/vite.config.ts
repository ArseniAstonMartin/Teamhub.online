/// <reference types="vitest" />
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { sentryVitePlugin } from '@sentry/vite-plugin';
import { fileURLToPath, URL } from 'node:url';
import { execSync } from 'node:child_process';
import pkg from './package.json';

const gitSha = (() => {
  try {
    return execSync('git rev-parse --short HEAD').toString().trim();
  } catch {
    return 'local';
  }
})();

const release = `teamhub-web@${pkg.version}+${gitSha}`;

export default defineConfig(({ mode }) => ({
  plugins: [
    react(),
    // Uploads source maps and creates a Sentry release on production builds.
    // Skipped when SENTRY_AUTH_TOKEN is absent (local dev without Sentry configured).
    ...(mode === 'production' && process.env.SENTRY_AUTH_TOKEN
      ? [
          sentryVitePlugin({
            org: 'teamhub-gs',
            project: 'teamhub-react',
            authToken: process.env.SENTRY_AUTH_TOKEN,
            release: { name: release },
            sourcemaps: { filesToDeleteAfterUpload: ['./dist/**/*.map'] },
          }),
        ]
      : []),
  ],
  define: {
    __APP_VERSION__: JSON.stringify(pkg.version),
    __GIT_SHA__: JSON.stringify(gitSha),
  },
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
      '@app': fileURLToPath(new URL('./src/app', import.meta.url)),
      '@pages': fileURLToPath(new URL('./src/pages', import.meta.url)),
      '@widgets': fileURLToPath(new URL('./src/widgets', import.meta.url)),
      '@features': fileURLToPath(new URL('./src/features', import.meta.url)),
      '@entities': fileURLToPath(new URL('./src/entities', import.meta.url)),
      '@shared': fileURLToPath(new URL('./src/shared', import.meta.url)),
    },
  },
  server: {
    port: 5173,
  },
  build: {
    sourcemap: true,
  },
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['./src/test-setup.ts'],
    exclude: ['**/node_modules/**', '**/dist/**', '**/e2e/**'],
  },
}));
