package com.ociweb.greenspring;

import com.ociweb.gl.api.GreenCommandChannel;
import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.gl.api.RestListener;
import com.ociweb.gl.api.StartupListener;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.annotation.processing.Filer;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import java.io.IOException;

class BehaviorStructure {
    private final ClassName serviceName;
    private final ClassName behaviorName;
    private final TypeSpec.Builder builder;
    private final BehaviorDispatch dispatch;

    BehaviorStructure(RequestMapping mapping, Element element, String subPackage) throws ClassNotFoundException {
        Element enclosingElement = element.getEnclosingElement();
        PackageElement packageElement = (PackageElement)enclosingElement;
        this.serviceName = ClassName.get(packageElement.getQualifiedName().toString(), element.getSimpleName().toString());
        this.behaviorName = ClassName.get(packageElement.getQualifiedName().toString() + subPackage, "Green" + element.getSimpleName().toString());
        String routeStr = mapping.value().length > 0 ? mapping.value()[0] : "/";
        String baseRoute = routeStr.substring(0, routeStr.length()-1);
        this.builder = createTypeBuilder();
        this.dispatch = new BehaviorDispatch(getBehaviorName(), baseRoute);
        this.dispatch.injectStructure(this.builder);
    }

    ClassName getBehaviorName() {
        return behaviorName;
    }

    String getConfigInvocation() {
        return this.dispatch.getConfigInvocation();
    }

    String getBehaviorInvocation() {
        return this.dispatch.getBehaviorInvocation();
    }

    void addRoutedMethod(RequestMapping mapping, ExecutableElement element) {
        this.dispatch.injectResource(builder, mapping, element);
    }

    void write(Filer filer, String indent) throws IOException {
        this.dispatch.injectRoutes(builder);
        JavaFile.builder(this.behaviorName.packageName(), builder.build())
                .skipJavaLangImports(true)
                .indent(indent)
                .build()
                .writeTo(filer);
    }

    private TypeSpec.Builder createTypeBuilder() throws ClassNotFoundException {
        MethodSpec constructor = MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PRIVATE)
                .addParameter(GreenRuntime.class, "runtime")
                .addCode("this.channel = runtime.newCommandChannel(NET_REQUESTER);\n")
                .build();

        MethodSpec startup = MethodSpec.methodBuilder("startup")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override.class)
                .addCode("this.service = new $T();\n", serviceName)
                .build();

        return TypeSpec.classBuilder(behaviorName)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addSuperinterface(StartupListener.class)
                .addSuperinterface(RestListener.class)
                .addField(serviceName, "service", Modifier.PRIVATE)
                .addField(GreenCommandChannel.class, "channel", Modifier.PRIVATE, Modifier.FINAL)
                .addMethod(constructor)
                .addMethod(startup);
    }
}
