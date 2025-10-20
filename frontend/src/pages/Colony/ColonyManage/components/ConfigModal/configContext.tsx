import { createContext, useContext } from "react";



export const ConfigContext = createContext({})

export const useConfigContext = () => useContext(ConfigContext)