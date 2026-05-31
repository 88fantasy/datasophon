package initcmd

import (
	"io"
	"strings"
	"testing"

	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/executor"
	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/osinfo"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

// ─── recording mock Executor ─────────────────────────────────────────────────

type recordingExec struct {
	commands   []string
	arch       osinfo.ArchType
	failCmds   []string        // substrings: any command containing one of these fails
	existPaths map[string]bool // path → exists
}

func (m *recordingExec) ExecShell(cmd string) executor.ExecResult {
	m.commands = append(m.commands, cmd)
	for _, fail := range m.failCmds {
		if strings.Contains(cmd, fail) {
			return executor.Fail("failed: " + cmd)
		}
	}
	return executor.Succeed("")
}

func (m *recordingExec) Exists(path string) executor.ExecResult {
	if m.existPaths[path] {
		return executor.Succeed("exists")
	}
	return executor.ExecResult{}
}

func (m *recordingExec) SendFile(_, _ string, _ bool) executor.ExecResult { return executor.Succeed("") }
func (m *recordingExec) SendDir(_, _ string, _ bool) executor.ExecResult  { return executor.Succeed("") }
func (m *recordingExec) GetFileString(_ string) executor.ExecResult        { return executor.Succeed("") }
func (m *recordingExec) WriteFromStream(_ io.Reader, _ string) executor.ExecResult {
	return executor.Succeed("")
}
func (m *recordingExec) WriteLines(_ []string, _ string) executor.ExecResult { return executor.Succeed("") }
func (m *recordingExec) GetArch() osinfo.ArchType                            { return m.arch }
func (m *recordingExec) GetOs() osinfo.OsType                                { return osinfo.OsTypeCentos7 }

// hasCmd reports whether any recorded command contains the given substring.
func hasCmd(cmds []string, substr string) bool {
	for _, c := range cmds {
		if strings.Contains(c, substr) {
			return true
		}
	}
	return false
}

// allPackageExist returns an existPaths map where all three packages resolve to true.
func allPackageExist(pkgPath, containerdTar, runcBin, cniTar string) map[string]bool {
	return map[string]bool{
		pkgPath + "/" + containerdTar: true,
		pkgPath + "/" + runcBin:      true,
		pkgPath + "/" + cniTar:       true,
	}
}

// ─── tests ───────────────────────────────────────────────────────────────────

func TestInitContainerd_DisabledSkip(t *testing.T) {
	m := &recordingExec{arch: osinfo.ArchX86_64, existPaths: map[string]bool{}}
	task := &InitContainerd{EnableK8sCluster: false}
	err := task.doRun(m)
	assert.NoError(t, err)
	assert.Empty(t, m.commands, "enableK8sCluster=false 时不应执行任何命令")
}

func TestInitContainerd_AlreadyInstalledSkip(t *testing.T) {
	// containerd --version succeeds → already installed, KubernetesForce=false → skip
	m := &recordingExec{arch: osinfo.ArchX86_64, existPaths: map[string]bool{}}
	task := &InitContainerd{EnableK8sCluster: true, KubernetesForce: false}
	err := task.doRun(m)
	assert.NoError(t, err)
	require.Equal(t, 1, len(m.commands))
	assert.Equal(t, "containerd --version", m.commands[0])
}

func TestInitContainerd_ForceReinstall(t *testing.T) {
	const pkgPath = "/pkg"
	const ctrdTar = "containerd.tar"
	const runcBin = "runc.amd64"
	const cniTar = "cni.tgz"

	m := &recordingExec{
		arch:       osinfo.ArchX86_64,
		existPaths: allPackageExist(pkgPath, ctrdTar, runcBin, cniTar),
	}
	// containerd --version succeeds (installed), force=true → remove then reinstall
	task := &InitContainerd{
		EnableK8sCluster: true,
		KubernetesForce:  true,
		PackagePath:      pkgPath,
		ContainerdX86Tar: ctrdTar,
		ContainerdArmTar: "containerd-arm.tar",
		RuncX86Bin:       runcBin,
		RuncArmBin:       "runc.arm64",
		CniX86Tar:        cniTar,
		CniArmTar:        "cni-arm.tgz",
	}
	err := task.doRun(m)
	assert.NoError(t, err)
	assert.True(t, hasCmd(m.commands, "systemctl stop containerd"), "应先停止 containerd")
	assert.True(t, hasCmd(m.commands, "rm -rf /var/lib/containerd"), "应删除 containerd 数据目录")
	assert.True(t, hasCmd(m.commands, "tar Cxzvf /usr/local"), "应重新解压 containerd")
	assert.True(t, hasCmd(m.commands, "systemctl enable --now containerd"), "应重新启动 containerd")
}

func TestInitContainerd_HappyPath_x86(t *testing.T) {
	const pkgPath = "/packages"
	const ctrdTar = "containerd-2.3.0-linux-amd64.tar.gz"
	const runcBin = "runc.amd64"
	const cniTar = "cni-plugins-linux-amd64-v1.6.0.tgz"

	m := &recordingExec{
		arch:       osinfo.ArchX86_64,
		failCmds:   []string{"containerd --version"}, // not yet installed
		existPaths: allPackageExist(pkgPath, ctrdTar, runcBin, cniTar),
	}
	task := &InitContainerd{
		EnableK8sCluster: true,
		PackagePath:      pkgPath,
		ContainerdX86Tar: ctrdTar,
		ContainerdArmTar: "containerd-2.3.0-linux-arm64.tar.gz",
		RuncX86Bin:       runcBin,
		RuncArmBin:       "runc.arm64",
		CniX86Tar:        cniTar,
		CniArmTar:        "cni-plugins-linux-arm64-v1.6.0.tgz",
	}
	err := task.doRun(m)
	assert.NoError(t, err)
	assert.True(t, hasCmd(m.commands, "tar Cxzvf /usr/local "+pkgPath+"/"+ctrdTar), "应使用 x86 containerd 包")
	assert.True(t, hasCmd(m.commands, "install -m 755 "+pkgPath+"/"+runcBin+" /usr/local/sbin/runc"), "应使用 x86 runc 包")
	assert.True(t, hasCmd(m.commands, "tar Cxzvf /opt/cni/bin "+pkgPath+"/"+cniTar), "应使用 x86 CNI 包")
	assert.True(t, hasCmd(m.commands, "systemctl enable --now containerd"))
}

func TestInitContainerd_HappyPath_arm64(t *testing.T) {
	const pkgPath = "/packages"
	const ctrdTar = "containerd-2.3.0-linux-arm64.tar.gz"
	const runcBin = "runc.arm64"
	const cniTar = "cni-plugins-linux-arm64-v1.6.0.tgz"

	m := &recordingExec{
		arch:       osinfo.ArchAarch64,
		failCmds:   []string{"containerd --version"},
		existPaths: allPackageExist(pkgPath, ctrdTar, runcBin, cniTar),
	}
	task := &InitContainerd{
		EnableK8sCluster: true,
		PackagePath:      pkgPath,
		ContainerdX86Tar: "containerd-2.3.0-linux-amd64.tar.gz",
		ContainerdArmTar: ctrdTar,
		RuncX86Bin:       "runc.amd64",
		RuncArmBin:       runcBin,
		CniX86Tar:        "cni-plugins-linux-amd64-v1.6.0.tgz",
		CniArmTar:        cniTar,
	}
	err := task.doRun(m)
	assert.NoError(t, err)
	assert.True(t, hasCmd(m.commands, "tar Cxzvf /usr/local "+pkgPath+"/"+ctrdTar), "应使用 arm64 containerd 包")
	assert.True(t, hasCmd(m.commands, "install -m 755 "+pkgPath+"/"+runcBin+" /usr/local/sbin/runc"), "应使用 arm64 runc 包")
	assert.True(t, hasCmd(m.commands, "tar Cxzvf /opt/cni/bin "+pkgPath+"/"+cniTar), "应使用 arm64 CNI 包")
}

func TestInitContainerd_ConfigSystemdCgroup(t *testing.T) {
	const pkgPath = "/packages"
	m := &recordingExec{
		arch:       osinfo.ArchX86_64,
		failCmds:   []string{"containerd --version"},
		existPaths: allPackageExist(pkgPath, "ctrd.tar", "runc.amd64", "cni.tgz"),
	}
	task := &InitContainerd{
		EnableK8sCluster: true,
		PackagePath:      pkgPath,
		ContainerdX86Tar: "ctrd.tar",
		ContainerdArmTar: "ctrd-arm.tar",
		RuncX86Bin:       "runc.amd64",
		RuncArmBin:       "runc.arm64",
		CniX86Tar:        "cni.tgz",
		CniArmTar:        "cni-arm.tgz",
	}
	err := task.doRun(m)
	assert.NoError(t, err)
	// 必须先生成 config.toml 再 sed 开启 SystemdCgroup
	assert.True(t, hasCmd(m.commands, "containerd config default > /etc/containerd/config.toml"), "应生成默认 config.toml")
	assert.True(t, hasCmd(m.commands, "SystemdCgroup = true"), "应将 SystemdCgroup 设为 true")
}

func TestInitContainerd_MissingPackage(t *testing.T) {
	m := &recordingExec{
		arch:       osinfo.ArchX86_64,
		failCmds:   []string{"containerd --version"},
		existPaths: map[string]bool{}, // no packages staged
	}
	task := &InitContainerd{
		EnableK8sCluster: true,
		PackagePath:      "/packages",
		ContainerdX86Tar: "containerd.tar",
		ContainerdArmTar: "containerd-arm.tar",
		RuncX86Bin:       "runc.amd64",
		RuncArmBin:       "runc.arm64",
		CniX86Tar:        "cni.tgz",
		CniArmTar:        "cni-arm.tgz",
	}
	err := task.doRun(m)
	require.Error(t, err)
	assert.Contains(t, err.Error(), "安装包不存在")
}

func TestInitContainerd_OfflineRegistryConfig(t *testing.T) {
	const pkgPath = "/packages"
	const registryIP = "192.168.1.100"
	const dockerPort = 8083

	m := &recordingExec{
		arch:       osinfo.ArchX86_64,
		failCmds:   []string{"containerd --version"},
		existPaths: allPackageExist(pkgPath, "ctrd.tar", "runc.amd64", "cni.tgz"),
	}
	task := &InitContainerd{
		EnableK8sCluster: true,
		Offline:          true,
		PackagePath:      pkgPath,
		DockerHTTPPort:   dockerPort,
		ContainerdX86Tar: "ctrd.tar",
		ContainerdArmTar: "ctrd-arm.tar",
		RuncX86Bin:       "runc.amd64",
		RuncArmBin:       "runc.arm64",
		CniX86Tar:        "cni.tgz",
		CniArmTar:        "cni-arm.tgz",
	}
	task.TaskBase.EnableRegistry = true
	task.TaskBase.RegistryIP = registryIP

	err := task.doRun(m)
	assert.NoError(t, err)
	// config_path should be set in config.toml
	assert.True(t, hasCmd(m.commands, `config_path = "/etc/containerd/certs.d"`), "应设置 config_path")
	// Nexus registry direct-access cert dir
	assert.True(t, hasCmd(m.commands, "mkdir -p /etc/containerd/certs.d/192.168.1.100:8083"), "应创建 Nexus cert dir")
	// Standard registry mirrors
	assert.True(t, hasCmd(m.commands, "mkdir -p /etc/containerd/certs.d/docker.io"), "应配置 docker.io mirror")
	assert.True(t, hasCmd(m.commands, "mkdir -p /etc/containerd/certs.d/registry.k8s.io"), "应配置 registry.k8s.io mirror")
}

func TestInitContainerd_OfflineSkippedWhenOnline(t *testing.T) {
	// Offline=false → registry configuration must NOT be triggered
	const pkgPath = "/packages"
	m := &recordingExec{
		arch:       osinfo.ArchX86_64,
		failCmds:   []string{"containerd --version"},
		existPaths: allPackageExist(pkgPath, "ctrd.tar", "runc.amd64", "cni.tgz"),
	}
	task := &InitContainerd{
		EnableK8sCluster: true,
		Offline:          false,
		PackagePath:      pkgPath,
		DockerHTTPPort:   8083,
		ContainerdX86Tar: "ctrd.tar",
		ContainerdArmTar: "ctrd-arm.tar",
		RuncX86Bin:       "runc.amd64",
		RuncArmBin:       "runc.arm64",
		CniX86Tar:        "cni.tgz",
		CniArmTar:        "cni-arm.tgz",
	}
	task.TaskBase.EnableRegistry = true
	task.TaskBase.RegistryIP = "192.168.1.100"

	err := task.doRun(m)
	assert.NoError(t, err)
	assert.False(t, hasCmd(m.commands, `config_path = "/etc/containerd/certs.d"`), "非离线环境不应写 config_path")
}
