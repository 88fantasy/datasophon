import { invokeMapValue, mapEmptyValueFn } from "../../../utils/listUtils"

export const invokeRenderSimpleDetails = (arr, obj) => {
    return arr.map(val => {
        let value = typeof val.dataIndex === 'function' ? val.dataIndex() : obj[val.dataIndex]

        value = mapEmptyValueFn(value)

        return (
            <div key={val.label}>
                <span className="label">{val.title}：</span>
                <span className="value">{value}</span>
            </div>
        )
    })
}