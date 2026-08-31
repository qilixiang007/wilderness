import { createApp } from 'vue'
import { createRouter, createWebHistory } from 'vue-router'
import App from './App.vue'
import routes from './router'
import { i18n } from './i18n'
import './styles.css'

const router = createRouter({
	history: createWebHistory(),
	routes
})

createApp(App).use(router).use(i18n).mount('#app')
