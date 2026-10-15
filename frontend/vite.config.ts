import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// In development the API runs on :8080; proxying keeps the browser on one origin,
// exactly like nginx does in the Docker image, so no CORS setup is needed.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': process.env.API_URL ?? 'http://localhost:8080',
    },
  },
});
