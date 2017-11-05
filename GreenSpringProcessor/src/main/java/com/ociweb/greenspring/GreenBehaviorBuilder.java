package com.ociweb.greenspring;

import com.ociweb.gl.api.*;
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
    private final boolean sharedChannel;
    private final boolean sharedService;
    private final String baseRoute;
    private final TypeSpec.Builder builder;
    private final List<GreenRouteBuilder> routes = new ArrayList<>();

    GreenBehaviorBuilder(RequestMapping mapping, Element element, String subPackage, boolean sharedChannel, boolean sharedService) throws ClassNotFoundException {
        this.subPackage = subPackage;
        Element enclosingElement = element.getEnclosingElement();
        PackageElement packageElement = (PackageElement)enclosingElement;
        this.serviceName = ClassName.get(packageElement.getQualifiedName().toString(), element.getSimpleName().toString());
        this.behaviorName = ClassName.get(packageElement.getQualifiedName().toString() + subPackage, "Green" + element.getSimpleName().toString());
        this.sharedChannel = sharedChannel;
        this.sharedService = sharedService;
        String routeStr = mapping.value().length > 0 ? mapping.value()[0] : "/";
        this.baseRoute = routeStr.substring(0, routeStr.length()-1);

        this.builder = TypeSpec.classBuilder(behaviorName)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addSuperinterface(StartupListener.class)
                .addSuperinterface(RestListener.class);
    }

    ClassName getBehaviorName() {
        return behaviorName;
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
        if (sharedChannel) {
            builder.addField(GreenCommandChannel.class, "channel", Modifier.PRIVATE, Modifier.FINAL);
        }
        else {
            builder.addField(GreenCommandChannel[].class, "channels", Modifier.PRIVATE, Modifier.FINAL);
        }

        MethodSpec.Builder constructor = MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PRIVATE)
                .addParameter(GreenRuntime.class, "runtime");

        if (sharedChannel) {
            constructor.addCode("this.channel = runtime.newCommandChannel(NET_REQUESTER);\n");
        }
        else {
            constructor.addCode("this.channels = new $T[$L];\n", GreenCommandChannel.class, routes.size());
            constructor.addCode("for (int i = 0; i <$L; i++) this.channels[i] = runtime.newCommandChannel(NET_REQUESTER);\n", routes.size());
        }

        builder.addMethod(constructor.build());
    }

    private void buildRegisterRoutes() {
        if (routes.size() > 0) {
            builder.addField(FieldSpec.builder(int[].class, "routeIds", Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                    .initializer("new $T[$L]", int.class, routes.size())
                    .build());
        }

        if (sharedChannel && routes.size() > 0) {
            builder.addField(int.class, "routeOffset", Modifier.PRIVATE, Modifier.STATIC);
        }

        MethodSpec.Builder config = MethodSpec.methodBuilder(getConfigInvocation())
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .addParameter(Builder.class, "builder");

        for (int i = 0; i < routes.size(); i++) {
            config.addCode("routeIds[$L] = builder.registerRoute($S);\n", i, routes.get(i).getGreenRoute(baseRoute));
        }

        if (sharedChannel) {
            if (routes.size() > 0) {
                config.addCode("routeOffset = routeIds[0];\n");
            }
        }

        builder.addMethod(config.build());
    }

    private void buildRegisterBehaviors() {
        if (routes.size() > 0) {
            builder.addField(FieldSpec.builder(RestListener[].class, "routes", Modifier.PRIVATE)
                    .initializer("new $T[$L]", RestListener.class, routes.size())
                    .build());
        }

        MethodSpec.Builder behavior = MethodSpec.methodBuilder(getBehaviorInvocation())
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .addParameter(GreenRuntime.class, "runtime")
                .addCode("$T green = new $T(runtime);\n", behaviorName, behaviorName)
                .addCode("green.doRegister(runtime);\n");

        MethodSpec.Builder doRegister = MethodSpec.methodBuilder("doRegister")
                .addModifiers(Modifier.PRIVATE)
                .addParameter(GreenRuntime.class, "runtime");

        for (int i = 0; i < routes.size(); i++) {
            GreenRouteBuilder route = routes.get(i);
            if (sharedChannel) {
                doRegister.addCode("routes[$L] = new $T(channel);\n", i, route.getBehaviorName());
            } else {
                doRegister.addCode("routes[$L] = new $T(channels[$L]);\n", i, route.getBehaviorName(), i);
                doRegister.addCode("runtime.registerListener(routes[$L]).includeRoutes(new int[] { routeIds[$L] });\n", i, i);
            }
        }

        if (sharedChannel) {
            if (routes.size() > 0) {
                doRegister.addCode("runtime.registerListener(this).includeRoutes(routeIds);\n");
            }
            else {
                doRegister.addCode("runtime.registerListener(this);\n");
            }
        }

        builder.addMethod(behavior.build());
        builder.addMethod(doRegister.build());
    }

    private void buildStartup() {
        if (routes.size() == 0) {
            builder.addField(FieldSpec.builder(RestListener[].class, "routes", Modifier.PRIVATE, Modifier.FINAL)
                .initializer("new $T[$L]", RestListener.class, routes.size())
                .build());

            builder.addField(FieldSpec.builder(serviceName, "service", Modifier.PRIVATE)
                    .build());
        }

        MethodSpec.Builder startup = MethodSpec.methodBuilder("startup")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override.class);

        if (routes.size() == 0) {
            startup.addCode("service = new $T();\n", serviceName);
        }
        else if (sharedService) {
            startup.addCode("$T service = new $T();\n", serviceName, serviceName);
        }

        for (int i = 0; i < routes.size(); i++) {
            GreenRouteBuilder route = routes.get(i);
            if (sharedService) {
                startup.addCode("(($T)routes[$L]).setService(service);\n", route.getBehaviorName(), i);
            } else {
                startup.addCode("(($T)routes[$L]).setService(new $T());\n", route.getBehaviorName(), i, serviceName);
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
            .addCode(routes.isEmpty() || !sharedChannel
                    ? "return true;\n"
                    : "return (routes[httpRequestReader.getRouteId() - routeOffset]).restRequest(httpRequestReader);\n");

        builder.addMethod(restRequest.build());
    }
}
