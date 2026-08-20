-- 存量库的 doris 框架服务定义补 kind=operator + operator 块（与 package/raw/meta/datacluster-k8s/
-- doris/manifest.yaml 保持一致），供接管扫描识别 DorisDisaggregatedCluster CR。
--
-- t_ddh_frame_k8s_service 是多版本表（同一 service_name 可有多个 frame_code / service_version
-- 的行，见 FrameK8sServiceServiceImpl.listNewest 的去重逻辑），本条件只按 service_name 过滤，
-- 会命中所有 doris 行。整串覆盖 artifact 会丢失各行原本可能存在的其它字段，因此改用 JSON_SET
-- 只合并 kind / operator 两个新键，原有字段（含 yaml）逐行原样保留。
--
-- artifact 为 TEXT DEFAULT NULL：JSON_SET(NULL, ...) 恒返回 NULL，为空的行本就没有 yaml/helm，
-- 接管扫描/匹配逻辑读到 null artifact 会直接跳过该行，无需在此回填，故用 IS NOT NULL 排除。
--
-- 注意：jobPattern 正则里的 \d 在 JSON 文本里要转义成 \\d（两个反斜杠字符），
-- 而 MySQL 字符串字面量里每个字面反斜杠又要写成 \\，所以此处出现 \\\\d（四个反斜杠）。
UPDATE t_ddh_frame_k8s_service
SET artifact = JSON_SET(artifact,
    '$.kind', 'operator',
    '$.operator', CAST('{"group":"disaggregated.cluster.doris.com","version":"v1","kind":"DorisDisaggregatedCluster","plural":"dorisdisaggregatedclusters","monitorProfile":"doris-disaggregated","roles":[{"name":"fe","jobPattern":"-fe$"},{"name":"compute","jobPattern":"-cg\\\\d+$"}]}' AS JSON))
WHERE service_name = 'doris' AND artifact IS NOT NULL;
