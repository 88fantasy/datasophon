

import { useParams } from 'react-router'
import DagListModal from '../../../../../../Proxy/components/DagListModal'
const Index = (props) => {


    const {
        clusterId
    } = props
    return <DagListModal
        clusterId={clusterId}
        className="!mb-[20px] h-[40vh]"
        y="22vh"
    />
}


export default Index