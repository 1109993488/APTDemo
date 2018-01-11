package com.blingbling.butterknife.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Created by BlingBling on 2018/1/9.
 */

@Retention(RetentionPolicy.CLASS)
@Target({ElementType.METHOD})
public @interface OnClick {
    int[] value();
}
