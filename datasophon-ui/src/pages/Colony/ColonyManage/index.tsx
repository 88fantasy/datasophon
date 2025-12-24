import React, { useEffect, useState } from 'react';
import { axiosPost } from '../../../api/request';
import { API } from '../../../api';
import { Row } from 'antd';
import Card from './components/Card';

const Index: React.FC = () => {

    const [state, setState] = useState()
    const invokeInit = async () => {
        const res = await axiosPost(API.getColonyList, {})

        if (res.code === 200) {
            res.data.push({
                add: true
            })
            setState(res.data)
        }
    }

    useEffect(() => {
        invokeInit()
    }, [])

    return state && (
        <Row gutter={16}>
            {
                (state || []).map((val, index) => {
                    return (
                        <Card
                            key={`${index}_${val.id}`}
                            val={val}
                            invokeInit={invokeInit}
                        />
                    )
                })
            }
        </Row>
    )

};

export default Index;