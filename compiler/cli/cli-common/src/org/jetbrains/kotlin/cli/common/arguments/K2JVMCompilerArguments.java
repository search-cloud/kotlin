/*
 * Copyright 2010-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.cli.common.arguments;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.config.JvmTarget;

public class K2JVMCompilerArguments extends CommonCompilerArguments {
    public static final long serialVersionUID = 0L;

    @ArgumentName("d")
    @ArgumentDescription("Destination for generated class files")
    @ValueDescription("<directory|jar>")
    public String destination;

    @ArgumentName({"classpath", "cp"})
    @ArgumentDescription("Paths where to find user class files")
    @ValueDescription("<path>")
    public String classpath;

    @GradleOption(DefaultValues.BooleanFalseDefault.class)
    @ArgumentName("include-runtime")
    @ArgumentDescription("Include Kotlin runtime in to resulting .jar")
    public boolean includeRuntime;

    @GradleOption(DefaultValues.StringNullDefault.class)
    @ArgumentName("jdk-home")
    @ArgumentDescription("Path to JDK home directory to include into classpath, if differs from default JAVA_HOME")
    @ValueDescription("<path>")
    public String jdkHome;

    @GradleOption(DefaultValues.BooleanFalseDefault.class)
    @ArgumentName("no-jdk")
    @ArgumentDescription("Don't include Java runtime into classpath")
    public boolean noJdk;

    @GradleOption(DefaultValues.BooleanTrueDefault.class)
    @ArgumentName("no-stdlib")
    @ArgumentDescription("Don't include Kotlin runtime into classpath")
    public boolean noStdlib;

    @GradleOption(DefaultValues.BooleanTrueDefault.class)
    @ArgumentName("no-reflect")
    @ArgumentDescription("Don't include Kotlin reflection implementation into classpath")
    public boolean noReflect;

    @ArgumentName("module")
    @ArgumentDescription("Path to the module file to compile")
    @ValueDescription("<path>")
    public String module;

    @ArgumentName("script")
    @ArgumentDescription("Evaluate the script file")
    public boolean script;

    @ArgumentName("script-templates")
    @ArgumentDescription("Script definition template classes")
    @ValueDescription("<fully qualified class name[,]>")
    public String[] scriptTemplates;

    @ArgumentName("kotlin-home")
    @ArgumentDescription("Path to Kotlin compiler home directory, used for runtime libraries discovery")
    @ValueDescription("<path>")
    public String kotlinHome;

    @ArgumentName("module-name")
    @ArgumentDescription("Module name")
    public String moduleName;

    @GradleOption(DefaultValues.JvmTargetVersions.class)
    @ArgumentName("jvm-target")
    @ArgumentDescription("Target version of the generated JVM bytecode (1.6 or 1.8), default is 1.6")
    @ValueDescription("<version>")
    public String jvmTarget;

    @GradleOption(DefaultValues.BooleanFalseDefault.class)
    @ArgumentName("java-parameters")
    @ArgumentDescription("Generate metadata for Java 1.8 reflection on method parameters")
    public boolean javaParameters;

    // Advanced options
    @ArgumentName("Xno-call-assertions")
    @ArgumentDescription("Don't generate not-null assertion after each invocation of method returning not-null")
    public boolean noCallAssertions;

    @ArgumentName("Xno-param-assertions")
    @ArgumentDescription("Don't generate not-null assertions on parameters of methods accessible from Java")
    public boolean noParamAssertions;

    @ArgumentName("Xno-optimize")
    @ArgumentDescription("Disable optimizations")
    public boolean noOptimize;

    @ArgumentName("Xreport-perf")
    @ArgumentDescription("Report detailed performance statistics")
    public boolean reportPerf;

    @ArgumentName("Xmultifile-parts-inherit")
    @ArgumentDescription("Compile multifile classes as a hierarchy of parts and facade")
    public boolean inheritMultifileParts;

    @ArgumentName("Xskip-runtime-version-check")
    @ArgumentDescription("Allow Kotlin runtime libraries of incompatible versions in the classpath")
    public boolean skipRuntimeVersionCheck;

    @ArgumentName("Xdump-declarations-to")
    @ArgumentDescription("Path to JSON file to dump Java to Kotlin declaration mappings")
    @ValueDescription("<path>")
    public String declarationsOutputPath;

    @ArgumentName("Xsingle-module")
    @ArgumentDescription("Combine modules for source files and binary dependencies into a single module")
    public boolean singleModule;

    @ArgumentName("Xadd-compiler-builtins")
    @ArgumentDescription("Add definitions of built-in declarations to the compilation classpath (useful with -no-stdlib)")
    public boolean addCompilerBuiltIns;

    @ArgumentName("Xload-builtins-from-dependencies")
    @ArgumentDescription("Load definitions of built-in declarations from module dependencies, instead of from the compiler")
    public boolean loadBuiltInsFromDependencies;

    // Paths to output directories for friend modules.
    public String[] friendPaths;

    @NotNull
    public static K2JVMCompilerArguments createDefaultInstance() {
        K2JVMCompilerArguments arguments = new K2JVMCompilerArguments();
        arguments.jvmTarget = JvmTarget.DEFAULT.getDescription();
        return arguments;
    }

    @Override
    @NotNull
    public String executableScriptFileName() {
        return "kotlinc-jvm";
    }
}
