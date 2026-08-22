package com.datasophon.api.dto.instance;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

/**
 * 覆盖 P0-2 修复：{@code DatasourceSave.host} 会被
 * {@code DorisDatasourceDiscoveryService.jdbcUrl} 直接拼进 JDBC URL，Bean Validation
 * 必须先挡掉 {@code ? & # / 空格} 之类能改写连接串语义的字符。
 */
class K8sTakeoverDTOTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    @DisplayName("host 带连接串分隔符时校验不通过")
    void rejectsHostWithConnectionStringDelimiters() {
        assertThat(violationsFor("h?allowLoadLocalInfile=true#", 9030)).isNotEmpty();
        assertThat(violationsFor("host&extra=1", 9030)).isNotEmpty();
        assertThat(violationsFor("evil.com/../x", 9030)).isNotEmpty();
        assertThat(violationsFor("host with space", 9030)).isNotEmpty();
    }

    @Test
    @DisplayName("合法 hostname / IPv4 / IPv6 校验通过")
    void allowsLegitimateHosts() {
        assertThat(violationsFor("doris-fe.doris.svc.cluster.local", 9030)).isEmpty();
        assertThat(violationsFor("192.168.1.10", 9030)).isEmpty();
        assertThat(violationsFor("[::1]", 9030)).isEmpty();
    }

    @Test
    @DisplayName("端口超出 1-65535 范围时校验不通过")
    void rejectsPortOutOfRange() {
        assertThat(violationsFor("doris.example", 0)).isNotEmpty();
        assertThat(violationsFor("doris.example", 65536)).isNotEmpty();
        assertThat(violationsFor("doris.example", -1)).isNotEmpty();
    }

    private Set<ConstraintViolation<K8sTakeoverDTO.DatasourceSave>> violationsFor(String host, Integer port) {
        K8sTakeoverDTO.DatasourceSave req = new K8sTakeoverDTO.DatasourceSave();
        req.setHost(host);
        req.setPort(port);
        req.setPassword("secret");
        return validator.validate(req);
    }
}
