declare namespace DATASOPHON {
  interface ApiResponse<T = any> {
    success: boolean;
    data: T;
    errorCode?: number;
    errorMessage?: string;
    showType?: string;
  }
}
