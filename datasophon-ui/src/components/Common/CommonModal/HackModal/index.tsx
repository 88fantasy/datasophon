import { CloseOutlined } from "@ant-design/icons";
import { Modal } from "antd";
import styles from './index.module.less'

const Index = (props) => {

  let className = props.className || ''


  className += ' relative'

  return (
    <Modal
      {...props}
      className={className}

      title={false}
      closable={false}
      bodyStyle={{
        paddingLeft: 0,
        paddingRight: 0,
        paddingTop: 0
      }}
    >
      <div className={styles.s__title}>
        <div className="flex-1">
          {
            typeof props.title === 'function' ?
              props.title()
              : props.title
          }
        </div>
        {
          props.closable !== false && <CloseOutlined
            onClick={props.onCancel}
          />
        }
      </div>
      <div className="px-[16px]">
        {
          props?.children
        }
      </div>

    </Modal>
  )
}


export default Index;
