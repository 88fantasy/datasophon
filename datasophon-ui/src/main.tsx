import { StrictMode, Suspense } from 'react'
import { createRoot } from 'react-dom/client'
import './styles'
import App from './App'
import { account } from './utils/account'
import { routesMap } from './routes'
import { VUE_APP_PUBLIC_PATH } from './config'
import { axiosJsonPost, axiosPost } from './api/request'
import { API } from './api'
import { getRouteQuery } from './utils/routerUtils'
// import '@ant-design/v5-patch-for-react-19';


const invokeInit = async () => {


  console.log('user', account.getUser())
  if (account.getUser()) {
    const res = await axiosPost(API.getColonyList)


    if (res.code === 200) {


      if (/\/account\/login/gi.test(location.pathname)) {

        let redirectUri = getRouteQuery('redirectUri')

        redirectUri = redirectUri ? decodeURIComponent(redirectUri) : `${VUE_APP_PUBLIC_PATH}/Colony/ColonyManage`

        window.location.href = redirectUri

        return
      }

    }
  }


  createRoot(document.getElementById('root')!).render(
    <StrictMode>
      <App />
    </StrictMode>,
  )
}

invokeInit()

