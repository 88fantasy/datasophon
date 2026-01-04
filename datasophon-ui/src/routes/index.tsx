import { createBrowserRouter, matchRoutes, Navigate, useParams, type RouteObject } from 'react-router-dom';
import { lazy } from 'react';
import {
  AlertOutlined,
  AppstoreOutlined,
  ChromeFilled,
  CrownFilled,
  DashboardOutlined,
  LaptopOutlined,
  ProjectOutlined,
  SettingOutlined,
  SmileFilled,
  TabletFilled,
  UserOutlined,
} from '@ant-design/icons';
import { cloneDeep, noop } from 'lodash-es';
import { VUE_APP_PUBLIC_PATH } from '../config';
import type { TExendsRouteObject } from './interface';

// 懒加载页面组件
const Login = lazy(() => import('../pages/Login'));
const Dag = lazy(() => import('../components/DagModal'));
const Proxy = lazy(() => import('../pages/Proxy'));
const ColonyManage = lazy(() => import('../pages/Colony/ColonyManage'));
const ColonyParcel = lazy(() => import('../pages/Colony/ColonyParcel'));
const ColonyFrame = lazy(() => import('../pages/Colony/ColonyFrame'));
const UserManage = lazy(() => import('../pages/User/UserManage'));
// const ClusterOverview = lazy(() => import('../pages/Cluster/Overview'));
const SystemCenterTag = lazy(() => import('../pages/SystemCenter/Tag'));
const SystemCenterFrame = lazy(() => import('../pages/SystemCenter/Frame'));
const SystemCenterUser = lazy(() => import('../pages/SystemCenter/User'));
const AlarmManageGroup = lazy(() => import('../pages/AlarmManage/Group'));
const AlarmManageMetric = lazy(() => import('../pages/AlarmManage/Metric'));
const HostManage = lazy(() => import('../pages/HostManage'));
const ServiceManageInstance = lazy(() => import('../pages/ServiceManage/Instance'));
const ServiceManage = lazy(() => import('../pages/ServiceManage'));

const contentRoutes = [
  {
    path: '/Colony',
    element: <Proxy />,
    icon: <AppstoreOutlined />,
    title: '集群管理',
    children: [
      // {
      //   index: true,
      //   element: <Navigate to="ColonyManage" replace />,
      // },
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
    icon: <UserOutlined />,
    title: '用户管理',
    children: [
      {
        path: 'UserManage',
        title: '用户管理',
        element: <UserManage />,
      },
    ],
  },
  // {
  //   path: '/Cluster/:clusterId/Overview',
  //   element: <Proxy />,
  //   icon: <DashboardOutlined />,
  //   title: '总览',
  //   children: [
  //     {
  //       path: 'Index',
  //       element: <ClusterOverview />,
  //       hideInMenu: true,
  //       title: '总览'
  //     }
  //   ]
  // },
  {
    path: '/Cluster/:clusterId/ServiceManage',
    element: <Proxy />,
    icon: <ProjectOutlined />,
    title: '首页',
    children: [
      {
        index: true,
        element: <ServiceManage />
      },
      {
        path: 'Instance/:instanceId',
        element: <ServiceManageInstance />,
        hideInMenu: true,
        title: '实例'
      }
    ]
  },
  {
    path: '/Cluster/:clusterId/HostManage',
    element: <Proxy />,
    icon: <LaptopOutlined />,
    title: '主机管理',
    children: [
      {
        path: 'Index',
        element: <HostManage />,
        hideInMenu: true,
        title: '总览'
      }
    ]
  },
  {
    path: '/Cluster/:clusterId/AlarmManage',
    element: <Proxy />,
    icon: <AlertOutlined />,
    title: '告警管理',
    children: [
      {
        path: 'Group',
        title: '告警组管理',
        element: <AlarmManageGroup />,
      },
      {
        path: 'Metric',
        title: '告警指标管理',
        element: <AlarmManageMetric />,
      },
    ],
  },
  {
    path: '/Cluster/:clusterId/SystemCenter',
    element: <Proxy />,
    icon: <SettingOutlined />,
    title: '系统管理',
    children: [
      {
        path: 'User',
        title: '租户管理',
        element: <SystemCenterUser />,
      },
      {
        path: 'Frame',
        title: '机架管理',
        element: <SystemCenterFrame />,
      },
      {
        path: 'Tag',
        title: '标签管理',
        element: <SystemCenterTag />,

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
  {
    path: '/Dag',
    element: <Dag />
  },
  ...contentRoutes,
] as RouteObject[];

const routesMap = {}




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
        hideInMenu: val.hideInMenu,
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

const menu = invokeGenMenus(contentRoutes)

const invokeInejectIndex = (arr) => {
  arr.map(val => {
    if (val.children && val.children.length) {
      const firstChidld = val.children[0]

      if (!firstChidld.index) {
        val.children.unshift({
          index: true,
          element: <Navigate to={firstChidld.path} replace />,
        })
      }


      invokeInejectIndex(val.children)
    }
  })
}
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

  invokeInejectIndex(arr)

  return arr
}
routes = invokeGenRoutes(routes)
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


function invokeGenMenuByPattern() {
  const currentPathArr = window.location.pathname.split('/')


  let res = []

  const params = useParams();


  console.log('currentPathArr', params, currentPathArr)



  if (params.clusterId) {


    if (invokeGenMenuByPattern.clusterIdRoutes) {
      res = invokeGenMenuByPattern.clusterIdRoutes
    } else {

      const cpContentRoutes = cloneDeep(contentRoutes)
      res = cpContentRoutes.filter((val) => {
        return /\/:clusterId\//.test(val.path)
      })

      res = invokeGenMenus(res)

      invokeGenMenuByPattern.clusterIdRoutes = res
    }



  } else {
    if (invokeGenMenuByPattern.defaultRoutes) {
      res = invokeGenMenuByPattern.defaultRoutes
    } else {
      const cpContentRoutes = cloneDeep(contentRoutes)
      res = cpContentRoutes.filter((val) => {
        return !/\/:clusterId\//.test(val.path)
      })

      res = invokeGenMenus(res)

      invokeGenMenuByPattern.defaultRoutes = res


    }

  }


  console.log('res', res)

  return res
}

// window.invokeGenMenuByPattern = invokeGenMenuByPattern
const router = createBrowserRouter(routes);

export {
  menu,
  menuMap,
  routesMap,
  invokeGenMenuByPattern,
  routes
}





console.log('router', router)
console.log("data ", matchRoutes(router.routes, window.location.pathname));

export default router;