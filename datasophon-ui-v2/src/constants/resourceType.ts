/** K8s 资源类型常量 */
export const T_POD = 'pod';
export const T_DEPLOYMENT = 'deployment';
export const T_SERVICE = 'service';
export const T_INGRESS = 'ingress';
export const T_CONFIGMAP = 'configmap';

/** 资源类型 → 显示标签 */
export const RESOURCE_TYPE_LABELS: Record<string, string> = {
  [T_POD]: 'Pod',
  [T_DEPLOYMENT]: 'Deployment',
  [T_SERVICE]: 'Service',
  [T_INGRESS]: 'Ingress',
  [T_CONFIGMAP]: 'ConfigMap',
};
