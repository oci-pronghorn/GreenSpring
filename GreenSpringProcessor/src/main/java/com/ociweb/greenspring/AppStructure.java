package com.ociweb.greenspring;

import com.ociweb.gl.api.Builder;
import com.ociweb.gl.api.GreenAppParallel;
import com.ociweb.gl.api.GreenRuntime;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;

import javax.annotation.processing.Filer;
import javax.lang.model.element.Modifier;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

class AppStructure {
    private final ClassName appName = ClassName.get("com.ociweb.apis", "GreenSpringApp");
    private final int port = 80;

    private final List<BehaviorStructure> models = new ArrayList<>();
    private final TypeSpec.Builder builder;

    AppStructure() {
        this.builder = TypeSpec.classBuilder(appName)
                .addModifiers(Modifier.PUBLIC)
                .addSuperinterface(GreenAppParallel.class);
    }

    void addBehavior(BehaviorStructure model) {
        models.add(model);
    }

    void write(Filer filer) throws IOException {

        MethodSpec.Builder declareConfiguration = MethodSpec.methodBuilder("declareConfiguration")
                .addModifiers(Modifier.PUBLIC)
                .addParameter(Builder.class, "builder")
                .addComment("// TODO: figure out good class name and port")
                .addCode("builder.enableServer(false, $L);\n", port)
                .addCode("builder.useInsecureNetClient();\n");

        for (BehaviorStructure model : models) {
            declareConfiguration.addCode("$T.$L(builder);\n", model.getBehaviorName(), model.getConfigInvocation());
        }

        MethodSpec.Builder declareBehavior = MethodSpec.methodBuilder("declareBehavior")
                .addModifiers(Modifier.PUBLIC)
                .addParameter(GreenRuntime.class, "runtime");


        MethodSpec.Builder declareParallelBehavior = MethodSpec.methodBuilder("declareParallelBehavior")
                .addModifiers(Modifier.PUBLIC)
                .addParameter(GreenRuntime.class, "runtime");

        for (BehaviorStructure model : models) {
            declareBehavior.addCode("$T.$L(runtime);\n", model.getBehaviorName(), model.getBehaviorInvocation());
        }

        this.builder
            .addMethod(
                MethodSpec.methodBuilder("main")
                    .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                    .addParameter(String[].class, "args")
                    .addCode("GreenRuntime.run(new $T(), args);\n", appName)
                    .build())
            .addMethod(declareConfiguration.build())
            .addMethod(declareBehavior.build())
            .addMethod(declareParallelBehavior.build());

        JavaFile.Builder java = JavaFile.builder(appName.packageName(), this.builder.build())
                .indent("    ");

        for (BehaviorStructure behavior : models) {
            behavior.write(filer);
        }

        java.build().writeTo(filer);
    }
}
