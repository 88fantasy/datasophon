package osinfo

import "strings"

type ArchType string

const (
	ArchX86_64  ArchType = "x86_64"
	ArchAarch64 ArchType = "aarch64"
	ArchOther   ArchType = "other"
)

func ParseArch(raw string) ArchType {
	switch strings.TrimSpace(raw) {
	case "x86_64":
		return ArchX86_64
	case "aarch64", "arm64":
		return ArchAarch64
	default:
		return ArchOther
	}
}
