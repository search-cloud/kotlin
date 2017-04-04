/*
 * Copyright 2010-2017 JetBrains s.r.o.
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

package org.jetbrains.kotlin.cli.common.arguments

import java.lang.reflect.Field

// TODO docs

annotation class ArgumentName(vararg val value: String)

annotation class ArgumentDescription(val value: String)

annotation class ValueDescription(val value: String)

fun <A : CommonCompilerArguments> parseCommandLineArguments(args: Array<String>, result: A): List<String> {
    data class ArgumentField(val field: Field, val argumentNames: List<String>) {
        val type = field.type
    }

    val fields = generateSequence(result::class.java, Class<*>::getSuperclass).toList().flatMap { klass ->
        klass.declaredFields.mapNotNull { field ->
            val argument = field.getAnnotation(ArgumentName::class.java)
            if (argument != null)
                ArgumentField(field, argument.value.toList())
            else null
        }
    }

    val freeArgs = mutableListOf<String>()
    var i = 0
    while (i < args.size) {
        val arg = args[i++]
        if (!arg.startsWith("-")) {
            freeArgs.add(arg)
            continue
        }

        val argWithoutPrefix = arg.substring(1)
        val argumentField = fields.firstOrNull { (_, names) -> names.any { it == argWithoutPrefix } }
        if (argumentField == null) {
            freeArgs.add(arg)
            continue
        }

        val field = argumentField.field
        if (argumentField.type.let { it == Boolean::class.javaPrimitiveType || it == Boolean::class.javaObjectType }) {
            field.set(result, true)
            continue
        }

        if (i == args.size) {
            throw IllegalArgumentException("No value passed for argument $arg")
        }
        val stringValue = args[i++]

        when (argumentField.type) {
            String::class.java -> field.set(result, stringValue)
            Array<String>::class.java -> {
                val newElements = stringValue.split(",").toTypedArray()
                @Suppress("UNCHECKED_CAST")
                val oldValue = field.get(result) as Array<String>?
                field.set(result, if (oldValue != null) arrayOf(*oldValue, *newElements) else newElements)
            }
            else -> throw IllegalStateException("Unsupported argument type: ${argumentField.type}")
        }
    }

    return freeArgs
}
