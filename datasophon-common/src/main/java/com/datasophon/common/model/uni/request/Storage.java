package com.datasophon.common.model.uni.request;

import lombok.Data;

@Data
public class Storage {
    private String blobStoreName = "default";
    private Boolean strictContentTypeValidation = true;
    private WritePolicy writePolicy = WritePolicy.ALLOW_ONCE;
}
