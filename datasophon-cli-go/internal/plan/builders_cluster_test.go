package plan

import (
	"testing"

	initcmd "github.com/88fantasy/datasophon/datasophon-cli-go/internal/cli/init"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

// TestBuildWorkerMysqlConf_ResolvesRealMysqlIpForAllNodes 覆盖回归场景：所有节点都要拿到
// 真实的 MySQL IP（而不是 hostname 或 127.0.0.1），否则 InitDbHookAction 在非 MySQL 节点上
// 连接失败——这正是 worker.properties 静态默认值一直踩的坑。
func TestBuildWorkerMysqlConf_ResolvesRealMysqlIpForAllNodes(t *testing.T) {
	cfg := stubCfg()
	ctx := stubCtx(cfg, t.TempDir())

	actions, err := buildWorkerMysqlConf(allNodes)(ctx)
	require.NoError(t, err)
	require.Len(t, actions, 2)

	for _, a := range actions {
		task, ok := a.Handler.(*initcmd.InitWorkerLocalProperties)
		require.True(t, ok)
		assert.Equal(t, "10.0.0.1", task.MysqlIP, "mysql.node=node1 应解析为其真实 IP，而非 hostname")
		assert.Equal(t, "mysql-root-pass", task.MysqlPassword)
		assert.Equal(t, ctx.InstallPath, task.InstallPath)
	}
}

func TestBuildWorkerMysqlConf_UnknownMysqlNodeReturnsError(t *testing.T) {
	cfg := stubCfg()
	cfg.Mysql.Node = "does-not-exist"
	ctx := stubCtx(cfg, t.TempDir())

	_, err := buildWorkerMysqlConf(allNodes)(ctx)
	require.Error(t, err)
}
