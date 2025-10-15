import qs from "qs";
import { cloneDeep, isNull, isString, isUndefined } from "lodash-es";
import { replace } from "react-router-dom";
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

export function replaceRouter({
  navigate,
  query = {},
  path = window.location.pathname,
}) {
  navigate(`${path}?${qs.stringify(Object.assign(getRouteQuery(), query))}`, {
    replace: true,
  });
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
