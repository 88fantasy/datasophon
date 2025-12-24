

import { useParams } from 'react-router'
import ResultModal from '../../../../../../Proxy/components/ResultModal/index'
const Index = (props) => {


    const {
        clusterId
    } = props
    return <ResultModal
        clusterId={clusterId}
        className="!mb-[20px] h-[40vh]"
        y="22vh"
    />
}


export default Index