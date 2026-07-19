package shellutil

import "strings"

// Quote 返回 POSIX shell 安全的单引号字符串。
func Quote(value string) string {
	return "'" + strings.ReplaceAll(value, "'", `'\''`) + "'"
}
