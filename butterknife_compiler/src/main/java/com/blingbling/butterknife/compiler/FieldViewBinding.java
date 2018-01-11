package com.blingbling.butterknife.compiler;

import com.blingbling.butterknife.annotation.BindView;

import javax.lang.model.element.Element;
import javax.lang.model.type.TypeMirror;

/**
 * Created by BlingBling on 2018/1/5.
 */

class FieldViewBinding {

    private String mName;
    private int mValue;
    private TypeMirror mType;

    public FieldViewBinding(Element element) {
        BindView fieldViewBinding = element.getAnnotation(BindView.class);
        mName = element.getSimpleName().toString();
        mType = element.asType();
        mValue = fieldViewBinding.value();
    }

    public String getName() {
        return mName;
    }

    public TypeMirror getType() {
        return mType;
    }

    public int getValue() {
        return mValue;
    }
}
