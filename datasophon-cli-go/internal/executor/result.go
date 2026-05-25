package executor

// ExecResult 对应 Java ExecResult POJO。
type ExecResult struct {
	Output    string // 标准输出 (对应 Java execOut)
	ErrOutput string // 错误输出 (对应 Java execErrOut)
	Success   bool   // 是否成功 (对应 Java execResult boolean)
}

func Succeed(out string) ExecResult {
	return ExecResult{Output: out, Success: true}
}

func Fail(errOut string) ExecResult {
	return ExecResult{ErrOutput: errOut, Success: false}
}
