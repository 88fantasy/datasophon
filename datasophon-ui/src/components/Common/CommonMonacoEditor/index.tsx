import { forwardRef, memo, useCallback, useEffect, useImperativeHandle, useMemo, useRef, useState } from "react"
// import * as sql from 'monaco-editor/esm/vs/basic-languages/sql/sql.js';
// import { invokeGenerateElId, isEmpty } from "~/utils/utils";
import MonacoEditor, { invokeCheckMonacoEditorHadInit, invokeFormatByType, invokeGetHighlighter, invokeInitConfig, invokeInitEditor } from "./utils";
import { Monaco } from "@monaco-editor/react";
import { editor } from "monaco-editor";
import { invokeExcuteAnimation } from "../../../utils/animationUtils";
import { cloneDeep } from "lodash-es";
import { invokeGenerateElId, isEmpty } from "../../../utils/util";
// import * as sqlFormatter from 'sql-formatter';


invokeInitConfig()
invokeGetHighlighter()
const onCopyCode = (
  editor: monaco.editor.IStandaloneCodeEditor,
  e: React.MouseEvent<HTMLSpanElement, MouseEvent>
) => {
  e.stopPropagation()

  editor.getModel()?.getFullModelRange()

  const text = editor.getModel()?.getValue()

  if (text) {
    try {
      h5Copy(JSON.stringify(JSON.parse(text), null, 2))
    } catch (error) {
      console.warn(error)
      h5Copy(text)
    }
  }
}


const Index = forwardRef((props, ref) => {


  const editorRef = useRef<editor.IStandaloneCodeEditor>();
  const monacoRef = useRef<Monaco>();
  const wraperElId = useRef(invokeGenerateElId())
  const timeoutId = useRef<number>()
  const key = useRef(invokeGenerateElId())
  // const ref =

  const [libHadInit, setLibHadInit] = useState(() => {
    return invokeCheckMonacoEditorHadInit()
  })


  const editorWillMount = useCallback((editor) => {
    console.log('editorWillMount', editor);
    if (typeof props.editorWillMount === 'function') {
      props.editorWillMount(editor)
    }
  }, [props])


  const invokeFormatDocument = useCallback(async () => {
    if (timeoutId.current) {
      clearTimeout(timeoutId.current)
    }
    try {
      const el = document.getElementById(wraperElId.current)
      console.log('invokeFormatDocument.begin')
      if (editorRef.current && el) {
        await editorRef.current.getAction('editor.action.formatDocument')?.run();
      }
    } catch (error) {
      console.error(error)
    }

    console.log('invokeFormatDocument.end')


  }, [])


  const onEditorMount = useCallback(async (
    editor: editor.IStandaloneCodeEditor,
    monaco: Monaco,
  ) => {


    console.log('onEditorMount.editor', editor)
    console.log('onEditorMount.monaco', monaco)

    console.log('onEditorMount.formatDocument', editor.getAction('editor.action.formatDocument'))


    editorRef.current = editor
    monacoRef.current = monaco


    await invokeFormatDocument()

    const fnMap = {}



    Object.keys(editor).map(val => {
      if (typeof editor[val] === 'function') {
        // let fn = props[val].bind(_.noop, { editor, monaco })

        let fn
        if (fnMap[val]) {
          //  fn = fn
          // fn = editor[val]

          fn = (...args) => {

            fnMap[val](...args)
            // bakFn()
          }
        }


        if (props[val]) {
          const bakFn = fn

          fn = (...args) => {
            bakFn?.(...args)
            props[val]({
              editor,
              monaco,
            }, ...args)
          }
        }

        if (fn) {
          editor[val] = fn
        }

      }
    })


    if (typeof props.editorDidMount === 'function') {
      props.editorDidMount(editor, monaco)
    }

  }, [invokeFormatDocument, props])

  useImperativeHandle(ref, () => {
    const res = {}


    Object.defineProperty(res, 'editor', {
      get() {
        return editorRef.current
      }
    })

    Object.defineProperty(res, 'monaco', {
      get() {
        return monacoRef.current
      }
    })

    return res
  })


  const mapProps = useMemo(() => {
    const res = {
      ...(cloneDeep(props)),
      // defaultValue: props.value
    }
    console.log('props', props)


    res.theme = 'one-light'



    // delete res.value
    // delete mapProps.options

    // delete res?.options?.readOnly

    if (!isEmpty(res.value)) {
      res.value = invokeFormatByType({
        language: res.language,
        value: res.value
      })
    }

    if (!isEmpty(res.defaultValue)) {
      res.defaultValue = invokeFormatByType({
        language: res.language,
        value: res.defaultValue
      })
    }

    console.log('mapProps', res)
    return res
  }, [props])


  useEffect(() => {
    let closeFn
    const invokeInit = () => {
      invokeExcuteAnimation((fn) => {
        closeFn = fn
        const res = invokeCheckMonacoEditorHadInit()
        if (res && res !== libHadInit) {
          setLibHadInit(res)
        } else if (!res) {
          invokeInit()
        }
      })
    }

    invokeInit()

    return () => {
      closeFn?.()
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])




  useEffect(() => {
    requestAnimationFrame(() => {
      invokeFormatDocument()
    })
    let cloaseFn
    invokeExcuteAnimation((fn) => {
      invokeFormatDocument()
      cloaseFn = fn
    })

    return () => {
      cloaseFn?.()
    }
  }, [props.value])


  console.log('update')

  return libHadInit && (
    <div
      className="flex h-full"
      style={{
        ...mapProps.style,
      }}
      id={wraperElId.current}
    >
      <MonacoEditor
        {
        ...mapProps
        }

        options={{
          formatOnPaste: true,
          formatOnType: true,
          wordWrap: 'on',         // 启用自动换行
          automaticLayout: true,
          ...(mapProps?.options || {})
        }}
        beforeMount={editorWillMount}
        onMount={onEditorMount}
      />
    </div>
  ) || (<div></div>)
})

export default memo(Index)
