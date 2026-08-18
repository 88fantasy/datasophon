-- 存量库的 doris 框架服务定义补 kind=operator + operator 块（与 package/raw/meta/datacluster-k8s/
-- doris/manifest.yaml 保持一致），供接管扫描识别 DorisDisaggregatedCluster CR。
-- 原有 yaml 字段保留不变，新建 MANAGED 集群装 Doris 的安装路径不受影响。
--
-- 注意：jobPattern 正则里的 \d 在 JSON 文本里要转义成 \\d（两个反斜杠字符），
-- 而 MySQL 字符串字面量里每个字面反斜杠又要写成 \\，所以此处出现 \\\\d（四个反斜杠）。
UPDATE t_ddh_frame_k8s_service
SET artifact = '{"yaml":"ddc-cluster.yaml","kind":"operator","operator":{"group":"disaggregated.cluster.doris.com","version":"v1","kind":"DorisDisaggregatedCluster","plural":"dorisdisaggregatedclusters","monitorProfile":"doris-disaggregated","roles":[{"name":"fe","jobPattern":"-fe$"},{"name":"compute","jobPattern":"-cg\\\\d+$"}]}}'
WHERE service_name = 'doris';
