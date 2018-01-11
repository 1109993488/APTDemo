package com.blingbling.butterknife.compiler;

import com.blingbling.butterknife.annotation.ContentView;

import javax.lang.model.element.Element;

/**
 * Created by BlingBling on 2018/1/5.
 */

class LayoutViewBinding {

    private int mValue;

    public LayoutViewBinding(Element element) {
        ContentView contentView = element.getAnnotation(ContentView.class);
        mValue = contentView.value();
    }

    public int getValue() {
        return mValue;
    }
}
