package com.blingbling.butterknife.compiler;

import com.blingbling.butterknife.annotation.OnClick;

import java.util.List;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;

/**
 * Created by BlingBling on 2018/1/5.
 */

class MethodViewBinding {

    private String mName;
    private int[] mValue;
    private boolean mParameterError;
    private boolean mHasViewParameter;

    public MethodViewBinding(Element element) {
        OnClick annotation = element.getAnnotation(OnClick.class);
        mName = element.getSimpleName().toString();
        mValue = annotation.value();

        ExecutableElement executableElement = (ExecutableElement) element;
        // Verify that the method has equal to or less than the number of parameters as the listener.
        List<? extends VariableElement> methodParameters = executableElement.getParameters();
        final int methodParameterSize = methodParameters.size();
        if (methodParameterSize > 0) {
            final VariableElement methodParameter = methodParameters.get(0);
            if (methodParameterSize == 1
                    && InjectProcessor.isTypeEqual(methodParameter.asType(), InjectProcessor.TYPE_VIEW)) {
                mHasViewParameter = true;
            } else {
                mParameterError = true;
            }
        }
    }

    public String getName() {
        return mName;
    }

    public int[] getValue() {
        return mValue;
    }

    public boolean isParameterError() {
        return mParameterError;
    }

    public boolean hasViewParameter() {
        return mHasViewParameter;
    }
}
