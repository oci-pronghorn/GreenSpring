package com.ociweb.greenspring;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ociweb.gl.api.HTTPRequestReader;
import com.ociweb.pronghorn.network.config.HTTPContentTypeDefaults;
import com.squareup.javapoet.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.VariableElement;
import java.util.*;
import java.util.stream.Collectors;

class BehaviorRoutedMethod {
    private final String route;
    private final String methodName;
    private final Map<String, String> routedParams = new HashMap<>();
    private final Map<String, Integer> routedIds = new HashMap<>();
    private final List<VariableElement> orderedParams = new ArrayList<>();

    private final static Map<String, String> spec = new HashMap<>();
    private final static Map<String, String> init = new HashMap<>();
    static {
        spec.put("java.lang.String", "$$");
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

    BehaviorRoutedMethod(RequestMapping mapping, ExecutableElement element) {
        this.route = mapping.value().length > 0 ? mapping.value()[0] : "";
        this.methodName = element.getSimpleName().toString();

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
    }

    String getDispatchName() {
        return "_" + methodName;
    }

    static void injectStructure(TypeSpec.Builder builder) {
        MethodSpec paramToString = MethodSpec.methodBuilder("paramToString")
                .addModifiers(Modifier.PRIVATE)
                .addParameter(int.class, "idx")
                .addParameter(HTTPRequestReader.class, "httpRequestReader")
                .returns(String.class)
                .addCode("paramBuffer.setLength(0);\nhttpRequestReader.getText(idx, paramBuffer);\nreturn paramBuffer.toString();\n")
                .build();

        builder
                .addMethod(paramToString)
                .addField(FieldSpec.builder(ObjectMapper.class, "mapper", Modifier.PRIVATE, Modifier.FINAL)
                    .initializer("new $T()", ObjectMapper.class)
                    .build())
                .addField(FieldSpec.builder(StringBuilder.class, "paramBuffer", Modifier.PRIVATE, Modifier.FINAL)
                    .initializer("new $T()", StringBuilder.class)
                    .build())
                .addField(FieldSpec.builder(ResponseEntity.class, "badRequest", Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                    .initializer("new $T($T.BAD_REQUEST)", ResponseEntity.class, HttpStatus.class)
                    .build());
    }

    private CodeBlock initializerForType(TypeName kind) {
        if (kind instanceof ParameterizedTypeName) {
            ParameterizedTypeName param = (ParameterizedTypeName)kind;
            TypeName sub = param.typeArguments.get(0);
            return CodeBlock.of("mapper.getTypeFactory().constructCollectionType($T.class, $T.class)", ArrayList.class, sub);
        }
        return CodeBlock.of("mapper.getTypeFactory().constructType($T)", kind);
    }
    /*
        // cannot modify response nor can we throw the exception...
        httpRequestReader.openPayloadData(new Payloadable() {
            @Override
            public void read(ChannelReader channelReader) {
                response = mapper.readValue(channelReader, _createInventoryRequestBodyType);
            }
        });
    */
    void injectDispatch(TypeSpec.Builder builder) {
        String dispatchName = getDispatchName();
        MethodSpec.Builder method = MethodSpec.methodBuilder(dispatchName)
                .addModifiers(Modifier.PRIVATE)
                .addParameter(HTTPRequestReader.class, "httpRequestReader")
                .addCode("$T response = badRequest;\n", ResponseEntity.class)
                .addCode("try {\n")
                .returns(boolean.class);

        for (VariableElement param : orderedParams) {
            TypeName kind = TypeName.get(param.asType());
            String name = param.getSimpleName().toString();
            method.addCode("    $T " + name, kind);
            RequestBody requestBodyParam = param.getAnnotation(RequestBody.class);
            String requestBodyType = dispatchName + "RequestBodyType";
            if (requestBodyParam != null) {
                builder.addField(FieldSpec.builder(JavaType.class, requestBodyType)
                        .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
                        .initializer(initializerForType(kind))
                        .build());
                method.addCode(" = mapper.readValue(\"[]\", " + requestBodyType + ");\n");
            }
            else {
                PathVariable routeParam = param.getAnnotation(PathVariable.class);
                if (routeParam != null) {
                    String key = kind.toString();
                    method.addCode(String.format(init.get(key), routedIds.get(name)));
                }
                else {
                    method.addCode(";\n");
                }
            }
        }

        String paramList = orderedParams.stream().map(VariableElement::getSimpleName).collect(Collectors.joining(", "));
        method.addCode(
                "    response = service." + methodName + "(" + paramList + ");\n" +
                "}\n" +
                "catch (Exception e) {\n" +
                "}\n" +
                "finally {\n" +
                 "    // TODO: serialize response json\n" +
                "    channel.publishHTTPResponse(httpRequestReader, response.getStatusCodeValue(), $T.JSON, (w)->{w.writeUTF(\"{}\");});\n" +
                "}\n" +
                "return true;\n", HTTPContentTypeDefaults.class);

       builder.addMethod(method.build());
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
