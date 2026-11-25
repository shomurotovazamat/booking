import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    // В режиме разработки запросы к API уходят на локальный Spring Boot
    proxy: { '/api': 'http://localhost:8080' },
  },
});
