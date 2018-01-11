package com.blingbling.butterknife.api;

/**
 * Created by BlingBling on 2018/1/4.
 */

public interface Unbinder {
    void unbind();

    Unbinder EMPTY = new Unbinder() {
        @Override
        public void unbind() { }
    };

}
