import { ConfigProvider } from 'antd';
import zhCN from 'antd/es/locale/zh_CN';
import 'dayjs/locale/zh-cn';
// import 'antd/dist/antd.variable.min.css';


// ConfigProvider.config({
//   // prefixCls: "dfmm",
//   theme: {
//     primaryColor: 'rgba(38,193,194,0.8);',
//     buton:'linear-gradient(135deg, rgba(38, 175, 201, 0.9) 0%, rgba(38, 193, 194, 0.9) 100%);'
//   },
// });
const index = <P extends object>(Com: React.ComponentType<P>) => {
  return (props: P) => {
    return (
      <ConfigProvider
        locale={zhCN}
        // prefixCls="dfmm"
      >

        <Com {...props} />
      </ConfigProvider>


    );
  }
};

export default index;
