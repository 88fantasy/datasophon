import { memo } from "react"
const Index = ({
    children,
    title
}) => {
    return (
        <div className="flex flex-col flex-1 border border-gray-300 rounded overflow-hidden">
            <div className="text-xs text-gray-500 px-3 py-2 border-b border-gray-300 bg-gray-50">
                {
                    title
                }
            </div>
            <div className="flex-1">
                {children}
            </div>
        </div>
    )
}


export default memo(Index)