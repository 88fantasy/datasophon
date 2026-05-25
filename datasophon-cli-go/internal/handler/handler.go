package handler

import "golang.org/x/crypto/ssh"

// Handler 对应 Java InitNodeHandler 接口。
type Handler interface {
	Name() string
	Handle(client *ssh.Client, dryRun bool) bool
}
