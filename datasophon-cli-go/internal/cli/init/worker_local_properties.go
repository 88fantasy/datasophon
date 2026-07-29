package initcmd

import (
	"fmt"
	"log/slog"
	"path/filepath"

	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/executor"
	"github.com/spf13/cobra"
	"golang.org/x/crypto/ssh"
)

// InitWorkerLocalProperties 向节点写入 datasophon-worker/conf/worker.local.properties，
// 覆盖 conf/worker.properties 里写死的 mysql.ip=127.0.0.1、mysql.password=空——这两个默认值
// 假设 MySQL 与 Worker 同机部署；多节点集群里除 MySQL 所在节点外，InitDbHookAction（服务
// 安装时初始化数据库表）会用错误的 IP/空密码连接 MySQL，导致除巧合同机的节点外全部连接失败。
// worker.local.properties 不随 datasophon-worker.tar.gz 打包，提前写好后 Worker 解压时
// 不会覆盖它，能存活到 Worker 真正启动那一刻被 PropertyUtils 加载。
type InitWorkerLocalProperties struct {
	TaskBase
	InstallPath   string
	MysqlIP       string
	MysqlPassword string
}

func (t *InitWorkerLocalProperties) Name() string { return "写入 worker.local.properties" }

func (t *InitWorkerLocalProperties) Handle(client *ssh.Client, dryRun bool) error {
	return t.doRun(executor.NewSSHExecutor(client, dryRun))
}

func (t *InitWorkerLocalProperties) Command(dryRun *bool) *cobra.Command {
	cmd := &cobra.Command{
		Use:   "worker_local_properties",
		Short: "写入 datasophon-worker/conf/worker.local.properties（覆盖 mysql.ip/mysql.password 默认值）",
		RunE: func(cmd *cobra.Command, args []string) error {
			return runLocal(*dryRun, t.doRun)
		},
	}
	t.AddBaseFlags(cmd)
	cmd.Flags().StringVar(&t.InstallPath, "installPath", "", "节点安装根目录（必填）")
	cmd.Flags().StringVar(&t.MysqlIP, "mysqlIp", "", "真实 MySQL 所在节点 IP（必填）")
	cmd.Flags().StringVar(&t.MysqlPassword, "mysqlPassword", "", "MySQL 密码（必填）")
	_ = cmd.MarkFlagRequired("installPath")
	_ = cmd.MarkFlagRequired("mysqlIp")
	_ = cmd.MarkFlagRequired("mysqlPassword")
	return cmd
}

func (t *InitWorkerLocalProperties) doRun(exec executor.Executor) error {
	confPath := filepath.Join(t.InstallPath, "datasophon-worker/conf/worker.local.properties")
	conf := []string{
		fmt.Sprintf("mysql.ip=%s", t.MysqlIP),
		fmt.Sprintf("mysql.password=%s", t.MysqlPassword),
	}
	r := exec.WriteLines(conf, confPath)
	if !r.Success {
		return fmt.Errorf("写入 worker.local.properties 失败: %s", r.Output)
	}
	slog.Info("worker.local.properties 已写入", "path", confPath)
	return nil
}
