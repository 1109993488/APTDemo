package com.blingbling.butterknife.compiler;

import com.google.auto.common.MoreElements;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

import java.util.ArrayList;
import java.util.List;

import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;

/**
 * Created by BlingBling on 2018/1/4.
 */

class BindingClass {

    public static final String JAVA_FILE_SUFFIX = "_ViewBinding";

    private static final ClassName VIEW = ClassName.get("android.view", "View");
    private static final ClassName UNBINDER = ClassName.get("com.blingbling.butterknife.api", "Unbinder");
    private static final ClassName ONCLICKLISTENER = ClassName.get("android.view", "View.OnClickListener");

    private ClassName mBindingClassName;
    private TypeName mTargetType;
    private LayoutViewBinding mLayoutViewBinding;
    private List<FieldViewBinding> mFieldViewBindings;
    private List<MethodViewBinding> mMethodViewBindings;

    public BindingClass(TypeElement enclosingElement) {
        TypeMirror typeMirror = enclosingElement.asType();

        mTargetType = TypeName.get(typeMirror);

        String packageName = MoreElements.getPackage(enclosingElement).getQualifiedName().toString();
        String className = enclosingElement.getQualifiedName().toString().substring(
                packageName.length() + 1).replace('.', '$');
        mBindingClassName = ClassName.get(packageName, className + JAVA_FILE_SUFFIX);
    }

    /**
     * 生成Java文件
     *
     * @return
     */
    public JavaFile brewJava() {
        TypeSpec.Builder result = TypeSpec.classBuilder(mBindingClassName.simpleName())
                .addModifiers(Modifier.PUBLIC)
                .addSuperinterface(UNBINDER);

        buildTargetField(result);
        createBindingConstructor(result);
        buildUnbindMethod(result);

        return JavaFile.builder(mBindingClassName.packageName(), result.build())
                .addFileComment("Generated code from Butter Knife. Do not modify!")
                .build();
    }

    /**
     * 创建类属性
     *
     * @param result
     */
    private void buildTargetField(TypeSpec.Builder result) {
        result.addField(mTargetType, "target", Modifier.PRIVATE);
        if (hasTargetMethod()) {
            final List<Integer> ids = methodViewIds();
            for (int i = 0, count = ids.size(); i < count; i++) {
                result.addField(VIEW, createViewName(ids.get(i)), Modifier.PRIVATE);
            }
        }
    }

    /**
     * 创建构造方法
     *
     * @param result
     */
    private void createBindingConstructor(TypeSpec.Builder result) {
        MethodSpec.Builder builder = MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addParameter(mTargetType, "target", Modifier.FINAL)
                .addParameter(VIEW, "source");
        builder.addStatement("this.target = target");

        if (mLayoutViewBinding != null) {
            builder.addCode("\n");
            builder.addStatement("target.setContentView($L)", mLayoutViewBinding.getValue());
        }

        if (hasTargetField()) {
            builder.addCode("\n");
            for (int i = 0, count = mFieldViewBindings.size(); i < count; i++) {
                final FieldViewBinding field = mFieldViewBindings.get(i);
                builder.addStatement("target.$N = ($T) source.findViewById($L)",
                        field.getName(),
                        field.getType(),
                        field.getValue());
            }
        }

        if (hasTargetMethod()) {
            builder.addCode("\n");
            final List<Integer> ids = methodViewIds();
            for (int i = 0, count = ids.size(); i < count; i++) {
                final int id = ids.get(i);
                final String name = createViewName(id);
                builder.addStatement("$N = source.findViewById($L)",
                        name,
                        ids.get(i));

                builder.addStatement("$N.setOnClickListener($L)",
                        name,
                        createOnClickListener(id));
            }
        }
        result.addMethod(builder.build());
    }

    /**
     * 创建点击事件回调
     *
     * @param id
     * @return
     */
    private TypeSpec createOnClickListener(int id) {
        MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder("onClick")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .addParameter(VIEW, "view");

        final int methodCount = mMethodViewBindings.size();
        for (int j = 0; j < methodCount; j++) {
            final MethodViewBinding method = mMethodViewBindings.get(j);
            if (containsId(method.getValue(), id)) {
                final String statement;
                if (method.hasViewParameter()) {
                    statement = "target.$N(view)";
                } else {
                    statement = "target.$N()";
                }
                methodBuilder.addStatement(statement, method.getName());
            }
        }

        TypeSpec.Builder result = TypeSpec.anonymousClassBuilder("")
                .addSuperinterface(ONCLICKLISTENER)
                .addMethod(methodBuilder.build());
        return result.build();
    }

    private boolean containsId(int[] ids, int id) {
        if (ids == null) {
            return false;
        }
        for (int i = 0; i < ids.length; i++) {
            if (id == ids[i]) {
                return true;
            }
        }
        return false;
    }

    /**
     * 创建销毁方法
     *
     * @param result
     */
    private void buildUnbindMethod(TypeSpec.Builder result) {
        MethodSpec.Builder builder = MethodSpec.methodBuilder("unbind")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC);
        builder.addStatement("$T target = this.target", mTargetType);
        builder.addStatement("this.target = null");

        if (hasTargetField()) {
            builder.addCode("\n");
            for (int i = 0, count = mFieldViewBindings.size(); i < count; i++) {
                builder.addStatement("target.$N = null", mFieldViewBindings.get(i).getName());
            }
        }

        if (hasTargetMethod()) {
            builder.addCode("\n");
            final List<Integer> ids = methodViewIds();
            for (int i = 0, count = ids.size(); i < count; i++) {
                final String view = createViewName(ids.get(i));
                builder.addStatement("$N.setOnClickListener(null)", view);
                builder.addStatement("$N = null", view);
            }
        }
        result.addMethod(builder.build());
    }

    /**
     * 搜集注解@OnClick事件的View的Id
     *
     * @return
     */
    private List<Integer> methodViewIds() {
        List<Integer> ids = new ArrayList<>();
        for (int i = 0, count = mMethodViewBindings.size(); i < count; i++) {
            final int[] value = mMethodViewBindings.get(i).getValue();
            for (int j = 0, len = value.length; j < len; j++) {
                final int id = value[j];
                if (!ids.contains(id)) {
                    ids.add(id);
                }
            }
        }
        return ids;
    }

    /**
     * 生成的View的名字
     *
     * @param id
     * @return
     */
    private String createViewName(int id) {
        return "view" + id;
    }

    private boolean hasTargetField() {
        return mFieldViewBindings != null && !mFieldViewBindings.isEmpty();
    }

    private boolean hasTargetMethod() {
        return mMethodViewBindings != null && !mMethodViewBindings.isEmpty();
    }

    public void setContentViewBinding(LayoutViewBinding layoutViewBinding) {
        this.mLayoutViewBinding = layoutViewBinding;
    }

    public void addFieldViewBinding(FieldViewBinding fieldViewBinding) {
        if (mFieldViewBindings == null) {
            mFieldViewBindings = new ArrayList<>();
        }
        mFieldViewBindings.add(fieldViewBinding);
    }

    public void addMethodViewBinding(MethodViewBinding methodViewBinding) {
        if (mMethodViewBindings == null) {
            mMethodViewBindings = new ArrayList<>();
        }
        mMethodViewBindings.add(methodViewBinding);
    }
}
