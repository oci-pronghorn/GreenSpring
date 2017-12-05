package com.ociweb.greenspring.adaptors;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.squareup.javapoet.*;

import javax.lang.model.element.Modifier;
import java.io.IOException;
import java.util.ArrayList;

public class GreenSerializer {

    public void buildMember(TypeSpec.Builder builder) {
        builder.addField(FieldSpec.builder(ObjectMapper.class, "mapper", Modifier.PRIVATE, Modifier.FINAL)
                .initializer("new $T()", ObjectMapper.class)
                .build());
    }

    public void buildRecycledMember(TypeSpec.Builder builder, TypeName kind) {
        builder.addField(FieldSpec.builder(JavaType.class, "requestBodyType")
                .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
                .initializer(initializerForType(kind))
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

    public void addRequestBodyRead(MethodSpec.Builder method) {
        method.beginControlFlow("try")
                .addStatement("requestBody = mapper.readValue(channelReader, requestBodyType)")
                .endControlFlow()
                .beginControlFlow("catch ($T e)", IOException.class)
                .addStatement("throw new $T(e)", RuntimeException.class)
                .endControlFlow();
    }

    public void addResponseBodyWrite(MethodSpec.Builder method) {
        method.beginControlFlow("try")
                .addStatement("mapper.writeValue(channelWriter, responseBody)")
                .endControlFlow()
                .beginControlFlow("catch ($T e)", IOException.class)
                .addStatement("throw new $T(e)", RuntimeException.class)
                .endControlFlow();
    }
}
