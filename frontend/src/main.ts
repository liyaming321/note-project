import 'ant-design-vue/dist/reset.css'
import 'vditor/dist/index.css'
import 'highlight.js/styles/github.css'
import './styles.css'

import Antd from 'ant-design-vue'
import { createApp } from 'vue'

import App from './App.vue'
import router from './router'

createApp(App).use(Antd).use(router).mount('#app')
