package com.blingbling.butterknife.compiler;

import com.blingbling.butterknife.annotation.BindView;
import com.blingbling.butterknife.annotation.ContentView;
import com.blingbling.butterknife.annotation.OnClick;
import com.google.auto.common.SuperficialValidation;
import com.google.auto.service.AutoService;
import com.squareup.javapoet.JavaFile;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.annotation.Annotation;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;

@AutoService(Processor.class)
public class InjectProcessor extends AbstractProcessor {

    static final String TYPE_ACTIVITY = "android.app.Activity";
    static final String TYPE_VIEW = "android.view.View";

    /** 处理Element的的工具类 */
    private Elements mElements;
    /** 处理TypeMirror的工具类 */
    private Types mTypes;
    /** 定义了一些关于创建源文件，类文件和一般资源的方法 */
    private Filer mFiler;
    /** 提供给注解处理器一个报告错误、警告以及提示信息的途径，它不是注解处理器开发者的日志工具，而是用来写一些信息给使用此注解器的第三方开发者的 */
    private Messager mMessager;

    /**
     * 每一个注解处理器类都必须有一个无参构造方法。
     * init方法是在Processor创建时被apt调用并执行初始化操作。
     *
     * @param processingEnvironment 提供一系列的注解处理工具。
     **/
    @Override
    public synchronized void init(ProcessingEnvironment processingEnvironment) {
        super.init(processingEnvironment);
        mElements = processingEnvironment.getElementUtils();
        mTypes = processingEnvironment.getTypeUtils();
        mFiler = processingEnvironment.getFiler();
        mMessager = processingEnvironment.getMessager();
    }

    /**
     * 指定支持的 java 版本，通常返回 SourceVersion.latestSupported()
     **/
    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    /**
     * @return 返回支持的Annotation类型
     */
    @Override
    public Set<String> getSupportedAnnotationTypes() {
        Set<String> set = new LinkedHashSet<>();
        set.add(ContentView.class.getCanonicalName());
        set.add(BindView.class.getCanonicalName());
        return set;
    }

    /**
     * 注解处理需要执行一次或者多次。每次执行时，处理器方法被调用，并且传入了当前要处理的注解类型。
     * 可以在这个方法中扫描和处理注解，并生成Java代码。
     *
     * @param set              当前要处理的注解类型
     * @param roundEnvironment 这个对象提供当前或者上一次注解处理中被注解标注的源文件元素。（获得所有被标注的元素）
     */
    @Override
    public boolean process(Set<? extends TypeElement> set, RoundEnvironment roundEnvironment) {
        //解析注解
        Map<TypeElement, BindingClass> targetClassMap = findAndParseTargets(roundEnvironment);

        //解析完成后，生成的代码的结构已经有了，它们存在TargetClass中
        for (Map.Entry<TypeElement, BindingClass> entry : targetClassMap.entrySet()) {

            TypeElement typeElement = entry.getKey();
            BindingClass bindingClass = entry.getValue();

            JavaFile javaFile = bindingClass.brewJava();
            try {
                javaFile.writeTo(mFiler);
            } catch (IOException e) {
                error(typeElement, "Unable to write injecting for type %s: %s", typeElement, e.getMessage());
            }
        }
        return false;
    }

    /**
     * 发现并解析注解字段
     */
    private Map<TypeElement, BindingClass> findAndParseTargets(RoundEnvironment env) {
        Map<TypeElement, BindingClass> builderMap = new LinkedHashMap<>();

        // Process each @ContentView element.
        for (Element element : env.getElementsAnnotatedWith(ContentView.class)) {
            if (!SuperficialValidation.validateElement(element)) continue;
            try {
                parseContentView(element, builderMap, ContentView.class);
            } catch (Exception e) {
                logParsingError(element, ContentView.class, e);
            }
        }

        // Process each @BindView element.
        for (Element element : env.getElementsAnnotatedWith(BindView.class)) {
            if (!SuperficialValidation.validateElement(element)) continue;
            try {
                parseBindView(element, builderMap, BindView.class);
            } catch (Exception e) {
                logParsingError(element, BindView.class, e);
            }
        }

        // Process each @OnClick element.
        for (Element element : env.getElementsAnnotatedWith(OnClick.class)) {
            if (!SuperficialValidation.validateElement(element)) continue;
            try {
                parseOnClick(element, builderMap, OnClick.class);
            } catch (Exception e) {
                logParsingError(element, OnClick.class, e);
            }
        }

        return builderMap;
    }

    private void parseContentView(Element element, Map<TypeElement, BindingClass> builderMap, Class<? extends Annotation> clazz) {
        TypeMirror type = element.asType();

        final boolean isActivity = isSubtypeOfType(type, TYPE_ACTIVITY);
        if (isActivity) {
            BindingClass bindingClass = getOrCreateBindingBuilder(builderMap, (TypeElement) element);
            bindingClass.setContentViewBinding(new LayoutViewBinding(element));
        } else {
            error(element, "@%s-annotated class incorrectly in Activity class. (%s)",
                    ContentView.class.getSimpleName(), ((TypeElement) element).getQualifiedName().toString());
        }
    }

    /**
     * 解析注解字段
     */
    private void parseBindView(Element element, Map<TypeElement, BindingClass> builderMap, Class<? extends Annotation> clazz) {

        TypeElement enclosingElement = (TypeElement) element.getEnclosingElement();

        //首先对被注解的参数进行验证
        boolean hasError = isInaccessibleViaGeneratedCode(clazz, "fields", element)
                || isBindingInWrongPackage(clazz, element);

        if (hasError) {
            return;
        }
        BindingClass bindingClass = getOrCreateBindingBuilder(builderMap, enclosingElement);
        bindingClass.addFieldViewBinding(new FieldViewBinding(element));
    }

    private void parseOnClick(Element element, Map<TypeElement, BindingClass> builderMap, Class<? extends Annotation> clazz) {
        // This should be guarded by the annotation's @Target but it's worth a check for safe casting.
        if (!(element instanceof ExecutableElement) || element.getKind() != ElementKind.METHOD) {
            throw new IllegalStateException(
                    String.format("@%s annotation must be on a method.", OnClick.class.getSimpleName()));
        }

        TypeElement enclosingElement = (TypeElement) element.getEnclosingElement();

        MethodViewBinding methodViewBinding = new MethodViewBinding(element);
        int[] ids = methodViewBinding.getValue();

        // Verify that the method and its containing class are accessible via generated code.
        boolean hasError = isInaccessibleViaGeneratedCode(OnClick.class, "methods", element);
        Integer duplicateId = findDuplicate(ids);
        if (duplicateId != null) {
            error(element, "@OnClick annotation for method contains duplicate ID %d. (%s.%s)",
                    duplicateId,
                    enclosingElement.getQualifiedName(),
                    element.getSimpleName());
            hasError = true;
        }

        if (methodViewBinding.isParameterError()) {
            error(element, "@OnClick methods parameter error. (%s.%s)",
                    enclosingElement.getQualifiedName(), element.getSimpleName());
            hasError = true;
        }

        if (hasError) {
            return;
        }

        BindingClass bindingClass = getOrCreateBindingBuilder(builderMap, enclosingElement);
        bindingClass.addMethodViewBinding(methodViewBinding);
    }

    /** Returns the first duplicate element inside an array, null if there are no duplicates. */

    private static Integer findDuplicate(int[] array) {
        Set<Integer> seenElements = new LinkedHashSet<>();

        for (int element : array) {
            if (!seenElements.add(element)) {
                return element;
            }
        }
        return null;
    }

    private boolean isInterface(TypeMirror typeMirror) {
        return typeMirror instanceof DeclaredType
                && ((DeclaredType) typeMirror).asElement().getKind() == ElementKind.INTERFACE;
    }

    static boolean isSubtypeOfType(TypeMirror typeMirror, String otherType) {
        if (isTypeEqual(typeMirror, otherType)) {
            return true;
        }
        if (typeMirror.getKind() != TypeKind.DECLARED) {
            return false;
        }
        DeclaredType declaredType = (DeclaredType) typeMirror;
        List<? extends TypeMirror> typeArguments = declaredType.getTypeArguments();
        if (typeArguments.size() > 0) {
            StringBuilder typeString = new StringBuilder(declaredType.asElement().toString());
            typeString.append('<');
            for (int i = 0; i < typeArguments.size(); i++) {
                if (i > 0) {
                    typeString.append(',');
                }
                typeString.append('?');
            }
            typeString.append('>');
            if (typeString.toString().equals(otherType)) {
                return true;
            }
        }
        Element element = declaredType.asElement();
        if (!(element instanceof TypeElement)) {
            return false;
        }
        TypeElement typeElement = (TypeElement) element;
        TypeMirror superType = typeElement.getSuperclass();
        if (isSubtypeOfType(superType, otherType)) {
            return true;
        }
        for (TypeMirror interfaceType : typeElement.getInterfaces()) {
            if (isSubtypeOfType(interfaceType, otherType)) {
                return true;
            }
        }
        return false;
    }

    static boolean isTypeEqual(TypeMirror typeMirror, String otherType) {
        return otherType.equals(typeMirror.toString());
    }

    private boolean isInaccessibleViaGeneratedCode(Class<? extends Annotation> annotationClass,
                                                   String targetThing, Element element) {
        boolean hasError = false;
        TypeElement enclosingElement = (TypeElement) element.getEnclosingElement();

        // Verify method modifiers.
        Set<Modifier> modifiers = element.getModifiers();
        if (modifiers.contains(Modifier.PRIVATE) || modifiers.contains(Modifier.STATIC)) {
            error(element, "@%s %s must not be private or static. (%s.%s)",
                    annotationClass.getSimpleName(), targetThing, enclosingElement.getQualifiedName(),
                    element.getSimpleName());
            hasError = true;
        }

        // Verify containing type.
        if (enclosingElement.getKind() != ElementKind.CLASS) {
            error(enclosingElement, "@%s %s may only be contained in classes. (%s.%s)",
                    annotationClass.getSimpleName(), targetThing, enclosingElement.getQualifiedName(),
                    element.getSimpleName());
            hasError = true;
        }

        // Verify containing class visibility is not private.
        if (enclosingElement.getModifiers().contains(Modifier.PRIVATE)) {
            error(enclosingElement, "@%s %s may not be contained in private classes. (%s.%s)",
                    annotationClass.getSimpleName(), targetThing, enclosingElement.getQualifiedName(),
                    element.getSimpleName());
            hasError = true;
        }

        return hasError;
    }

    private boolean isBindingInWrongPackage(Class<? extends Annotation> annotationClass,
                                            Element element) {
        TypeElement enclosingElement = (TypeElement) element.getEnclosingElement();
        String qualifiedName = enclosingElement.getQualifiedName().toString();

        if (qualifiedName.startsWith("android.")) {
            error(element, "@%s-annotated class incorrectly in Android framework package. (%s)",
                    annotationClass.getSimpleName(), qualifiedName);
            return true;
        }
        if (qualifiedName.startsWith("java.")) {
            error(element, "@%s-annotated class incorrectly in Java framework package. (%s)",
                    annotationClass.getSimpleName(), qualifiedName);
            return true;
        }

        return false;
    }

    private BindingClass getOrCreateBindingBuilder(
            Map<TypeElement, BindingClass> builderMap, TypeElement enclosingElement) {
        BindingClass binding = builderMap.get(enclosingElement);
        if (binding == null) {
            binding = new BindingClass(enclosingElement);
            builderMap.put(enclosingElement, binding);
        }
        return binding;
    }

    private void logParsingError(Element element, Class<? extends Annotation> annotation, Exception e) {
        StringWriter stackTrace = new StringWriter();
        e.printStackTrace(new PrintWriter(stackTrace));
        error(element, "Unable to parse @%s binding.\n\n%s", annotation.getSimpleName(), stackTrace);
    }

    private void error(Element element, String message, Object... args) {
        printMessage(Diagnostic.Kind.ERROR, element, message, args);
    }

    private void note(Element element, String message, Object... args) {
        printMessage(Diagnostic.Kind.NOTE, element, message, args);
    }

    private void printMessage(Diagnostic.Kind kind, Element element, String message, Object[] args) {
        if (args.length > 0) {
            message = String.format(message, args);
        }
        mMessager.printMessage(kind, message, element);
    }
}
