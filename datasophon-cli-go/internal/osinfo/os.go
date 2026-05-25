package osinfo

import "strings"

type OsType string

const (
	OsTypeOther              OsType = "other"
	OsTypeCentos7            OsType = "centos-7"
	OsTypeCentos8            OsType = "centos-8"
	OsTypeCentosV10          OsType = "centos-V10"
	OsTypeKylinV10           OsType = "kylin-V10"
	OsTypeOpenEuler220303SP3 OsType = "openEuler-22.03-LTS-SP3"
	OsTypeOpenEuler220303SP4 OsType = "openEuler-22.03-LTS-SP4"
	OsTypeOpenEuler220303SP1 OsType = "openEuler-22.03-LTS-SP1"
	OsTypeOpenEuler24030LTS  OsType = "openEuler-24.03-LTS"
	OsTypeUbuntu22041LTS     OsType = "Ubuntu-22.04.1-LTS"
	OsTypeAuto               OsType = "auto"
)

// IsCentos 对齐 Java OsType.isCentos —— openEuler/kylin/other 都走 yum 路径。
func (o OsType) IsCentos() bool {
	s := string(o)
	return strings.HasPrefix(s, "centos") ||
		strings.HasPrefix(s, "openEuler") ||
		strings.HasPrefix(s, "kylin") ||
		s == "other"
}

// IsUbuntu 对齐 Java OsType.isUnbuntu（拼写错误保留在注释，Go 版本用正确拼写）。
func (o OsType) IsUbuntu() bool {
	return strings.HasPrefix(string(o), "Ubuntu")
}

// ParseOsRelease 从 /etc/os-release 的 ID 和 VERSION_ID 字段推断 OsType。
func ParseOsRelease(content string) OsType {
	var id, versionID string
	for _, line := range strings.Split(content, "\n") {
		line = strings.TrimSpace(line)
		if strings.HasPrefix(line, "ID=") {
			id = strings.Trim(strings.TrimPrefix(line, "ID="), `"`)
		} else if strings.HasPrefix(line, "VERSION_ID=") {
			versionID = strings.Trim(strings.TrimPrefix(line, "VERSION_ID="), `"`)
		}
	}

	lower := strings.ToLower(id)
	switch {
	case strings.Contains(lower, "ubuntu"):
		return OsTypeUbuntu22041LTS
	case strings.Contains(lower, "openeuler"):
		switch versionID {
		case "22.03":
			return OsTypeOpenEuler220303SP3
		case "24.03":
			return OsTypeOpenEuler24030LTS
		default:
			return OsTypeOpenEuler220303SP3
		}
	case strings.Contains(lower, "kylin"):
		return OsTypeKylinV10
	case strings.Contains(lower, "centos"):
		if strings.HasPrefix(versionID, "7") {
			return OsTypeCentos7
		}
		return OsTypeCentos8
	default:
		return OsTypeOther
	}
}
