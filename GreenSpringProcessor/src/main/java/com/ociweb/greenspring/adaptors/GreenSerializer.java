package com.ociweb.greenspring.adaptors;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.squareup.javapoet.*;

import javax.lang.model.element.Modifier;
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
}
