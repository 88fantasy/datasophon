package com.datasophon.api.dag;


public interface NodeExecutionCallback {

    void onSuccess(String result);

    void onFailure(Throwable error);

}