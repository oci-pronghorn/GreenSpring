package com.ociweb.greenspring;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ociweb.gl.api.*;
import com.ociweb.pronghorn.network.config.HTTPContentTypeDefaults;
import com.ociweb.pronghorn.pipe.ChannelReader;
import com.ociweb.pronghorn.pipe.ChannelWriter;
import com.squareup.javapoet.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

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
    private final ClassName serviceName;
    private final String route;
    private final String methodName;
    private final ClassName behaviorName;
    private final ParameterizedTypeName responseName;
    private final TypeName responseBodyName;
    private final Map<String, String> routedParams = new HashMap<>();
    private final Map<String, Integer> routedIds = new HashMap<>();
    private final List<VariableElement> orderedParams = new ArrayList<>();
    private final TypeSpec.Builder builder;

    private final static Map<String, String> spec = new HashMap<>();
    private final static Map<String, String> init = new HashMap<>();
    static {
        spec.put("java.lang.String", "$");
        spec.put("byte", "#");
        spec.put("short", "#");
        spec.put("int", "#");
        spec.put("long", "#");
        spec.put("float", "%");
        spec.put("double", "%");

        init.put("java.lang.String", "paramToString($L, httpRequestReader)");
        init.put("byte", "httpRequestReader.getByte($L)");
        init.put("short", "httpRequestReader.getShort($L)");
        init.put("int", "httpRequestReader.getInt($L)");
        init.put("long", "httpRequestReader.getLong($L)");
        init.put("float", "httpRequestReader.getDouble($L)");
        init.put("double", "httpRequestReader.getDouble($L)");
    }

    GreenRouteBuilder(ClassName serviceName, String subPackage, RequestMapping mapping, ExecutableElement element) {
        this.serviceName = serviceName;
        this.route = mapping.value().length > 0 ? mapping.value()[0] : "";
        this.methodName = element.getSimpleName().toString();

        String packageName = serviceName.packageName() + subPackage + ".routes";
        String className = "Green" + serviceName.simpleName() + methodName.substring(0, 1).toUpperCase() + methodName.substring(1);
        this.behaviorName = ClassName.get(packageName, className);

        int idx = 0;
        List<? extends VariableElement> parameters = element.getParameters();
        for (VariableElement param : parameters) {
            String name = param.getSimpleName().toString();
            String kind = param.asType().toString();
            PathVariable routeParam = param.getAnnotation(PathVariable.class);
            if (routeParam != null) {
                this.routedParams.put(name, kind);
                this.routedIds.put(name, idx);
                idx++;
            }
            orderedParams.add(param);
        }
        this.responseName = (ParameterizedTypeName) TypeName.get(element.getReturnType());
        this.responseBodyName = responseName.typeArguments.get(0);

        this.builder = TypeSpec.classBuilder(behaviorName)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addSuperinterface(RestListener.class)
                .addSuperinterface(Payloadable.class)
                .addSuperinterface(Writable.class);
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
                .addField(FieldSpec.builder(ObjectMapper.class, "mapper", Modifier.PRIVATE, Modifier.FINAL)
                        .initializer("new $T()", ObjectMapper.class)
                        .build())
                .addField(FieldSpec.builder(StringBuilder.class, "paramBuffer", Modifier.PRIVATE, Modifier.FINAL)
                        .initializer("new $T()", StringBuilder.class)
                        .build())
                .addField(responseBodyName, "responseBody", Modifier.PRIVATE);
    }

    private void buildReader() {
        MethodSpec.Builder method = MethodSpec.methodBuilder("read")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override.class)
                .addParameter(ChannelReader.class, "channelReader")
                .beginControlFlow("try")
                .addStatement("requestBody = mapper.readValue(channelReader, requestBodyType)")
                .endControlFlow()
                .beginControlFlow("catch ($T e)", IOException.class)
                .addStatement("throw new $T(e)", RuntimeException.class)
                .endControlFlow();

        builder.addMethod(method.build());
    }

    private void buildWriter() {
        MethodSpec.Builder method = MethodSpec.methodBuilder("write")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override.class)
                .addParameter(ChannelWriter.class, "channelWriter")
                .beginControlFlow("try")
                .addStatement("mapper.writeValue(channelWriter, responseBody)")
                .endControlFlow()
                .beginControlFlow("catch ($T e)", IOException.class)
                .addStatement("throw new $T(e)", RuntimeException.class)
                .endControlFlow();

        builder.addMethod(method.build());
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
            RequestBody requestBodyParam = param.getAnnotation(RequestBody.class);
            if (requestBodyParam != null) {
                builder.addField(FieldSpec.builder(JavaType.class, "requestBodyType")
                        .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
                        .initializer(initializerForType(kind))
                        .build());
                builder.addField(FieldSpec.builder(kind, "requestBody")
                        .addModifiers(Modifier.PRIVATE)
                        .build());
                method.addStatement("httpRequestReader.openPayloadData(this)");
                method.addStatement("$T $L = requestBody", kind, name);
            }
            else {
                PathVariable routeParam = param.getAnnotation(PathVariable.class);
                if (routeParam != null) {
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
        method.addStatement("channel.publishHTTPResponse(httpRequestReader, $T.BAD_REQUEST.value())", HttpStatus.class);
        method.endControlFlow();
        method.addStatement("return true");

        builder.addMethod(method.build());
    }

    private CodeBlock initializerForType(TypeName kind) {
        if (kind instanceof ParameterizedTypeName) {
            ParameterizedTypeName param = (ParameterizedTypeName)kind;
            TypeName sub = param.typeArguments.get(0);
            return CodeBlock.of("mapper.getTypeFactory().constructCollectionType($T.class, $T.class)", ArrayList.class, sub);
        }
        return CodeBlock.of("mapper.getTypeFactory().constructType($T)", kind);
    }

    String getGreenRoute(String base) {
        int s = 1;
        StringBuilder transformed = new StringBuilder();
        do {
            int p = route.indexOf("/", s);
            if (p == -1) {
                p = route.length();
            }
            String partial = route.substring(s, p);
            if (partial.charAt(0) == '{') {
                String key = partial.substring(1, partial.length() - 1);
                String kind = routedParams.get(key);
                if (kind != null) {
                    String typeSpec = spec.get(kind);
                    if (typeSpec != null) {
                        partial = typeSpec + partial;
                    }
                }
            }
            transformed.append("/").append(partial);
            s = p + 1;
        } while (s < route.length());

        return base + transformed.toString();
    }
}
