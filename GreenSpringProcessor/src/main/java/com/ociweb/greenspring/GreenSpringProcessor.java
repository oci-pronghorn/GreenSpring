package com.ociweb.greenspring;

import com.ociweb.greenspring.adaptors.GreenRoute;
import com.ociweb.greenspring.annotation.CreateGreenSpringAppConfig;
import com.ociweb.greenspring.builder.GreenBehaviorBuilder;
import com.ociweb.greenspring.builder.GreenSpringAppBuilder;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;

import javax.tools.Diagnostic;
import java.io.IOException;
import java.util.List;
import java.util.Set;


@SupportedAnnotationTypes({
        "org.springframework.web.bind.annotation.*",
        "com.ociweb.greenspring.annotation.*"})
@SupportedSourceVersion(SourceVersion.RELEASE_8)
//@AutoService(GreenSpringProcessor.class) - Google thing to produce meta file

public class GreenSpringProcessor extends AbstractProcessor {
    private Filer filer;
    private Messager messager;
    private final CreateGreenSpringAppConfig config = new CreateGreenSpringAppConfig();

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        filer = processingEnv.getFiler();
        messager = processingEnv.getMessager();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (annotations.isEmpty()) {
            return true;
        }
        GreenSpringAppBuilder app = new GreenSpringAppBuilder(config.getAppName(), config.getSubPackage(), config.getPort());

        for (GreenRoute controller : GreenRoute.fetchControllers(roundEnv)) {
            Element element = controller.getElement();
            try {
                GreenBehaviorBuilder current = new GreenBehaviorBuilder(controller, config.getSubPackage());
                app.addBehavior(current);
                for (GreenRoute route : GreenRoute.fetchMethods(element)) {
                    current.addRoutedMethod(route);
                }
            } catch (ClassNotFoundException e) {
                messager.printMessage(Diagnostic.Kind.ERROR, e.getLocalizedMessage(), element);
            }
        }
        try {
            app.write(filer, config.getIndent());
        } catch (IOException e) {
            messager.printMessage(Diagnostic.Kind.ERROR, e.getLocalizedMessage());
        }
        return true;
    }
}
