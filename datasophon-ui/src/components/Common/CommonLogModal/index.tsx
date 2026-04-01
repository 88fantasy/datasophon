import { isEmpty } from "../../../utils/util"
import { T_LOG, T_VIEWER, T_HELM } from "./api"
// import DirViewer from "./DirViewer"
import Log from "./Log"
import Helm from "./Helm"

const Index = (props) => {


    const {
        type
    } = props
    if (type === T_LOG || isEmpty(type)) {
        return <Log {...props} />
    } else if (type === T_VIEWER) {
        // return <DirViewer {...props} />
    } else if (type === T_HELM) {
        return <Helm {...props} />
    }
}


export default Index