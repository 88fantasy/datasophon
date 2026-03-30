package com.datasophon.common.k8s.spec.docker;

import lombok.Data;

@Data
public class ImageHostPlatform {
    private String architecture;
    private String os;


}