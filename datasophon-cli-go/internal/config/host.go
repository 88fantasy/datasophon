package config

// Host 对应 Java Host model。
type Host struct {
	ProjectEnvDetailID int64  `yaml:"projectEnvDetailId"`
	IP                 string `yaml:"ip"`
	Port               int    `yaml:"port"`
	User               string `yaml:"user"`
	Password           string `yaml:"password"`
	Hostname           string `yaml:"hostname"`
}
