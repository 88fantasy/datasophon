import { createBrowserRouter, Navigate, type RouteObject } from 'react-router-dom';
import { lazy } from 'react';
import {
  ChromeFilled,
  CrownFilled,
  SmileFilled,
  TabletFilled,
} from '@ant-design/icons';
import { cloneDeep } from 'lodash-es';
import { VUE_APP_PUBLIC_PATH } from '../config';
import type { TExendsRouteObject } from './interface';

// 懒加载页面组件
const Login = lazy(() => import('../pages/Login'));
const Proxy = lazy(() => import('../pages/Proxy'));
const ColonyManage = lazy(() => import('../pages/Colony/ColonyManage'));
const ColonyParcel = lazy(() => import('../pages/Colony/ColonyParcel'));
const ColonyFrame = lazy(() => import('../pages/Colony/ColonyFrame'));
const UserManage = lazy(() => import('../pages/User/UserManage'));

const contentRoutes = [
  {
    path: '/Colony',
    element: <Proxy />,
    icon: <CrownFilled />,
    title: '集群管理',
    children: [
      {
        path: 'ColonyManage',
        title: '集群管理',
        element: <ColonyManage />,
      },
      {
        path: 'ColonyParcel',
        title: '存储库管理',
        element: <ColonyParcel />,
      },
      {
        path: 'ColonyFrame',
        title: '集群框架',
        element: <ColonyFrame />,
      },
    ],
  },
  {
    path: '/User',
    element: <Proxy />,
    icon: <SmileFilled />,
    title: '用户管理',
    children: [
      {
        path: 'UserManage',
        title: '用户管理',
        element: <UserManage />,
      },
    ],
  }

]

// 定义路由配置（使用 react-router 的 RouteObject 兼容结构）
let routes = [
  {
    path: '/account/login',
    element: <Login />,
    auth: 0
  },
  ...contentRoutes,
] as RouteObject[];

const routesMap = {}
const invokeGenRoutes = (arr: RouteObject[]) => {
  arr = cloneDeep(arr)
  arr = arr.map(val => {
    const path = `${VUE_APP_PUBLIC_PATH}/${val.path}`.replace(/\/\//, '/')
    const obj = {
      ...val,
      path
    }

    routesMap[path] = obj

    return obj
  })

  return arr
}

const menuMap = {}
const invokeGenMenus = (arr) => {
  const fn = (list: TExendsRouteObject[], pathPrefix) => {
    console.log('pathPrefix', pathPrefix)
    return list.map((val, index) => {
      val = cloneDeep(val)
      let path = val.path


      if (!/^(http|\/\/)/.test(val.path)) {
        path = `${pathPrefix}/${val.path}`.replace(/\/\//, '/')
      }

      const obj = {
        // title: val.title,
        icon: val.icon,
        name: val.title,
        component: val.element,
        access: 'canAdmin',
        path,
        routes: val.children && fn(val.children, path)
      }


      menuMap[path] = obj

      return obj
    })
  }


  const res = {
    route: {
      path: `${VUE_APP_PUBLIC_PATH}`,
      routes: fn(arr, VUE_APP_PUBLIC_PATH)
    },

  }


  return res

}

routes = invokeGenRoutes(routes)
const menu = invokeGenMenus(contentRoutes)
routes.push(
  {
    path: '/',
    element: <Navigate to={`${VUE_APP_PUBLIC_PATH}/account/login`} replace />,
  },
  {
    path: '*',
    element: <Navigate to={`${VUE_APP_PUBLIC_PATH}/account/login`} replace />,
  },
)


console.log('menu', menu)
console.log('routes', routes)


const router = createBrowserRouter(routes);

export {
  menu,
  menuMap,
  routesMap
}
export default router;