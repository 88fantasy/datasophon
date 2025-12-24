import { Suspense, useEffect } from "react"
import { RouterProvider, useLocation } from "react-router-dom"
import router, { routesMap } from "./routes"
import { App, ConfigProvider, Spin } from "antd";
import injectLocationChange from "./utils/injectLocationChange";
import { invokeRelogin } from "./utils/authorityUtils";

const showDagModal = () => import('./components/DagModal/api')
const invokeInjectLocationEvent = () => {
  injectLocationChange()


  window.addEventListener('locationchange', invokeRelogin)
  invokeRelogin()
}


window.invokeShowDagModal = async () => {
  const modelApi = await showDagModal()
  modelApi.default({})
}

const Index = () => {

  // const location = useLocation();
  // const navigate = useNavigate()



  // useEffect(() => {
  //   const user = account.getUser()
  //   console.log('user', user)
  //   if (!user && routesMap[location.pathname]?.auth) {
  //     navigate(`${VUE_APP_PUBLIC_PATH}/account/login`)
  //   }
  // }, [location.pathname, navigate])

  useEffect(() => {

    invokeInjectLocationEvent()
  }, [])


  return (
    <>
      <ConfigProvider
        theme={{
          cssVar: true
        }}
      >
        <App>
          <Suspense fallback={
            <Spin />
          }>
            <RouterProvider router={router} />
          </Suspense>
        </App>
      </ConfigProvider>


    </>

  )
}

export default Index