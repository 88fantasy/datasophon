package osinfo

import (
	"testing"

	"github.com/stretchr/testify/assert"
)

func TestParseOsRelease(t *testing.T) {
	tests := []struct {
		name    string
		content string
		want    OsType
	}{
		{
			name: "ubuntu",
			content: `ID=ubuntu
VERSION_ID="22.04"`,
			want: OsTypeUbuntu22041LTS,
		},
		{
			name: "centos7",
			content: `ID="centos"
VERSION_ID="7"`,
			want: OsTypeCentos7,
		},
		{
			name: "centos8",
			content: `ID="centos"
VERSION_ID="8"`,
			want: OsTypeCentos8,
		},
		{
			name: "openEuler_2203",
			content: `ID="openEuler"
VERSION_ID="22.03"`,
			want: OsTypeOpenEuler220303SP3,
		},
		{
			name: "openEuler_2403",
			content: `ID="openEuler"
VERSION_ID="24.03"`,
			want: OsTypeOpenEuler24030LTS,
		},
		{
			name: "openEuler_unknown_version",
			content: `ID="openEuler"
VERSION_ID="21.09"`,
			want: OsTypeOpenEuler220303SP3,
		},
		{
			name: "kylin",
			content: `ID="Kylin"
VERSION_ID="V10"`,
			want: OsTypeKylinV10,
		},
		{
			name:    "empty_content",
			content: "",
			want:    OsTypeOther,
		},
		{
			name: "unknown_id",
			content: `ID="debian"
VERSION_ID="12"`,
			want: OsTypeOther,
		},
	}
	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			assert.Equal(t, tc.want, ParseOsRelease(tc.content))
		})
	}
}

func TestIsCentos(t *testing.T) {
	tests := []struct {
		os   OsType
		want bool
	}{
		{OsTypeCentos7, true},
		{OsTypeCentos8, true},
		{OsTypeCentosV10, true},
		{OsTypeOpenEuler220303SP3, true},
		{OsTypeOpenEuler24030LTS, true},
		{OsTypeKylinV10, true},
		{OsTypeOther, true},
		{OsTypeUbuntu22041LTS, false},
		{OsTypeAuto, false},
	}
	for _, tc := range tests {
		t.Run(string(tc.os), func(t *testing.T) {
			assert.Equal(t, tc.want, tc.os.IsCentos())
		})
	}
}

func TestIsUbuntu(t *testing.T) {
	tests := []struct {
		os   OsType
		want bool
	}{
		{OsTypeUbuntu22041LTS, true},
		{OsTypeCentos7, false},
		{OsTypeOther, false},
		{OsTypeOpenEuler220303SP3, false},
		{OsTypeAuto, false},
	}
	for _, tc := range tests {
		t.Run(string(tc.os), func(t *testing.T) {
			assert.Equal(t, tc.want, tc.os.IsUbuntu())
		})
	}
}
