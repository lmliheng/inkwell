import { createApp } from 'vue'
import App from './App.vue'
import ElementPlus from 'element-plus'
import naive from 'naive-ui'
import print from 'vue3-print-nb'
import piniaPluginPersistedstate from 'pinia-plugin-persistedstate'
import 'element-plus/dist/index.css'
import router from './router/index'
import { createPinia } from 'pinia'

import './router/NavigationGuards'
import './asset/main.css'

import i18n from './i18n/index'

const app = createApp(App)
const pinia = createPinia()

import * as Sentry from "@sentry/vue";

Sentry.init({
  app,
  dsn: "https://4b841989e87612941b79ea5d8fa80ce6@o4512086088876032.ingest.us.sentry.io/4512095841026048",
  dataCollection: {
    // To disable sending user data and HTTP bodies, uncomment the lines below. For more info visit:
    // https://docs.sentry.io/platforms/javascript/guides/vue/configuration/options/#dataCollection
    // userInfo: false,
    // httpBodies: []
  },
  integrations: [
    Sentry.browserTracingIntegration({ router }),
    Sentry.replayIntegration()
  ],
  // Tracing
  tracesSampleRate: 1.0, // Capture 100% of the transactions
  // Set 'tracePropagationTargets' to control for which URLs distributed tracing should be enabled
  tracePropagationTargets: ["localhost", /^https:\/\/yourserver\.io\/api/],
  // Session Replay
  replaysSessionSampleRate: 0.1, // This sets the sample rate at 10%. You may want to change it to 100% while in development and then sample at a lower rate in production.
  replaysOnErrorSampleRate: 1.0 // If you're not already sampling the entire session, change the sample rate to 100% when sampling sessions where errors occur.
});

pinia.use(piniaPluginPersistedstate)
app.use(pinia)
app.use(print)
app.use(router)
app.use(ElementPlus)
app.use(naive)
app.use(i18n)
app.mount('#app')
