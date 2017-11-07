package com.ociweb.greenspring;

import com.ociweb.gl.api.*;
import com.ociweb.greenspring.annotation.CreateGreenSpringAppConfig;
import com.ociweb.greenspring.annotation.GreenParallelism;
import com.ociweb.greenspring.annotation.GreenServiceScope;
import com.squareup.javapoet.*;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.annotation.processing.Filer;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

class GreenBehaviorBuilder {
    private final ClassName serviceName;
    private final ClassName behaviorName;
    private final String subPackage;
    private final boolean parallelBehavior;
    private final boolean parallelRoutes;
    private final int serviceScope;
    private final String baseRoute;
    private final TypeSpec.Builder builder;
    private final List<GreenRouteBuilder> routes = new ArrayList<>();

    GreenBehaviorBuilder(RequestMapping mapping, Element element, String subPackage) throws ClassNotFoundException {
        this.subPackage = subPackage;
        Element enclosingElement = element.getEnclosingElement();
        PackageElement packageElement = (PackageElement)enclosingElement;
        this.serviceName = ClassName.get(packageElement.getQualifiedName().toString(), element.getSimpleName().toString());
        this.behaviorName = ClassName.get(packageElement.getQualifiedName().toString() + subPackage, "Green" + element.getSimpleName().toString());
        this.baseRoute = CreateGreenSpringAppConfig.normalizedRoute(mapping);

        GreenParallelism paralellism = element.getAnnotation(GreenParallelism.class);
        if (paralellism != null) {
            this.parallelBehavior = paralellism.parallelBehavior();
            this.parallelRoutes = paralellism.parallelRoutes();
            this.serviceScope = paralellism.serviceScope();
        }
        else {
            this.parallelBehavior = GreenParallelism.parallelBehaviorDefault;
            this.parallelRoutes = GreenParallelism.parallelRoutesDefault;
            this.serviceScope = GreenParallelism.serviceScopeDefault;
        }

        this.builder = TypeSpec.classBuilder(behaviorName)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addSuperinterface(StartupListener.class)
                .addSuperinterface(RestListener.class);
    }

    ClassName getBehaviorName() {
        return behaviorName;
    }

    boolean isParallelBehavior() {
        return parallelBehavior;
    }

    String getConfigInvocation() {
        return "registerRoutes";
    }

    String getBehaviorInvocation() {
        return "registerBehavior";
    }

    void addRoutedMethod(RequestMapping mapping, ExecutableElement element) {
        GreenRouteBuilder routedMethod = new GreenRouteBuilder(serviceName, subPackage, mapping, element);
        routes.add(routedMethod);
    }

    void write(Filer filer, String indent) throws IOException {
        buildConstructor();
        buildRegisterRoutes();
        buildRegisterBehaviors();
        buildStartup();
        buildRestRequest();

        JavaFile.builder(this.behaviorName.packageName(), builder.build())
                .skipJavaLangImports(true)
                .indent(indent)
                .build()
                .writeTo(filer);

        for (GreenRouteBuilder route: routes) {
            route.write(filer, indent);
        }
    }

    private void buildConstructor() {
        MethodSpec.Builder constructor = MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PRIVATE)
                .addParameter(GreenRuntime.class, "runtime");

        if (!parallelRoutes) {
            builder.addField(GreenCommandChannel.class, "channel", Modifier.PRIVATE, Modifier.FINAL);
            constructor.addStatement("this.channel = runtime.newCommandChannel(NET_REQUESTER)");
        }

        builder.addMethod(constructor.build());
    }

    private void buildRegisterRoutes() {
        if (!routes.isEmpty()) {
            builder.addField(FieldSpec.builder(int[].class, "routeIds", Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                    .initializer("new $T[$L]", int.class, routes.size())
                    .build());
        }

        if (!parallelRoutes && !routes.isEmpty()) {
            builder.addField(int.class, "routeOffset", Modifier.PRIVATE, Modifier.STATIC);
        }

        MethodSpec.Builder config = MethodSpec.methodBuilder(getConfigInvocation())
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .addParameter(Builder.class, "builder");

        for (int i = 0; i < routes.size(); i++) {
            config.addStatement("routeIds[$L] = builder.registerRoute($S)", i, routes.get(i).getGreenRoute(baseRoute));
        }

        if (!parallelRoutes) {
            if (!routes.isEmpty()) {
                config.addStatement("routeOffset = routeIds[0]");
            }
        }

        builder.addMethod(config.build());
    }

    private void buildRegisterBehaviors() {
        if (!routes.isEmpty()) {
            builder.addField(FieldSpec.builder(RestListener[].class, "routes", Modifier.PRIVATE)
                    .initializer("new $T[$L]", RestListener.class, routes.size())
                    .build());
        }

        MethodSpec.Builder behavior = MethodSpec.methodBuilder(getBehaviorInvocation())
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .addParameter(GreenRuntime.class, "runtime")
                .addStatement("$T green = new $T(runtime)", behaviorName, behaviorName)
                .addStatement("green.doRegister(runtime)");

        MethodSpec.Builder doRegister = MethodSpec.methodBuilder("doRegister")
                .addModifiers(Modifier.PRIVATE)
                .addParameter(GreenRuntime.class, "runtime");

        for (int i = 0; i < routes.size(); i++) {
            GreenRouteBuilder route = routes.get(i);
            if (parallelRoutes) {
                doRegister.addStatement("routes[$L] = new $T(runtime.newCommandChannel(NET_REQUESTER))", i, route.getBehaviorName());
                doRegister.addStatement("runtime.registerListener(routes[$L]).includeRoutes(new int[] { routeIds[$L] })", i, i);
            } else {
                doRegister.addStatement("routes[$L] = new $T(channel)", i, route.getBehaviorName());
            }
        }

        if (!parallelRoutes) {
            if (routes.size() > 0) {
                doRegister.addStatement("runtime.registerListener(this).includeRoutes(routeIds)");
            }
            else {
                doRegister.addStatement("runtime.registerListener(this)");
            }
        }
        else {
            doRegister.addStatement("runtime.addStartupListener(this)");
        }

        builder.addMethod(behavior.build());
        builder.addMethod(doRegister.build());
    }

    private void buildStartup() {
        MethodSpec.Builder startup = MethodSpec.methodBuilder("startup")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override.class);

        boolean serviceAsMember = true;
        if (serviceScope == GreenServiceScope.app) {
            builder.addField(serviceName, "service", Modifier.PRIVATE, Modifier.STATIC);
            startup.addStatement("if (service != null) service = new $T()", serviceName);
        }
        else if (serviceScope == GreenServiceScope.behavior || routes.isEmpty()) {
            builder.addField(serviceName, "service", Modifier.PRIVATE);
            startup.addStatement("service = new $T()", serviceName);
        }
        else {
            serviceAsMember = false;
        }

        for (int i = 0; i < routes.size(); i++) {
            GreenRouteBuilder route = routes.get(i);
            if (serviceAsMember) {
                startup.addStatement("(($T)routes[$L]).setService(service)", route.getBehaviorName(), i);
            } else {
                startup.addStatement("(($T)routes[$L]).setService(new $T())", route.getBehaviorName(), i, serviceName);
            }
        }

        builder.addMethod(startup.build());
    }

    private void buildRestRequest() {
        MethodSpec.Builder restRequest = MethodSpec.methodBuilder("restRequest")
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(Override.class)
            .addParameter(HTTPRequestReader.class, "httpRequestReader")
            .returns(boolean.class)
            .addStatement(routes.isEmpty() || parallelRoutes
                    ? "return true"
                    : "return (routes[httpRequestReader.getRouteId() - routeOffset]).restRequest(httpRequestReader)");

        builder.addMethod(restRequest.build());
    }
}
