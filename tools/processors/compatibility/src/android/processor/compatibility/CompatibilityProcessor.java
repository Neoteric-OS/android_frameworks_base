/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.processor.compatibility;

import static javax.tools.StandardLocation.CLASS_OUTPUT;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;
import com.sun.tools.javac.model.JavacElements;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.Pair;
import com.sun.tools.javac.util.Position;

import java.io.IOException;
import java.io.PrintStream;
import java.lang.annotation.Annotation;
import java.net.URLEncoder;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;

import android.util.Log;

/**
 * Annotation processor for {@link UnsupportedAppUsage} annotations.
 *
 * This processor currently outputs a CSV file with a mapping of dex signatures to corresponding
 * source positions.
 *
 * This is used for automating updates to the annotations themselves.
 */
@SupportedAnnotationTypes({"android.compat.annotation.ChangeId",
        "android.compat.annotation.Disabled",
        "android.compat.annotation.EnabledAfter"
})
public class CompatibilityProcessor extends AbstractProcessor {

    // Package name for writing output. Output will be written to the "class output" location within
    // this package.
    private static final String PACKAGE = "unsupportedappusage";
    private static final String INDEX_CSV = "unsupportedappusage_index.csv";

    private static final ImmutableSet<Class<? extends Annotation>> SUPPORTED_ANNOTATIONS =
            ImmutableSet.of(android.compat.annotation.ChangeId.class);
    private static final ImmutableSet<String> SUPPORTED_ANNOTATION_NAMES =
            SUPPORTED_ANNOTATIONS.stream().map(annotation -> annotation.getCanonicalName()).collect(
                    ImmutableSet.toImmutableSet());

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latest();
    }


    /**
     * This is the main entry point in the processor, called by the compiler.
     */
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (Class<? extends Annotation> supportedAnnotation : SUPPORTED_ANNOTATIONS) {
            Set<? extends Element> annotated = roundEnv.getElementsAnnotatedWith(
                    supportedAnnotation);
            if (annotated.size() == 0) {
                continue;
            }

            for (Element e : annotated) {
                Log.i(null, element.getKind());
            }
        }

        return true;
    }
}
