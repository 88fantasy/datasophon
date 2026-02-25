

/*
 * @Author: Rhymedys/Rhymedys@gmail.com
 * @Date: 2018-11-19 15:27:56
 * @Last Modified by: shanshan_liu
 * @Last Modified time: 2022-02-18 15:27:55
 */
// import { ElTag } from "element-plus";
// import { createVNode } from "vue";

/*
 * @Author: Rhymedys/Rhymedys@gmail.com
 * @Date: 2018-11-19 15:27:56
 * @Last Modified by: shanshan_liu
 * @Last Modified time: 2022-02-18 15:27:55
 */
// import { ElTag } from "element-plus";
// import { createVNode } from "vue";
import type { IFromSeriesCommonSelectOption } from "./interface"




/**
 * label 映射 value
 *
 * @export
 * @param {IFromSeriesCommonSelectOption[]} list
 * @param {(string|number)} value
 * @returns
 */
export function mapLabelByValue(list: IFromSeriesCommonSelectOption[], value: string | number) {
  const res = mapObjectByValue(list, value)

  return res && res.label || value
}



/**
 * value 映射 label
 *
 * @export
 * @param {IFromSeriesCommonSelectOption[]} list
 * @param {string} label
 * @returns
 */
export function mapValueByLabel(list: IFromSeriesCommonSelectOption[], label: string) {

  const res = mapObjectByLabel(list, label)

  return res && res.value || ''
}



/**
 * 通过value找到配置对象
 *
 * @export
 * @param {IFromSeriesCommonSelectOption[]} list
 * @param {(string | number)} value
 * @returns
 */
export function mapObjectByValue<T extends IFromSeriesCommonSelectOption>(list: T[], value: string | number) {
  return list.find(val => val.value === String(value))
}



/**
 * 通过label找到配置对象
 *
 * @export
 * @param {IFromSeriesCommonSelectOption[]} list
 * @param {string} label
 * @returns
 */
export function mapObjectByLabel<T extends IFromSeriesCommonSelectOption>(list: T[], label: string) {
  return list.find(val => val.label === label)
}
export function mapArrToValueEnum(list) {
  const res = {}


  list.forEach(v => {
    res[v.value] = {
      text: v.label,
      status: v.status,
    }
  })

  return res
}


// export function invokeMakeTagDom(type, content) {
// 	return createVNode(ElTag, { type }, {
// 		default: () => content
// 	})
// }
