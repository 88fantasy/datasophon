/** @type {import('tailwindcss').Config} */
module.exports = {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        primary: {
          DEFAULT: '#1890ff',
          dark: '#096dd9',
        },
      },
    },
  },
  plugins: [],
  corePlugins: {
    preflight: false, // 禁用默认样式以避免与 Ant Design 冲突
  },
}