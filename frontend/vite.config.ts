import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    // 개발 중 /api는 Vite가 백엔드로 프록시한다(same-origin). 브라우저가 8080에
    // 직접 붙지 않으므로 CORS·WSL localhost 포워딩 문제를 원천적으로 피한다.
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
})
