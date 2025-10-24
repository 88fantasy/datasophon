import { account } from "../../../../utils/account"
import { mapEmptyValueFn } from "../../../../utils/listUtils"


const col = [
    {
        value: 'username',
        label: '用户名称'
    },
    {
        value: 'email',
        label: '邮箱地址'
    },
    {
        value: 'phone',
        label: '手机号码'
    },
]
const Index = () => {
    const user = account.getUser()



    return (
        <div>
            {
                col.map(item => (
                    <div key={item.value}>
                        {item.label}: {mapEmptyValueFn(user[item.value])}
                    </div>
                ))
            }
        </div>


    )

}


export default Index