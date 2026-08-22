UPDATE t_ddh_frame_k8s_service
SET artifact = JSON_SET(artifact,
    '$.kind', 'operator',
    '$.operator', CAST('{"group":"disaggregated.cluster.doris.com","kind":"DorisDisaggregatedCluster","plural":"dorisdisaggregatedclusters","monitorProfile":"doris-disaggregated","roles":[{"name":"fe","jobPattern":"-fe$"},{"name":"compute","jobPattern":"-cg\\\\d+$"}]}' AS JSON))
WHERE service_name = 'doris' AND artifact IS NOT NULL;

UPDATE t_ddh_frame_k8s_service
SET service_name = 'doris-disaggregated'
WHERE service_name = 'doris';
