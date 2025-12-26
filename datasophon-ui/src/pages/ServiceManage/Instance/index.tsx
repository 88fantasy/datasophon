import { lazy, memo } from "react"
// import InstanceProxy from "./InstanceProxy"
import Overview from "../../Cluster/Overview"
import { useLocation, useNavigate } from "react-router";

const InstanceProxy = lazy(() => import('./InstanceProxy'));
const Index = () => {
    const location = useLocation();
    const isOverview = /Instance\/Overview$/gi.test(location.pathname)


    if (!isOverview) {
        return <InstanceProxy />
    } else {
        return <Overview />
    }
}


export default memo(Index)