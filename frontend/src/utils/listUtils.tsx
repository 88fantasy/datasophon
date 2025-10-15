import { cloneDeep, isNil } from 'lodash-es';
import { formatMomentObJ2YYYYMMDD } from './dateTime';
import { Dropdown } from 'antd';
import { isEmpty } from './util';

const emptyKey = '-';

export const defPagnation = {
  defaultPageSize: 20,
  defaultCurrent: 1,
  current: 1,
  pageSize: 20,
  showQuickJumper: true,
  showSizeChanger: true,
}
export const invokeMapValue = (data, property, objKey) => {
  let res = data[property];

  if (property && !res && /./.test(property)) {
    const keys = property.split('.');

    res = data[keys.shift()];
    while (res && keys.length) {
      res = res[keys.shift()];
    }
  }

  if (objKey) {
    if (Array.isArray(res)) {
      res = res.map(item => item[objKey]).join(',');
    } else if (_.isObject(res)) {
      res = res.name;
    }
  }

  return res;
};

export const invokeTransFlattenObjToOriginObj = obj => {
  const res = {};

  const fn = (mapObj, keys, value) => {
    const k = keys.shift();

    if (k) {
      if (keys.length) {
        if (!mapObj[k]) {
          mapObj[k] = {};
        }
        fn(mapObj[k], keys, value);
      } else {
        mapObj[k] = value;
      }
    }
  };

  Object.keys(obj).forEach(key => {
    const v = obj[key];
    if (/\./.test(key)) {
      const keys = key.split('.');
      fn(res, keys, v);
    } else {
      res[key] = v;
    }
  });

  return res;
};

export const invokeTransOriginObjToFlattenObj = (obj, prefixKey = '') => {
  const res = {};

  Object.keys(obj).forEach(k => {
    let v = obj[k];

    if (/Null/.test(Object.prototype.toString.call(v))) {
      v = undefined;
    }

    let mapKey = k;

    if (prefixKey) {
      mapKey = `${prefixKey}.${k}`;
    }

    if (/Object/.test(Object.prototype.toString.call(v))) {
      Object.assign(res, invokeTransOriginObjToFlattenObj(v, mapKey));
    } else {
      res[mapKey] = v;
    }
  });

  return res;
};

export const invokeFlattenArr = (arr, obj = {}) => {
  const resArr = [];

  const resMap = {};

  if (arr?.length) {
    const { childrenKey = 'children', idKey = 'id' } = obj;

    arr.map(val => {
      resMap[val[idKey]] = val;
      const children = val[childrenKey];

      if (children) {
        const res = invokeFlattenArr(children, obj);

        Object.assign(resMap, res.resMap);
        resArr.push(...res.resArr);
      }
    });
  }

  return {
    resArr,
    resMap,
  };
};

export const invokeFilterArrByTxt = (arr, txt = '') => {
  console.log('arr', arr);
  txt = txt.trim();
  arr = cloneDeep(arr);
  const fn = (list, k) => {
    return list.filter(v => {
      if (v.children) {
        v.children = fn(v.children, k);
      }

      if (new RegExp(txt).test(v.label)) {
        return true;
      } else if (v.children?.length) {
        return true;
      }
    });
  };

  if (txt) {
    return fn(arr, txt);
  } else {
    return arr;
  }
};

export const invokeFilterNoChildrenObj = arr => {
  return arr.filter(v => {
    if (v.children) {
      v.children = invokeFilterNoChildrenObj(v.children);
    }
    return v.children?.length;
  });
};

export const invokeDeepFind = ({ arr, key, value }) => {
  let res;
  const linkedList = [];

  if (Array.isArray(arr) && arr.length) {
    if (isNil(key)) {
      const firstObj = arr.find(val => !isNil(val));

      if (/Object/.test(Object.prototype.toString.call(firstObj))) {
        key = 'key';
      }
    }

    for (const item of arr) {
      if ((!isNil(key) && item[key] === value) || item === value) {
        res = item;
        linkedList.push(item);
      } else if (item.children) {
        const invokeDeepFindRes = invokeDeepFind({
          arr: item.children,
          key,
          value,
        });
        res = invokeDeepFindRes.res;
        if (res) {
          linkedList.push(item, ...(invokeDeepFindRes.linkedList || []));
        }
      }

      if (!isNil(res)) {
        break;
      }
    }
  }

  return {
    res,
    linkedList,
  };
};

export const valueFn = scope => {
  if (Array.isArray(scope)) {
    scope.row = scope[1];
    scope.column = {
      property: scope[4].dataIndex,
    };
  }

  const property = scope.column.property;

  const res = invokeMapValue(scope.row, property);

  return res;
};

export const mapEmptyValueFn = res => {
  return !isEmpty(res) ? res : emptyKey;
};

export const invokeGenTagDomFn = fn => {
  return scope => {
    const obj = fn(valueFn(scope)) || {};

    return mapEmptyValueFn(obj.tagDom || obj.label);
  };
};

export const formatTimeRender = scope => {
  let res = valueFn(scope);

  res = mapEmptyValueFn(res);

  if (res !== emptyKey) {
    res = `${res}h`;
  }

  return res;
};

export const formatPrecenterRender = scope => {
  const res = valueFn(scope);

  return formatPrecenter(res);
};

export const formatDateTimeRender = scope => {
  let res = valueFn(scope);

  res = (res && formatMomentObJ2YYYYMMDD(res)) || undefined;

  return mapEmptyValueFn(res);
};
export const formatValue = scope => {
  const res = valueFn(scope);

  return mapEmptyValueFn(res);
};

export const formatPrecenter = res => {
  res = mapEmptyValueFn(res);

  if (res !== emptyKey) {
    res = `${(res * 100).toFixed(1)}%`;
  }

  return res;
};

export const invokeJsonParse = (obj, str) => {
  if (typeof obj === 'string') {
    str = obj;
    try {
      str = JSON.parse(str);
    } catch (e) {
      console.warn(e);
    }

    return str;
  } else {
    if (typeof str === 'string') {
      str = [str];
    }
    str.forEach(v => {
      try {
        obj[v] = JSON.parse(obj[v]);
      } catch (e) {
        console.warn(e);
      }
    });
  }
};



export const invokeGenerateOptsCol = ({
  arr,
  maxLen,
  // attrArr
}) => {

  arr = arr.filter(Boolean)


  if (maxLen && arr.length > maxLen) {
    const items = arr.splice(maxLen - 1);


    // const lastMapEvent = {}


    items.map(val => {
      if (isEmpty(val.key)) {
        val.key = val.label
      }
      // lastMapEvent[val.key || val.label] = val.onClick
    })


    // const onClick = ({ key, onClick }) => {
    //   const fn = lastMapEvent[key]

    //   fn && fn()
    // }

    const render = () => {
      const label = '更多'
      return (
        <Dropdown
          menu={{
            items,
            // onClick
          }}
          key={label}
        >
          <a>{label}</a>
        </Dropdown>
      )
    }

    arr.push(
      render
    )
  }



  return arr.map(val => {
    if (typeof val === 'function') {
      return val()
    } else {
      return (
        <a key={val.label} onClick={val.onClick}>{val.label}</a>
      )
    }
  })
};

export const invokeScrollIntoView = (key) => {

  let animationId = requestAnimationFrame(() => {
    const el = document.querySelector(key)
    if (el && el.checkVisibility) {
      el.scrollIntoView()
    }

    cancelAnimationFrame(animationId)
    animationId = undefined
  })
};
