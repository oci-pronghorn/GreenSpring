package com.ociweb.greenspring;

import com.ociweb.gl.api.Builder;
import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.gl.api.HTTPRequestReader;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

class BehaviorDispatch {
    private final ClassName behaviorName;
    private final String route;
    private final List<BehaviorRoutedMethod> routes = new ArrayList<>();

    BehaviorDispatch(ClassName behaviorName, String route) {
        this.behaviorName = behaviorName;
        this.route = route;
    }

    String getConfigInvocation() {
        return "registerRoutes";
    }

    String getBehaviorInvocation() {
        return "registerBehavior";
    }

    void injectStructure(TypeSpec.Builder builder) {

        MethodSpec behaviorInvocation = MethodSpec.methodBuilder(getBehaviorInvocation())
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .addParameter(GreenRuntime.class, "runtime")
                .addCode("runtime.addRestListener(new $T(runtime)).includeRoutes(routeIds);\n", behaviorName)
                .build();

        String code =
                "int routeId = httpRequestReader.getRouteId();\n" +
                "BiFunction<$T, HTTPRequestReader, Boolean> method = dispatch[routeId - routeOffset];\n" +
                "return method.apply(this, httpRequestReader);\n";

        MethodSpec restRequest = MethodSpec.methodBuilder("restRequest")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override.class)
                .addParameter(HTTPRequestReader.class, "httpRequestReader")
                .returns(boolean.class)
                .addCode(code, behaviorName)
                .build();

        builder
                .addField(FieldSpec.builder(int.class, "routeOffset", Modifier.PRIVATE, Modifier.STATIC).build())
                .addMethod(behaviorInvocation)
                .addMethod(restRequest);
        BehaviorRoutedMethod.injectStructure(builder);
    }

    void injectResource(TypeSpec.Builder builder, RequestMapping mapping, ExecutableElement element) {
        BehaviorRoutedMethod routedMethod = new BehaviorRoutedMethod(mapping, element);
        routes.add(routedMethod);
        routedMethod.injectDispatch(builder);
    }

    void injectRoutes(TypeSpec.Builder builder) {
        Object[] methods = routes.toArray();
        int routeCount = methods.length;
        String sn = String.format("] = (BiFunction<%s, HTTPRequestReader, Boolean>)%s::", behaviorName.simpleName(), behaviorName.simpleName());

        StringBuilder code = new StringBuilder();
        for (int i = 0; i < routeCount; i++) {
            BehaviorRoutedMethod method = (BehaviorRoutedMethod)methods[i];
            code.append("routeIds[").append(i).append("] = builder.registerRoute(\"").append(method.getGreenRoute(route)).append("\");\n");
            code.append("dispatch[").append(i).append(sn).append(method.getDispatchName()).append(";\n");
        }
        if (routeCount > 0) {
            code.append("routeOffset = routeIds[0];\n");
        }
        else {
            code.append("routeOffset = -1;\n");
        }

        MethodSpec config = MethodSpec.methodBuilder(getConfigInvocation())
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .addParameter(Builder.class, "builder")
                .addCode(code.toString())
                .build();

        builder
                .addField(FieldSpec.builder(int[].class, "routeIds", Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                        .initializer("new int[$L]", routeCount)
                        .build())
                .addField(FieldSpec.builder(BiFunction[].class, "dispatch", Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                        .initializer("new BiFunction[$L]", routeCount)
                        .build())
                .addMethod(config);
    }
}
