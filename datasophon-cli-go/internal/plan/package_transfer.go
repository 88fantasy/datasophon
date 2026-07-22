package plan

import (
	"fmt"

	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/executor"
	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/shellutil"
)

// ensurePackageOnTarget keeps the existing shared-path fast path, while also
// supporting infrastructure tasks placed on a remote node without a shared filesystem.
func ensurePackageOnTarget(exec executor.Executor, packagePath, component string) error {
	if executor.InspectPath(exec, packagePath) == executor.PathExists {
		return nil
	}
	if result := exec.SendFile(packagePath, packagePath, false); !result.Success {
		return fmt.Errorf("分发 %s 安装包失败 %s: %s", component, packagePath, result.ErrOutput)
	}
	return nil
}

// switchVersionSymlink changes a stable executable entry point without an
// interval where the link is missing. It is shared by install, upgrade and
// rollback paths for bootstrap-managed binaries.
func switchVersionSymlink(exec executor.Executor, linkPath, targetPath string) executor.ExecResult {
	nextLink := linkPath + ".next"
	command := fmt.Sprintf("ln -sfn %s %s && mv -Tf %s %s",
		shellutil.Quote(targetPath), shellutil.Quote(nextLink),
		shellutil.Quote(nextLink), shellutil.Quote(linkPath))
	return exec.ExecShell(command)
}
