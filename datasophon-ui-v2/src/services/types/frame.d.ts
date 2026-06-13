declare namespace DATASOPHON {
  /** GET /v2/frame/list 响应体 */
  interface FrameResponse {
    id: number;
    frameName: string;
    frameCode: string;
    frameVersion: string;
  }

  /** @deprecated 旧 DAO 镜像，迁移完成后删除，暂保留供未迁移文件使用 */
  interface FrameInfo {
    id: number;
    frameName: string;
    frameCode: string;
    frameVersion: string;
  }

  interface FrameServiceItem {
    id: number;
    frameId: number;
    frameCode: string;
    serviceName: string;
    serviceVersion: string;
    serviceDesc?: string;
  }

  interface FrameK8sServiceItem {
    id: number;
    frameId: number;
    serviceName: string;
    serviceVersion: string;
    serviceDesc?: string;
    supportArtifacts?: string[];
  }

  interface FrameWithServices extends FrameInfo {
    frameServiceList?: FrameServiceItem[];
    frameK8sServiceList?: FrameK8sServiceItem[];
  }

  interface FrameServiceItemResponse {
    id: number;
    frameId: number;
    frameCode: string;
    serviceName: string;
    label?: string;
    serviceVersion?: string;
    serviceDesc?: string;
    installed?: boolean;
  }

  interface FrameK8sServiceItemResponse {
    id: number;
    frameId: number;
    serviceName: string;
    serviceVersion?: string;
    serviceDesc?: string;
    supportArtifacts?: string[];
  }

  interface FrameWithServicesResponse {
    id: number;
    frameName: string;
    frameCode: string;
    frameVersion: string;
    frameServiceList?: FrameServiceItemResponse[];
    frameK8sServiceList?: FrameK8sServiceItemResponse[];
  }
}
