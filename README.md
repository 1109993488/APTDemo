# 注解框架

## 基础知识

### 元注解

所谓的元注解就是注解的注解。Java提供了4个元注解，分别是：

1. `@Target`：用于描述注解的使用范围，如果自定义注解不存在`@Target`，则表示该注解可以使用在任何程序元素之上。接收参数ElementType，其值如下：

   ```	java
   /**接口、类、枚举、注解**/
   ElementType.TYPE
   /**字段、枚举的常量**/
   ElementType.FIELD
   /**方法**/
   ElementType.METHOD
   /**方法参数**/
   ElementType.PARAMETER
   /**构造方法**/
   ElementType.CONSTRUCTOR
   /**局部变量**/
   ElementType.LOCAL_VARIABLE
   /**注解**/
   ElementType.ANNOTATION_TYPE
   /**包**/
   ElementType.PACKAGE
   /**表示该注解能写在类型变量的声明语句中。 java8新增**/
   ElementType.TYPE_PARAMETER
   /**表示该注解能写在使用类型的任何语句中。 java8新增**/
   ElementType.TYPE_USE
   ```

2. `@Retention`：表示注解类型保留的时长，它接收RetentonPolicy参数，其值如下：

   ```java
   /**注解仅存在于源码中，在编译阶段丢弃。这些注解在编译结束之后就不再有任何意义，所以它们不会写入字节码。**/
   RetentionPolicy.SOURCE
   /**默认的保留策略，注解会在class字节码文件中存在，但运行时无法获得。**/
   RetentionPolicy.CLASS
   /**注解会在class字节码文件中存在，在运行时可以通过反射获取到。**/
   RetemtionPolicy.RUNTIME
   ```

3. `@Documented`: 表示注解可以出现在javadoc中。

4. `@Inherited`：表示注解可以被子类继承。

## AbstractProcessor

AbstractProcessor 是 javac 扫描和处理注解的关键类,所有自定义的Processor都是继承自AbastractProcessor,一个基本的Procssor结构如下所示：

```java
@AutoService(Processor.class)
public class SimpleProcessor extends AbstractProcessor {

    /**
    * 每一个注解处理器类都必须有一个无参构造方法。
    * init方法是在Processor创建时被javac调用并执行初始化操作。
    * @param processingEnv 提供一系列的注解处理工具。
    **/
    @Override
    public synchronized void init(ProcessingEnvironment env){ }

    /**
     * 注解处理需要执行一次或者多次。每次执行时，处理器方法被调用，并且传入了当前要处理的注解类型。
     * 可以在这个方法中扫描和处理注解，并生成Java代码。
     * @param annotations 当前要处理的注解类型
     * @param roundEnv 这个对象提供当前或者上一次注解处理中被注解标注的源文件元素。（获得所有被标注的元   素）
     */
    @Override
    public boolean process(Set<? extends TypeElement> annoations, RoundEnvironment env) { }

    /** 注解处理器要处理的注解类型,值为完全限定名（就是带所在包名和路径的类全名） **/
    @Override
    public Set<String> getSupportedAnnotationTypes() { }

    /** 指定支持的 java 版本，通常返回 SourceVersion.latestSupported() **/
    @Override
    public SourceVersion getSupportedSourceVersion() { }

}
```

有一点需要注意，Android Library中去除了javax包的部分功能，所以，在新建Module的时候不能选Android Library，需要使用Java Library。

### 注册Processor

引入依赖：[auto-service](http://jcenter.bintray.com/com/google/auto/service/auto-service/)

```groovy
dependencies {
    compile 'com.google.auto.service:auto-service:1.0-rc4'
}
```

使用@AutoService生成META-INF/services/javax.annotation.processing.Processor文件:

```java
AutoService(Processor.class)
public class AutoBuilderProcessor extends AbstractProcessor {
    ...
}
```

### AbstractProcessor属性

#### init

在init方法中我们通过super.init(processingEnv)方法得到了processingEnv的引用。通过processingEnv对象我们能获得如下引用：

- Elements:一个处理Element的的工具类。
- Types：一个处理TypeMirror的工具类。
- Filer：定义了一些关于创建源文件，类文件和一般资源的方法。
- Messager：提供给注解处理器一个报告错误、警告以及提示信息的途径，它不是注解处理器开发者的日志工具，而是用来写一些信息给使用此注解器的第三方开发者的。

#### process

首先，需要说明一下Element的含义，Element代表程序的元素，例如包、类、方法、成员变量。对应关系如下：

```
PackageElement   		--->	包
ExecuteableElement		--->	方法、构造方法
VariableElement 		--->	成员变量、enum常量、方法或构造方法参数、局部变量或异常参数。
TypeElement 			--->	类、接口
TypeParameterElement 		--->	在方法或构造方法、类、接口处定义的泛型参数。
```

## 代码生成

使用Square公司出品的[JavaPoet](https://github.com/square/javapoet)来生成java源代码。

```
dependencies {
    implementation 'com.squareup:javapoet:1.9.0'
}
```
- `JavaFile` 包含一个顶级类的Java文件。
- `TypeSpec` 代表一个类，接口，或者枚举声明。
- `FieldSpec` 代表一个成员变量，一个字段声明。
- `MethodSpec` 代表一个构造函数或方法声明。

```java
private void generateHelloWorld() throws IOException {
    //main代表方法名
    MethodSpec main = MethodSpec.methodBuilder("main")
            //Modifier 修饰的关键字
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            //添加string[]类型的名为args的参数
            .addParameter(String[].class, "args")
            //添加代码，这里$T和$S后面会讲，这里其实就是添加了System,out.println("Hello World");
            .addStatement("$T.out.println($S)", System.class, "Hello World!")
            .addStatement("$T.out.println($S + MAX_VALUE)", System.class, "Integer.MAX_VALUE = ")
            .build();
    TypeSpec typeSpec = TypeSpec.classBuilder("HelloWorld")//HelloWorld是类名
            .addModifiers(Modifier.FINAL, Modifier.PUBLIC)
            .addMethod(main)  //在类中添加方法
            .build();
    JavaFile javaFile = JavaFile.builder("com.example", typeSpec)
            .addStaticImport(Integer.class, "MAX_VALUE")
            .build();
    javaFile.writeTo(System.out);
}
```

### 占位符

- `$L` for Literals
- `$T` for Types
- `$S` for Strings
- `$N` for Names(我们自己生成的方法名或者变量名等等)

这里的`$T`，在生成的源代码里面，也会自动导入你的类。

## Thanks
- [AutoBuilder](https://github.com/Tiny-hoooooo/AutoBuilder)
- [butterknife](https://github.com/JakeWharton/butterknife)