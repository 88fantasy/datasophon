import { memo, useCallback, useState } from "react"
import { Button } from "antd"
import { clone, cloneDeep } from "lodash-es"


const Index = memo((props) => {
  const [state, setState] = useState({
    loadingMap: {}
  })

  const invokeGenBtnList = useCallback(() => {
    return props.list.map(v => {

      v = clone(v)

      const onClick = v.onClick

      v.onClick = async () => {

        state.loadingMap[v.label] = true

        setState(cloneDeep(state))


        const res = onClick && await onClick()

        state.loadingMap[v.label] = false
        setState(cloneDeep(state))
      }


      if (!Object.prototype.hasOwnProperty.call(v, 'loading')) {
        v.loading = state.loadingMap[v.label]
      }

      return (
        <Button
          key={v.label}
          {...v}
        >
          {v.label}
        </Button>
      )
    })
  }, [props, state])



  return (
    <div className="flex items-center gap-[10px]">
      {
        invokeGenBtnList()
      }
    </div>
  )
})


export default Index



