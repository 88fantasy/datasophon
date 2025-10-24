// // universal-url-listener.js
// (function () {
//   function dispatchLocationChange() {
//     window.dispatchEvent(new Event('locationchange'));
//   }

//   // 监听浏览器导航
//   window.addEventListener('popstate', dispatchLocationChange);
//   window.addEventListener('hashchange', dispatchLocationChange);

//   // 拦截 pushState 和 replaceState
//   ['pushState', 'replaceState'].forEach((method) => {
//     const original = history[method];
//     history[method] = function (state, title, url) {
//       const result = original.apply(this, [state, title, url]);
//       dispatchLocationChange();
//       return result;
//     };
//   });


//   window.injhectLocationChange = dispatchLocationChange;

//   // 初始触发一次
//   dispatchLocationChange();
// })();

const index = () => {

  if (window.injhectLocationChange) {
    return
  }
  function dispatchLocationChange() {
    window.dispatchEvent(new Event('locationchange'));
  }

  // 监听浏览器导航
  window.addEventListener('popstate', dispatchLocationChange);
  window.addEventListener('hashchange', dispatchLocationChange);

  // 拦截 pushState 和 replaceState
  ['pushState', 'replaceState'].forEach((method) => {
    const original = history[method];
    history[method] = function (state, title, url) {
      const result = original.apply(this, [state, title, url]);
      dispatchLocationChange();
      return result;
    };
  });


  window.injhectLocationChange = dispatchLocationChange;

  // 初始触发一次
  dispatchLocationChange();
}

export default index


