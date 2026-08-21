-- doris 框架服务改名为 doris-disaggregated（与新增的 doris-coupled 对称命名，见
-- package/raw/meta/datacluster-k8s/doris-coupled/manifest.yaml）。
--
-- 必须原地 UPDATE 而不是让 LoadServiceMeta 按新 manifest 名字插一条新行：已经接管注册过
-- 存算分离 Doris 实例的集群，其 K8sServiceInstance.serviceId 外键引用着这一行的 id——插新
-- 行会让旧行永久孤立、且同一个 CRD（disaggregated.cluster.doris.com/DorisDisaggregatedCluster）
-- 会同时挂在两行框架服务定义下，接管扫描时对同一个 CRD 建出两条 crdKey，行为不确定。
--
-- K8sServiceInstance 本身不存 serviceName（只存 serviceId），已注册实例的显示名称是查询时
-- 实时 JOIN t_ddh_frame_k8s_service 得到的，原地改名后自动生效，不需要再改实例表数据。
UPDATE t_ddh_frame_k8s_service
SET service_name = 'doris-disaggregated'
WHERE service_name = 'doris';
