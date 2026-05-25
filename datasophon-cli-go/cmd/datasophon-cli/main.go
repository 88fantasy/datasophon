package main

import (
	"fmt"
	"log/slog"
	"os"

	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/cli"
)

func main() {
	// 对应 Java Main.java:25-28 的 DDH_HOME 强制检查
	if os.Getenv("DDH_HOME") == "" {
		fmt.Fprintln(os.Stderr,
			"DDH_HOME is empty, please set DDH_HOME using 'export DDH_HOME=xxx' command")
		os.Exit(1)
	}

	// 默认 slog 输出到 stderr，level INFO
	slog.SetDefault(slog.New(slog.NewTextHandler(os.Stderr, &slog.HandlerOptions{
		Level: slog.LevelInfo,
	})))

	if err := cli.Root().Execute(); err != nil {
		os.Exit(1)
	}
}
