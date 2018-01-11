package com.blingbling.butterknife.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Created by BlingBling on 2018/1/4.
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.TYPE})
public @interface ContentView {
    int value();
}
