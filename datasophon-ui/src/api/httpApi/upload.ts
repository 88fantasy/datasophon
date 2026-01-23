import { VUE_APP_PUBLIC_PATH } from "../../config";
import paths from "../baseUrl"; // 后台服务地址

let path = VUE_APP_PUBLIC_PATH + paths.path();

export default {
  upload: path + "/tempfile/upload",
  validMetaFile: path + "/extrepo/validMetaFile",
  validatePkgFile: path + "/extrepo/validatePkgFile",
  importCmp: path + "/extrepo/importCmp",
  queryProgress: path + "/extrepo/queryProgress",
  deploy: path + "/extrepo/deploy",
  getDeployProgressDAG: path + "/extrepo/getDeployProgressDAG",
  getDeployProgressDAG2: path + "/extrepo/getDeployProgressDAG2",
  uploadChunk: path + "/tempfile/uploadChunk",
  createShardUploadTask: path + "/tempfile/createShardUploadTask",
  mergeChunk: path + "/tempfile/mergeChunk",
  queryMergeProgress: path + "/tempfile/queryMergeProgress",
  isChunkUploaded: path + "/tempfile/isChunkUploaded",
};
