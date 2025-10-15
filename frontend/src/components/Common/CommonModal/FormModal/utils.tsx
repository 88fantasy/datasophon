export const invokeRenderForm = (formConfig) => {
    return formConfig.map(val => {
        const Com = val.com
        return <Com
            key={val.name}
            {...val}
        />
    })
}