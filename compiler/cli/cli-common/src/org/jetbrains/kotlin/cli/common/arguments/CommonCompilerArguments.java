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

import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.util.List;

public abstract class CommonCompilerArguments implements Serializable {
    public static final long serialVersionUID = 0L;

    public static final String PLUGIN_OPTION_FORMAT = "plugin:<pluginId>:<optionName>=<value>";

    @GradleOption(DefaultValues.LanguageVersions.class)
    @ArgumentName("language-version")
    @ArgumentDescription("Provide source compatibility with specified language version")
    @ValueDescription("<version>")
    public String languageVersion;

    @GradleOption(DefaultValues.LanguageVersions.class)
    @ArgumentName("api-version")
    @ArgumentDescription("Allow to use declarations only from the specified version of bundled libraries")
    @ValueDescription("<version>")
    public String apiVersion;

    @GradleOption(DefaultValues.BooleanFalseDefault.class)
    @ArgumentName("nowarn")
    @ArgumentDescription("Generate no warnings")
    public boolean suppressWarnings;

    @GradleOption(DefaultValues.BooleanFalseDefault.class)
    @ArgumentName("verbose")
    @ArgumentDescription("Enable verbose logging output")
    public boolean verbose;

    @ArgumentName("version")
    @ArgumentDescription("Display compiler version")
    public boolean version;

    @ArgumentName({"help", "h"})
    @ArgumentDescription("Print a synopsis of standard options")
    public boolean help;

    @ArgumentName("X")
    @ArgumentDescription("Print a synopsis of advanced options")
    public boolean extraHelp;

    @ArgumentName("Xno-inline")
    @ArgumentDescription("Disable method inlining")
    public boolean noInline;

    // TODO Remove in 1.0
    @ArgumentName("Xrepeat")
    @ArgumentDescription("Repeat compilation (for performance analysis)")
    @ValueDescription("<count>")
    public String repeat;

    @ArgumentName("Xskip-metadata-version-check")
    @ArgumentDescription("Load classes with bad metadata version anyway (incl. pre-release classes)")
    public boolean skipMetadataVersionCheck;

    @ArgumentName("Xallow-kotlin-package")
    @ArgumentDescription("Allow compiling code in package 'kotlin'")
    public boolean allowKotlinPackage;

    @ArgumentName("Xplugin")
    @ArgumentDescription("Load plugins from the given classpath")
    @ValueDescription("<path>")
    public String[] pluginClasspaths;

    @ArgumentName("Xmulti-platform")
    @ArgumentDescription("Enable experimental language support for multi-platform projects")
    public boolean multiPlatform;

    @ArgumentName("Xno-check-impl")
    @ArgumentDescription("Do not check presence of 'impl' modifier in multi-platform projects")
    public boolean noCheckImpl;

    @ArgumentName("Xskip-java-check")
    @ArgumentDescription("Do not warn when running the compiler under Java 6 or 7")
    public boolean noJavaVersionWarning;

    @ArgumentName("Xcoroutines=warn")
    @ArgumentDescription("")
    public boolean coroutinesWarn;

    @ArgumentName("Xcoroutines=error")
    @ArgumentDescription("")
    public boolean coroutinesError;

    @ArgumentName("Xcoroutines=enable")
    @ArgumentDescription("")
    public boolean coroutinesEnable;

    @ArgumentName("P")
    @ArgumentDescription("Pass an option to a plugin")
    @ValueDescription(PLUGIN_OPTION_FORMAT)
    public String[] pluginOptions;

    public List<String> freeArgs = new SmartList<>();

    public List<String> unknownExtraFlags = new SmartList<>();

    @NotNull
    public static CommonCompilerArguments createDefaultInstance() {
        DummyImpl arguments = new DummyImpl();
        arguments.coroutinesEnable = false;
        arguments.coroutinesWarn = true;
        arguments.coroutinesError = false;
        return arguments;
    }

    @NotNull
    public String executableScriptFileName() {
        return "kotlinc";
    }

    // Used only for serialize and deserialize settings. Don't use in other places!
    public static final class DummyImpl extends CommonCompilerArguments {}
}
