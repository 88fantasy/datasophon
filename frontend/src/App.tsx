import { Suspense, useEffect } from "react"
import { RouterProvider, useLocation } from "react-router-dom"
import router, { routesMap } from "./routes"
import { account } from "./utils/account";
import { useNavigate } from "react-router-dom";
import { VUE_APP_PUBLIC_PATH } from "./config";
import { App, Spin } from "antd";




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


  return (
    <>
      <App>
        <Suspense fallback={
          <Spin />
        }>
          <RouterProvider router={router} />
        </Suspense>
      </App>

    </>

  )
}

export default Index