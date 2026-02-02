

import DagListModal from '../../../../../../Proxy/components/DagListModal'
const Index = (props) => {


    const {
        clusterId
    } = props
    return <DagListModal
        clusterId={clusterId}
        className="!mb-[20px] min-h-[40vh]"
        y="22vh"
    />
}


export default Index