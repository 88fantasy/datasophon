import * as monaco from "monaco-editor";
import MonacoEditor, { loader } from "@monaco-editor/react";
import { shikiToMonaco } from "@shikijs/monaco";
import { createHighlighter } from "shiki";
import * as sqlFormatter from "sql-formatter";
import { language as sqlLanuage } from "monaco-editor/esm/vs/basic-languages/sql/sql";
import { clone } from "lodash-es";

loader.config({
  monaco,
});

export const languageConfig = {
  sql: {
    setMonarchTokensProvider: sqlLanuage,
    format(value) {
      console.log("format", value);
      try {
        return sqlFormatter.format(value);
      } catch (error) {
        console.warn(error);
        return value;
      }
    },
  },
  json: {},
  javascript: {},
  html: {},
};

export const invokeGenCustomTheme = async () => {
  const themes = [
    "one-dark-pro",
    "github-dark",
    "one-light",
    "light-plus",
    "dark-plus",
  ];

  return clone(themes);
};

export const invokeFormatByType = (conf = {}) => {
  return languageConfig[conf.language]?.format?.(conf.value) || conf.value;
};

export const invokeInitConfig = (editor = monaco, conf) => {
  console.log("editor", editor);
  if (editor.editor.invokeInitConfigInit) {
    return;
  }

  Object.keys(languageConfig).forEach((id) => {
    editor.languages.register({ id });

    let config = languageConfig[id] || {};

    if (typeof config === "function") {
      config = config(conf);
    }

    const { setMonarchTokensProvider, format } = config;
    if (setMonarchTokensProvider) {
      editor.languages.setMonarchTokensProvider(id, setMonarchTokensProvider);
    }

    if (format) {
      editor.languages.registerDocumentFormattingEditProvider(id, {
        provideDocumentFormattingEdits: (model) => {
          console.log("formatting");
          const originalText = model.getValue();
          const formattedText = format(originalText);
          // console.log('formattedText', formattedText);
          return [
            {
              range: model.getFullModelRange(),
              text: formattedText,
            },
          ];
        },
      });
    }
  });

  editor.editor.invokeInitConfigInit = true;
};

// console.log('monaco', monaco);

export async function invokeGetHighlighter(editor = monaco) {
  if (!editor.editor.invokeGetHighlighterInit) {
    // monacoLanguages.map(id => {
    //   editor.languages.register({ id });
    //   // monaco.languages.register({ id: 'typescript' })
    //   // monaco.languages.register({ id: 'javascript' })
    //   // monaco.languages.register({ id: 'json' })
    //   // monaco.languages.register({ id: 'json' })
    // });

    const langs = Object.keys(languageConfig).map((val) => {
      return languageConfig[val]?.langConfig || val;
    });

    console.log("langs", langs, invokeGenCustomTheme());
    const themes = await invokeGenCustomTheme();
    const highlighter = await createHighlighter({
      // engine: createOnigurumaEngine(),
      themes,
      langs,

      // .map(val => {
      //   return languageConfig[val].langConfig || val;
      // }),
      // defultColor: 'dark',
    });

    // highlighter.loadTheme({
    //   name: `123`,
    //   // type: 'dark',
    //   // include: val,
    //   // semanticHighlighting: true,

    //   settings: [
    //     {
    //       scope: ['variable.other.field.dql'],
    //       settings: {
    //         foreground: '#2CC377',
    //         fontStyle: 'bold',
    //       },
    //     },
    //     {
    //       scope: ['variable.other.dql'],
    //       settings: {
    //         foreground: '#28a745',
    //         fontStyle: 'bold',
    //       },
    //     },

    //     {
    //       // name: 'support.function.dql',
    //       scope: ['support.function.dql'],
    //       settings: {
    //         foreground: '#2CC377',
    //         fontStyle: 'bold',
    //       },
    //     },
    //     {
    //       scope: ['keyword.operator.dql'],
    //       settings: {
    //         foreground: '#17a2b8',
    //       },
    //     },
    //     {
    //       scope: ['comment.line.double-dash.dql'],
    //       settings: {
    //         foreground: '#6c757d',
    //       },
    //     },
    //   ],
    // });

    // console.log('dqlLanguageConfig', dqlLanguageConfig);
    // highlighter.loadLanguage(dqlLanguageConfig);

    // await Promise.all(
    //   Object.keys(languageConfig).map(k => {
    //     const api = languageConfig[k].langConfig;

    //     if (api) {
    //       console.log('112');
    //       const res = highlighter.loadLanguage(api);
    //       console.log('res', res);
    //       return res;
    //     }
    //     // else {
    //     //   return Promise.resolve();
    //     // }
    //   }),
    // );

    console.log("getLanguage", highlighter.getLoadedLanguages());

    // console.log(
    //   'highlighter',
    //   highlighter,
    //   highlighter.getTheme('one-dark-pro'),
    // );

    shikiToMonaco(highlighter, editor);

    // invokeInitConfig(editor);

    editor.editor.invokeGetHighlighterInit = true;
    // invokeInit.theme = highlighter.getTheme('one-dark-pro');
    editor.editor.highlighter = highlighter;

    // console.log(
    //   highlighter.codeToHtml(`MAX()`, {
    //     lang: 'dql',
    //     theme: 'dql',
    //   }),
    // );
  }

  return editor.editor.highlighter;

  // const monaco = useMonaco();
}

// invokeInit();

// invokeGetHighlighter();

export const invokeCheckMonacoEditorHadInit = (editor = monaco) => {
  return editor.editor.invokeGetHighlighterInit;
};
export const setTheme = async (monaco) => {
  // editor.setTheme(invokeInit.theme);
  const highlighter = await invokeGetHighlighter();
  console.log("highlighter", highlighter, monaco);
  shikiToMonaco(highlighter, monaco);
};

export const invokeInitEditor = (elId, opts) => {
  const optsMap = {};
  const fnMap = {};

  Object.keys(opts).map((val) => {
    if (typeof opts[val] === "function") {
      fnMap[val] = opts[val];
    } else {
      optsMap[val] = opts[val];
    }
  });

  // optsMap.value = JSON.parse(JSON.stringify(opts.value ||opts.defaultValue));
  // const value = optsMap.value;

  // delete optsMap.value;

  console.log("invokeInitEditor", elId, opts);
  const editor = monaco.editor.create(document.getElementById(elId)!, optsMap);

  // editor.setValue(value);
  editor.getAction("editor.action.formatDocument").run();
  Object.keys(fnMap).map((k) => {
    editor[k] = fnMap;
  });

  return {
    editor,
    monaco,
  };
};

// export function useMonacoEditor() {
//   const monaco = useMonaco();
//   monacoLanguages.map(id => {
//     monaco.languages.register({ id });
//     // monaco.languages.register({ id: 'typescript' })
//     // monaco.languages.register({ id: 'javascript' })
//     // monaco.languages.register({ id: 'json' })
//     // monaco.languages.register({ id: 'json' })
//   });
//   // monaco.languages.getLanguages().forEach(lang => {
//   //   console.log(lang.id);
//   // });

//   //   themes: {
//   //   light: 'one-light',
//   //   dark: 'material-theme-darker',
//   // },
//   // defaultColor: 'dark',

//   createHighlighter({
//     themes: ['one-dark-pro', 'one-light'],
//     langs: monacoLanguages,
//     // defultColor: 'dark',
//   }).then(highlighter => shikiToMonaco(highlighter, monaco));

//   return {
//     Editor,
//     monaco,
//   };
// }

// export type Monaco = typeof monaco;
// export { Editor };
// invokeGetHighlighter();

async function test() {
  const mylangGrammar = {
    name: "mylang",
    scopeName: "source.mylang",
    patterns: [
      {
        match: "\\btrue\\b|\\bfalse\\b",
        name: "constant.language.boolean.mylang",
      },
      { match: "#[a-zA-Z0-9]+", name: "keyword.control.mylang" },
      { match: '"[^"]*"', name: "string.quoted.double.mylang" },
    ],
    repository: {},
  };

  // 语言定义
  const mylangLanguage = {
    id: "mylang",
    name: "mylang",
    scopeName: "source.mylang",
    grammar: mylangGrammar,
    patterns: mylangGrammar.patterns,
    // aliases: ['mylang'],
  };

  const customTheme = {
    name: "my-custom-theme",
    settings: [
      {
        scope: ["constant.language.boolean.mylang"],
        settings: { foreground: "#FF6600", fontStyle: "bold" },
      },
      {
        scope: ["keyword.control.mylang"],
        settings: { foreground: "#00AAFF", fontStyle: "italic" },
      },
      {
        scope: ["string.quoted.double.mylang"],
        settings: { foreground: "#00CC00", fontStyle: "underline" },
      },
    ],
  };

  const highlighter = await createHighlighter({
    // engine: createOnigurumaEngine(),
    themes: [customTheme],
    langs: [mylangLanguage],

    // .map(val => {
    //   return languageConfig[val].langConfig || val;
    // }),
    // defultColor: 'dark',
  });

  console.log(highlighter.getLoadedThemes(), highlighter.getLoadedLanguages());

  // // 手动加载自定义主题
  // // const theme = await loadTheme(customTheme);
  // await highlighter.loadTheme(customTheme);

  // // 加载自定义语言
  // await highlighter.loadLanguage(mylangLanguage);

  const code = `
#start
let flag = true
let msg = "Hello MyLang"
#end
  `;

  const html = highlighter.codeToHtml(code, {
    lang: "mylang",
    theme: "my-custom-theme",
  });

  console.log(html);
}

window.testShiki = test;
// window.testShiki1 = ()=>{

// };

export { monaco };
export default MonacoEditor;
