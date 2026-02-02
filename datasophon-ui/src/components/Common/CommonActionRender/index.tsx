import { Button, Space } from "antd";

export const actionReder = (obj = {}, field, actions, dom) => {
    const {
        formRef,
        key
    } = obj


    const moveUp = (index: number) => {
        if (index <= 0) return;
        const list = formRef.current?.getFieldValue(key) || [];
        const newList = [...list];
        [newList[index - 1], newList[index]] = [newList[index], newList[index - 1]];
        formRef.current?.setFieldValue(key, newList);
    };

    const moveDown = (index: number) => {
        const list = formRef.current?.getFieldValue(key) || [];
        if (index >= list.length - 1) return;
        const newList = [...list];
        [newList[index], newList[index + 1]] = [newList[index + 1], newList[index]];
        formRef.current?.setFieldValue(key, newList);
    };

    return [
        <Space key="actions">
            {field.name > 0 && (
                <Button type="link" size="small" onClick={() => moveUp(field.name)}>
                    上移
                </Button>
            )}
            {field.name < (formRef.current?.getFieldValue(key)?.length ?? 1) - 1 && (
                <Button type="link" size="small" onClick={() => moveDown(field.name)}>
                    下移
                </Button>
            )}
            {dom}
        </Space>,
    ]
}