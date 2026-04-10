

import DagListModal from '../../../../../../Proxy/components/DagListModal'
const Index = (props) => {


    const {
        clusterId,
        current,
        index,
        memoCluster
    } = props



    return current === index && <DagListModal
        clusterId={clusterId}
        className="!mb-[20px] min-h-[40vh]"
        memoCluster={memoCluster}
        y="22vh"
    />
}


export default Index