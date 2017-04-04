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

package org.jetbrains.kotlin.cli.common;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.cli.common.arguments.ArgumentName;
import org.jetbrains.kotlin.cli.common.arguments.ArgumentDescription;
import org.jetbrains.kotlin.cli.common.arguments.CommonCompilerArguments;
import org.jetbrains.kotlin.cli.common.arguments.ValueDescription;

import java.io.PrintStream;
import java.lang.reflect.Field;

class Usage {
    // The magic number 29 corresponds to the similar padding width in javac and scalac command line compilers
    private static final int OPTION_NAME_PADDING_WIDTH = 29;

    private static final String coroutinesKeyDescription = "Enable coroutines or report warnings or errors on declarations and use sites of 'suspend' modifier";

    public static void print(@NotNull PrintStream target, @NotNull CommonCompilerArguments arguments, boolean extraHelp) {
        target.println("Usage: " + arguments.executableScriptFileName() + " <options> <source files>");
        target.println("where " + (extraHelp ? "advanced" : "possible") + " options include:");
        boolean coroutinesUsagePrinted = false;
        for (Class<?> clazz = arguments.getClass(); clazz != null; clazz = clazz.getSuperclass()) {
            for (Field field : clazz.getDeclaredFields()) {
                String usage = fieldUsage(field, extraHelp);
                if (usage != null) {
                    if (usage.contains("Xcoroutines")) {
                        if (coroutinesUsagePrinted) continue;
                        coroutinesUsagePrinted = true;
                    }
                    target.println(usage);
                }
            }
        }

        if (extraHelp) {
            target.println();
            target.println("Advanced options are non-standard and may be changed or removed without any notice.");
        }
    }

    @Nullable
    private static String fieldUsage(@NotNull Field field, boolean extraHelp) {
        ArgumentName argument = field.getAnnotation(ArgumentName.class);
        if (argument == null) return null;

        String[] argumentNames = argument.value();
        assert argumentNames.length == 1 || argumentNames.length == 2 : "There should be 1 or 2 argument names, argument: " + field;

        // TODO: this is a dirty hack, provide better mechanism for keys that can have several values
        boolean isXCoroutinesKey = argumentNames[0].contains("Xcoroutines");
        String value = isXCoroutinesKey ? "Xcoroutines={enable|warn|error}" : argumentNames[0];
        boolean extraOption = value.startsWith("X") && value.length() > 1;
        if (extraHelp != extraOption) return null;

        StringBuilder sb = new StringBuilder("  ");
        sb.append("-");
        sb.append(value);

        if (argumentNames.length >= 2) {
            sb.append(" (");
            sb.append("-");
            sb.append(argumentNames[1]);
            sb.append(")");
        }

        ValueDescription valueDescription = field.getAnnotation(ValueDescription.class);
        if (valueDescription != null) {
            sb.append(" ");
            sb.append(valueDescription.value());
        }

        if (isXCoroutinesKey) {
            sb.append(" ");
            sb.append(coroutinesKeyDescription);
            return sb.toString();
        }

        int width = OPTION_NAME_PADDING_WIDTH - 1;
        if (sb.length() >= width + 5) { // Break the line if it's too long
            sb.append("\n");
            width += sb.length();
        }
        while (sb.length() < width) {
            sb.append(" ");
        }

        sb.append(" ");

        ArgumentDescription argumentDescription = field.getAnnotation(ArgumentDescription.class);
        assert argumentDescription != null : "No ArgumentDescription for argument " + value;
        sb.append(argumentDescription.value());

        return sb.toString();
    }
}
