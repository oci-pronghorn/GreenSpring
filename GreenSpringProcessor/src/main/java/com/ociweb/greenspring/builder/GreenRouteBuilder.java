package com.ociweb.greenspring.builder;

import com.ociweb.gl.api.*;
import com.ociweb.greenspring.adaptors.GreenMethod;
import com.ociweb.greenspring.adaptors.GreenRoute;
import com.ociweb.greenspring.adaptors.GreenSerializer;
import com.ociweb.pronghorn.network.config.HTTPContentTypeDefaults;
import com.ociweb.pronghorn.pipe.ChannelReader;
import com.ociweb.pronghorn.pipe.ChannelWriter;

import com.squareup.javapoet.*;

import javax.annotation.processing.Filer;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.VariableElement;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

class GreenRouteBuilder {
    private final GreenRoute route;
    private final ClassName serviceName;
    private final String methodName;
    private final ClassName behaviorName;
    private boolean hasRequestBody;
    private final TypeName responseName;
    private final TypeName responseBodyName;
    private final Map<String, String> routedParams = new HashMap<>();
    private final Map<String, Integer> routedIds = new HashMap<>();
    private final List<VariableElement> orderedParams = new ArrayList<>();
    private final TypeSpec.Builder builder;

    private final static Map<String, String> init = new HashMap<>();
    static {
        init.put("java.lang.String", "paramToString($L, httpRequestReader)");
        init.put("byte", "httpRequestReader.getByte($L)");
        init.put("short", "httpRequestReader.getShort($L)");
        init.put("int", "httpRequestReader.getInt($L)");
        init.put("long", "httpRequestReader.getLong($L)");
        init.put("float", "httpRequestReader.getDouble($L)");
        init.put("double", "httpRequestReader.getDouble($L)");
    }

    private final GreenMethod annotatedMethod = new GreenMethod();
    private final GreenSerializer serializer = new GreenSerializer();

    GreenRouteBuilder(ClassName serviceName, String subPackage, GreenRoute route) {
        ExecutableElement element = (ExecutableElement)route.getElement();
        this.route = route;
        this.serviceName = serviceName;
        this.methodName = element.getSimpleName().toString();

        String packageName = serviceName.packageName() + subPackage + ".routes";
        String className = "Green" + serviceName.simpleName() + methodName.substring(0, 1).toUpperCase() + methodName.substring(1);
        this.behaviorName = ClassName.get(packageName, className);

        int idx = 0;
        this.hasRequestBody = false;
        List<? extends VariableElement> parameters = element.getParameters();
        for (VariableElement param : parameters) {
            String name = param.getSimpleName().toString();
            String kind = param.asType().toString();
            if (annotatedMethod.isParamInRoute(param)) {
                this.routedParams.put(name, kind);
                this.routedIds.put(name, idx);
                idx++;
            }
            else  if (annotatedMethod.isParamRequestBody(param)) {
                this.hasRequestBody = true;
            }
            orderedParams.add(param);
        }

        this.responseName = TypeName.get(element.getReturnType());

        //if (responseName.toString().endsWith("ResponseEntity")) {
            this.responseBodyName = ((ParameterizedTypeName)responseName).typeArguments.get(0);
        //}

        //TypeName returnType = TypeName.get(element.getReturnType());


        this.builder = TypeSpec.classBuilder(behaviorName)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addSuperinterface(RestListener.class);

        if (this.responseName != null) {
            this.builder.addSuperinterface(Writable.class);
        }
        if (hasRequestBody) {
            this.builder.addSuperinterface(Payloadable.class);
        }
    }

    TypeName getBehaviorName() {
        return behaviorName;
    }

    void write(Filer filer, String indent) throws IOException {
        buildConstructor();
        buildState();
        buildReader();
        buildWriter();
        buildRestRequest();

        JavaFile.builder(this.behaviorName.packageName(), builder.build())
                .skipJavaLangImports(true)
                .indent(indent)
                .build()
                .writeTo(filer);
    }

    private void buildConstructor() {
        MethodSpec.Builder constructor = MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addParameter(GreenCommandChannel.class, "channel")
                .addStatement("this.channel = channel");

        builder.addMethod(constructor.build());
    }

    private void buildState() {
        MethodSpec paramToString = MethodSpec.methodBuilder("paramToString")
                .addModifiers(Modifier.PRIVATE)
                .addParameter(int.class, "idx")
                .addParameter(HTTPRequestReader.class, "httpRequestReader")
                .returns(String.class)
                .addStatement("paramBuffer.setLength(0)")
                .addStatement("httpRequestReader.getText(idx, paramBuffer)")
                .addStatement("return paramBuffer.toString()")
                .build();

        MethodSpec setService = MethodSpec.methodBuilder("setService")
                .addModifiers(Modifier.PUBLIC)
                .addParameter(serviceName, "service")
                .addStatement("this.service = service;")
                .build();

        builder
                .addField(GreenCommandChannel.class, "channel", Modifier.PRIVATE, Modifier.FINAL)
                .addField(serviceName, "service", Modifier.PRIVATE)
                .addMethod(setService)
                .addMethod(paramToString)
                .addField(FieldSpec.builder(StringBuilder.class, "paramBuffer", Modifier.PRIVATE, Modifier.FINAL)
                        .initializer("new $T()", StringBuilder.class)
                        .build())
                .addField(responseBodyName, "responseBody", Modifier.PRIVATE);

        serializer.buildMember(builder);
    }

    private void buildReader() {
        if (hasRequestBody) {
            MethodSpec.Builder method = MethodSpec.methodBuilder("read")
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(Override.class)
                    .addParameter(ChannelReader.class, "channelReader");

            serializer.addRequestBodyRead(method);

            builder.addMethod(method.build());
        }
    }

    private void buildWriter() {
        if (responseBodyName != null) {
            MethodSpec.Builder method = MethodSpec.methodBuilder("write")
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(Override.class)
                    .addParameter(ChannelWriter.class, "channelWriter");

            serializer.addResponseBodyWrite(method);

            builder.addMethod(method.build());
        }
    }

    private void buildRestRequest() {
        MethodSpec.Builder method = MethodSpec.methodBuilder("restRequest")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override.class)
                .addParameter(HTTPRequestReader.class, "httpRequestReader")
                .returns(boolean.class);

        method.beginControlFlow("try");

        for (VariableElement param : orderedParams) {
            TypeName kind = TypeName.get(param.asType());
            String name = param.getSimpleName().toString();
            if (annotatedMethod.isParamRequestBody(param)) {
                serializer.buildRecycledMember(builder, kind);
                builder.addField(FieldSpec.builder(kind, "requestBody")
                        .addModifiers(Modifier.PRIVATE)
                        .build());
                method.addStatement("httpRequestReader.openPayloadData(this)");
                method.addStatement("$T $L = requestBody", kind, name);
            }
            else {
                if (annotatedMethod.isParamInRoute(param)) {
                    String key = kind.toString();
                    String initializer = init.get(key);
                    method.addStatement("$T $L = " + initializer, kind, name, routedIds.get(name));
                }
                else {
                    method.addStatement("$T $L", kind, name);
                }
            }
        }

        String paramList = orderedParams.stream().map(VariableElement::getSimpleName).collect(Collectors.joining(", "));
        method.addStatement("$T response = service." + methodName + "(" + paramList + ")", responseName);
        method.addStatement("this.responseBody = response.getBody()");
        method.addStatement("channel.publishHTTPResponse(httpRequestReader, response.getStatusCodeValue(), $T.JSON, this)", HTTPContentTypeDefaults.class);
        method.endControlFlow();
        method.beginControlFlow("catch (Throwable e)");
        method.addStatement("channel.publishHTTPResponse(httpRequestReader, 400)");
        method.endControlFlow();
        method.addStatement("return true");

        builder.addMethod(method.build());
    }

    String getGreenRoute(String baseRoute) {
        return this.route.getGreenRouteString(baseRoute, routedParams);
    }
}
