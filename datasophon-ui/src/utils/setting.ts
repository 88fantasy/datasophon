export const invokeFormatMultipleWithMapData = (params, item) => {
    try {
        params.forEach((val) => {
            let itemValue = val.type === "multipleWithMap" && item[val.name];

            if (itemValue) {
                itemValue = _.cloneDeep(itemValue);
                let splitLen = Object.keys(val.defaultValue[0]).length;

                const res = [];

                const fn = (arr) => {
                    const itemMap = {};
                    arr.forEach((item, index) => {
                        itemMap[Object.keys(item)[0]] = Object.values(item)[0];
                    });

                    res.push(itemMap);

                    if (itemValue.length) {
                        fn(itemValue.splice(0, splitLen));
                    }
                };

                fn(itemValue.splice(0, splitLen));
                item[val.name] = res;
            }
        });

    } catch (e) {
        console.warn(e)
    }
    return item;
}