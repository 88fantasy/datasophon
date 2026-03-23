package com.datasophon.dao.model.extrepo;

/**
 * @author zhanghuangbin
 */
public interface ServiceResource<T> {

    String getName();

    String getVersion();

    default T unwrap() {
        return (T) this;
    }
}
