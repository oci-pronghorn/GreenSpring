package com.ociweb.greenspring;

import com.ociweb.gl.api.Builder;
import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.gl.api.HTTPRequestReader;
import com.squareup.javapoet.*;
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
    private final ParameterizedTypeName function;

    BehaviorDispatch(ClassName behaviorName, String route) {
        this.behaviorName = behaviorName;
        this.route = route;
        this.function = ParameterizedTypeName.get(
                ClassName.get(BiFunction.class),
                behaviorName,
                TypeName.get(HTTPRequestReader.class),
                TypeName.get(Boolean.class));
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

        builder
                .addField(FieldSpec.builder(int.class, "routeOffset", Modifier.PRIVATE, Modifier.STATIC).build())
                .addMethod(behaviorInvocation);
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

        MethodSpec.Builder config = MethodSpec.methodBuilder(getConfigInvocation())
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .addParameter(Builder.class, "builder");
        for (int i = 0; i < routeCount; i++) {
            BehaviorRoutedMethod method = (BehaviorRoutedMethod) methods[i];
            config.addCode("routeIds[$L] = builder.registerRoute($S);\n", i, method.getGreenRoute(route));
            config.addCode("dispatch[$L] = ($T)$T::$L;\n", i, function, behaviorName, method.getDispatchName());
        }

        if (routeCount > 0) {
            config.addCode("routeOffset = routeIds[0];\n");
        }
        else {
            config.addCode("routeOffset = -1;\n");
        }

        MethodSpec restRequest = MethodSpec.methodBuilder("restRequest")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override.class)
                .addParameter(HTTPRequestReader.class, "httpRequestReader")
                .returns(boolean.class)
                .addCode(routes.isEmpty() ? "return true;\n" : "return (($T)dispatch[httpRequestReader.getRouteId() - routeOffset]).apply(this, httpRequestReader);\n", function)
                .build();

        builder
                .addField(FieldSpec.builder(int[].class, "routeIds", Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                        .initializer("new $T[$L]", int.class, routeCount)
                        .build())
                .addField(FieldSpec.builder(BiFunction[].class, "dispatch", Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                        .initializer("new $T[$L]", BiFunction.class, routeCount)
                        .build())
                .addMethod(config.build())
                .addMethod(restRequest);
    }
}
