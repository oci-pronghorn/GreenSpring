package com.ociweb.greenspring.builder;

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

public class GreenSpringAppBuilder {
    private final int port;
    private final List<GreenBehaviorBuilder> models = new ArrayList<>();
    private final String appName;
    private final String subPackage;
    private String topPackage = null;

    public GreenSpringAppBuilder(String appName, String subPackage, int port) {
        this.appName = appName;
        this.subPackage = subPackage;
        this.port = port;
    }

    public void addBehavior(GreenBehaviorBuilder model) {
        models.add(model);

        String newPackage = model.getBehaviorName().packageName();
        if (topPackage == null) {
            topPackage = newPackage;
        }
        else {
            int lastSection = 0;
            for (int i = 0; i < Math.min(newPackage.length(), topPackage.length()); i++) {
                if (topPackage.charAt(i) == '.') {
                    lastSection = i;
                }
                if (newPackage.charAt(i) != topPackage.charAt(i)) {
                    topPackage = topPackage.substring(0, lastSection);
                    break;
                }
            }
        }
    }

    public void write(Filer filer, String indent) throws IOException {

        ClassName buildName = ClassName.get(topPackage + subPackage, appName);

        TypeSpec.Builder builder = TypeSpec.classBuilder(buildName)
                .addModifiers(Modifier.PUBLIC)
                .addSuperinterface(GreenAppParallel.class);

        MethodSpec.Builder declareConfiguration = MethodSpec.methodBuilder("declareConfiguration")
                .addModifiers(Modifier.PUBLIC)
                .addParameter(Builder.class, "builder")
                .addStatement("builder.useHTTP1xServer($L)", port)
                .addStatement("builder.useInsecureNetClient()");

        for (GreenBehaviorBuilder model : models) {
            declareConfiguration.addStatement("$T.$L(builder)", model.getBehaviorName(), model.getConfigInvocation());
        }

        MethodSpec.Builder declareBehavior = MethodSpec.methodBuilder("declareBehavior")
                .addModifiers(Modifier.PUBLIC)
                .addParameter(GreenRuntime.class, "runtime");


        MethodSpec.Builder declareParallelBehavior = MethodSpec.methodBuilder("declareParallelBehavior")
                .addModifiers(Modifier.PUBLIC)
                .addParameter(GreenRuntime.class, "runtime");

        for (GreenBehaviorBuilder model : models) {
            if (model.isParallelBehavior()) {
                declareParallelBehavior.addStatement("$T.$L(runtime)", model.getBehaviorName(), model.getBehaviorInvocation());
            }
            else {
                declareBehavior.addStatement("$T.$L(runtime)", model.getBehaviorName(), model.getBehaviorInvocation());
            }
        }

        builder
            .addMethod(
                MethodSpec.methodBuilder("main")
                    .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                    .addParameter(String[].class, "args")
                    .addStatement("GreenRuntime.run(new $T(), args)", buildName)
                    .build())
            .addMethod(declareConfiguration.build())
            .addMethod(declareBehavior.build())
            .addMethod(declareParallelBehavior.build());

        JavaFile.Builder java = JavaFile.builder(buildName.packageName(), builder.build())
                .skipJavaLangImports(true)
                .indent(indent);

        for (GreenBehaviorBuilder behavior : models) {
            behavior.write(filer, indent);
        }

        java.build().writeTo(filer);
    }
}
