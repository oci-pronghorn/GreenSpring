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

        init.put("java.lang.String", " = paramToString(%d, httpRequestReader);\n");
        init.put("byte", " = httpRequestReader.getByte(%d);\n");
        init.put("short", " = httpRequestReader.getShort(%d);\n");
        init.put("int", " = httpRequestReader.getInt(%d);\n");
        init.put("long", " = httpRequestReader.getLong(%d);\n");
        init.put("float", " = httpRequestReader.getDouble(%d);\n");
        init.put("double", " = httpRequestReader.getDouble(%d);\n");
    }

    GreenRouteBuilder(ClassName serviceName, String subPackage, RequestMapping mapping, ExecutableElement element) {
        this.serviceName = serviceName;
        this.route = mapping.value().length > 0 ? mapping.value()[0] : "";
        this.methodName = element.getSimpleName().toString();
        this.behaviorName = ClassName.get(serviceName.packageName() + subPackage + ".routes", serviceName.simpleName() + methodName);

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
                .addCode("this.channel = channel;\n");

        builder.addMethod(constructor.build());
    }

    private void buildState() {
        MethodSpec paramToString = MethodSpec.methodBuilder("paramToString")
                .addModifiers(Modifier.PRIVATE)
                .addParameter(int.class, "idx")
                .addParameter(HTTPRequestReader.class, "httpRequestReader")
                .returns(String.class)
                .addCode("paramBuffer.setLength(0);\nhttpRequestReader.getText(idx, paramBuffer);\nreturn paramBuffer.toString();\n")
                .build();

        MethodSpec setService = MethodSpec.methodBuilder("setService")
                .addModifiers(Modifier.PUBLIC)
                .addParameter(serviceName, "service")
                .addCode("this.service = service;\n")
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
                .addCode("//requestBody = mapper.readValue(channelReader, requestBodyType);\n");
        builder.addMethod(method.build());
    }

    private void buildWriter() {
        MethodSpec.Builder method = MethodSpec.methodBuilder("write")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override.class)
                .addParameter(ChannelWriter.class, "channelWriter")
                .addCode("//mapper.writeValue(channelWriter, responseBody);\n");

        builder.addMethod(method.build());
    }

    private void buildRestRequest() {
        MethodSpec.Builder method = MethodSpec.methodBuilder("restRequest")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override.class)
                .addParameter(HTTPRequestReader.class, "httpRequestReader")
                .returns(boolean.class);

        method.addCode(
                "try {\n");

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
                method.addCode("    httpRequestReader.openPayloadData(this);\n");
                method.addCode("    $T $L = requestBody;\n", kind, name);
            }
            else {
                PathVariable routeParam = param.getAnnotation(PathVariable.class);
                if (routeParam != null) {
                    String key = kind.toString();
                    method.addCode("    $T $L" + String.format(init.get(key), routedIds.get(name)), kind, name);
                }
                else {
                    method.addCode("    $T $L;\n", kind, name);
                }
            }
        }

        String paramList = orderedParams.stream().map(VariableElement::getSimpleName).collect(Collectors.joining(", "));
        method.addCode(
                "    $T response = service." + methodName + "(" + paramList + ");\n" +
                "    this.responseBody = response.getBody();\n" +
                "    channel.publishHTTPResponse(httpRequestReader, response.getStatusCodeValue(), $T.JSON, this);\n" +
                "}\n" +
                "catch (Exception e) {\n" +
                "    channel.publishHTTPResponse(httpRequestReader, $T.BAD_REQUEST.value());\n" +
                "}\n" +
                "return true;\n", responseName, HTTPContentTypeDefaults.class, HttpStatus.class);

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

        return String.format("%s%s", base, transformed.toString());
    }
}
