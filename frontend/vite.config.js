import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
	plugins: [vue()],
	server: {
		port: 5173,
		host: '0.0.0.0',
		proxy: {
			'/api': {
				target: 'http://localhost:8080',
				changeOrigin: true
			},
			// 图片也是后端静态资源（backend/src/main/resources/static/images/），
			// 前端相对路径 /images/* 需要代理到后端才能显示。
			'/images': {
				target: 'http://localhost:8080',
				changeOrigin: true
			}
		}
	}
})
