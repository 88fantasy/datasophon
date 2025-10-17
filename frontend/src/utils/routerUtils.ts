import qs from "qs";
import { cloneDeep, isNull, isString, isUndefined } from "lodash-es";
import { VUE_APP_PUBLIC_PATH } from "../config";
import { matchPath, matchRoutes, parsePath } from "react-router-dom";
import router from "../routes";
// import { useHistory } from 'react-router';

// import { useHistory } from 'react-router';

let invokeGetHistoryFn;

export const invokeSetGetHistoryFn = (fn) => {
  invokeGetHistoryFn = fn;
};

export const getRouteParam = (k?: string, defVal?: any) => {
  const params = new URLSearchParams(location.search);

  // return k ? params[k] || defVal : params;

  if (!(isUndefined(k) || isNull(k) || (isString(k) && k.length <= 0))) {
    return params[k] || defVal;
  } else {
    return params;
  }
};

export function getRouteQuery(key?: string, defVal?: any) {
  // const currentRouter = getRouterFromRouteConfig()
  // const currentRoute = currentRouter.currentRoute.value
  // const query = currentRoute.query
  let query = {};
  let search = window.location.search;
  const { hash } = window.location;
  if (hash) {
    // query = qs.parse(window.location.hash.split('#|#')[1])
    if (/\?/.test(hash)) {
      search = hash.replace(/#.*?\?/, "");
    }
  }

  search = search.replace(/^\?/, "");

  query = qs.parse(search);

  if (!key) {
    return cloneDeep(query);
  } else if (query[key]) {
    return query[key];
  } else if (defVal !== undefined) {
    return defVal;
  }

  return "";
}

export function replaceRouter(obj) {
  const { navigate, query = {}, path = window.location.pathname } = obj;
  if (navigate) {
    navigate(`${path}?${qs.stringify(Object.assign(getRouteQuery(), query))}`, {
      replace: true,
    });
  } else {
    history.replaceState(
      null,
      "",
      `${path}?${qs.stringify(Object.assign(getRouteQuery(), query))}`
    );
  }
}

export function pushRouter(obj) {
  if (typeof obj === "string") {
    obj = {
      path: obj,
    };
  }

  if (!obj.query) {
    obj.query = {};
  }

  obj.query.rt = new Date().getTime();
  // useHist;

  const history = invokeGetHistoryFn();

  history.push(obj.path, {
    state: obj.query,
  });

  // useNav

  // normalizeQueryWithPreverseKey(obj);
  // getRouterFromRouteConfig(obj).push({
  //   name: obj.name,
  //   path: obj.path,
  //   query: obj.query,
  //   params: obj.params || null,
  //   meta: obj.meta || null,
  // });
  // const histor;
}

export const invokeGenPath = (path) => {
  let res;
  if (/^(\/\/ | http)/.test(path)) {
    res = path;
  } else if (
    VUE_APP_PUBLIC_PATH &&
    new RegExp(VUE_APP_PUBLIC_PATH).test(path)
  ) {
    res = path;
  } else {
    res = `${VUE_APP_PUBLIC_PATH}${path}`;
  }

  return invokeHandlePath(res);
};

function flatterRoutes(arr, path = "") {
  let res = [];

  // if (!res) {

  // res = flatterRoutes.cache = [];
  arr.map((val) => {
    if (path) {
      val.path = `/${path}/${val.path}`.replace(/\/\//g, "/");
    }

    if (val.children) {
      res.push(...flatterRoutes(val.children, val.path));
    }

    res.push(val);
  });
  // }

  return res;
}

export function invokeGetRouteByPath(path = window.location.pathname) {
  let routes = invokeGetRouteByPath.cache;

  if (!routes) {
    console.log("flatterRoutes(cloneDeep(router.routes))");
    routes = invokeGetRouteByPath.cache = flatterRoutes(
      cloneDeep(router.routes)
    );
  }

  // const routes =invokeGetRouteByPath.cache flatterRoutes(cloneDeep(router.routes));

  // console.log(
  //   "flatterRoutes(cloneDeep(router.routes))",
  //   matchRoutes(routes, path)
  // );
  const matchRoute = matchRoutes(routes, path)?.[0];

  return matchRoute;
}
export const invokeHandlePath = (path) => {
  // const currentPathArr = window.location.pathname.split("/");

  // const patternId = currentPathArr.filter(Boolean)[1];

  const matchRoute = invokeGetRouteByPath();
  const params = matchRoute.params;

  let pathArr = path.split("/").filter(Boolean);

  pathArr = pathArr.map((val) => {
    if (/:clusterId/.test(val)) {
      return params.clusterId;
    }

    return val;
  });

  return `/${pathArr.join("/")}`;
};

export function convertToRoutePattern(url) {
  // 匹配：/ddh/<包含"clusterId"的段>/HostManage
  const matchRoute = invokeGetRouteByPath(url);

  return matchRoute.pathname;
}
