package com.datasophon.worker.strategy.resource;

import com.datasophon.common.utils.ExecResult;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class EmptyStrategy extends ResourceStrategy {

    @Override
    public String type() {
        return "empty";
    }

    @Override
    public ExecResult exec() {
        return ExecResult.success();
    }
}
