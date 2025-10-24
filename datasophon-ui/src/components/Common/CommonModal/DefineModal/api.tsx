import Component from '.';
import apiHook from '../apiHook';

export const invokeHackerConfig = (config, getComponent) => {
  config = config.config || config.props;
  config.invokeInjectConfirmEvent = fn => {
    const fnCall = () => {
      const com = getComponent();
      if (com) {
        const props = com.props;
        const conf = props.config || props;
        conf.dialogConfig &&
          (conf.dialogConfig.onOk = async () => {
            const res = await fn();
            console.log('invokeHackerConfig', res);

            res && conf.onOkClick && conf.onOkClick(res);

            return res;
          });
      } else {
        fnCall();
      }

    };

    fnCall();

  };

};


const index = (...args) => {

  return apiHook(Component)(...args);
};

export default index;
