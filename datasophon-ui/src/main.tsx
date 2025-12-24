import { StrictMode, Suspense } from 'react'
import { createRoot } from 'react-dom/client'
import './styles'
import App from './App'
import injectLocationChange from './utils/injectLocationChange'
import { account } from './utils/account'
import { routesMap } from './routes'
import { VUE_APP_PUBLIC_PATH } from './config'
// import '@ant-design/v5-patch-for-react-19';




createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
