/*
 * Copyright 2010-2015 KtBrains s.r.o.
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

package org.jetbrains.kotlin.idea.debugger

import com.intellij.debugger.SourcePosition
import com.intellij.debugger.engine.DebugProcess
import com.intellij.debugger.engine.DebuggerUtils
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.sun.jdi.AbsentInformationException
import com.sun.jdi.ReferenceType
import org.jetbrains.kotlin.codegen.binding.CodegenBinding
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.fileClasses.NoResolveFileClassesProvider
import org.jetbrains.kotlin.fileClasses.getFileClassInternalName
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptor
import org.jetbrains.kotlin.idea.debugger.breakpoints.getLambdasAtLineIfAny
import org.jetbrains.kotlin.idea.debugger.evaluate.KotlinDebuggerCaches
import org.jetbrains.kotlin.idea.util.application.runReadAction
import org.jetbrains.kotlin.load.java.JvmAbi
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.isObjectLiteral
import org.jetbrains.kotlin.resolve.inline.InlineUtil

class DebugProcessContext(
        delegate: DebugProcess,
        scopes: List<GlobalSearchScope>,
        val findInlineUseSites: Boolean = true,
        val alwaysReturnLambdaParentClass: Boolean = true
) : DebugProcess by delegate {
    val nameProvider = DebuggerClassNameProvider(this, scopes)
}

fun DebugProcessContext.getClassesForPosition(position: SourcePosition): List<ReferenceType> {
    return doGetClassesForPosition(position) { className, lineNumber ->
        virtualMachineProxy.classesByName(className).map { findTargetClass(it, lineNumber) }
    }
}

fun DebugProcessContext.getOuterClassInternalNamesForPosition(position: SourcePosition): List<String> {
    return doGetClassesForPosition(position) { className, _ -> listOf(className) }
}

private inline fun <T: Any> DebugProcessContext.doGetClassesForPosition(
        position: SourcePosition,
        transformer: (className: String, lineNumber: Int) -> List<T?>
): List<T> {
    val line = position.line
    val result = getOuterClassNamesForElement(position.readAction { it.elementAt })
            .flatMap { transformer(it, line) }
            .filterNotNullTo(mutableSetOf())

    for (lambda in position.readAction(::getLambdasAtLineIfAny)) {
        getOuterClassNamesForElement(lambda).flatMap { transformer(it, line) }.filterNotNullTo(result)
    }

    return result.toList()
}

@PublishedApi
internal tailrec fun DebugProcessContext.getOuterClassNamesForElement(element: PsiElement?): List<String> {
    if (element == null) return emptyList()
    val actualElement = getNonStrictParentOfType(element, CLASS_ELEMENT_TYPES)

    return when (actualElement) {
        is KtFile -> {
            listOf(actualElement.readAction { NoResolveFileClassesProvider.getFileClassInternalName(it) })
        }
        is KtClassOrObject -> {
            val enclosingElementForLocal = actualElement.readAction { KtPsiUtil.getEnclosingElementForLocalDeclaration(it) }
            if (enclosingElementForLocal != null) { // A local class
                getOuterClassNamesForElement(enclosingElementForLocal)
            }
            else if (actualElement.readAction { it.isObjectLiteral() }) {
                getOuterClassNamesForElement(actualElement.parentInReadAction)
            }
            else { // Guaranteed to be non-local class or object
                actualElement.readAction { getNameForNonLocalClass(it) }?.let { listOf(it) } ?: emptyList()
            }
        }
        is KtProperty -> {
            if (actualElement.readAction { it.isTopLevel }) {
                return getOuterClassNamesForElement(actualElement.parentInReadAction)
            }

            val enclosingElementForLocal = actualElement.readAction { KtPsiUtil.getEnclosingElementForLocalDeclaration(it) }
            if (enclosingElementForLocal != null) {
                return getOuterClassNamesForElement(enclosingElementForLocal)
            }

            val containingClassOrFile = actualElement.readAction {
                PsiTreeUtil.getParentOfType(it, KtFile::class.java, KtClassOrObject::class.java)
            }

            if (containingClassOrFile is KtObjectDeclaration && containingClassOrFile.readAction { it.isCompanion() }) {
                val descriptor = actualElement.readAction { it.resolveToDescriptor() } as? PropertyDescriptor
                // Properties from the companion object are placed in the companion object's containing class
                if (descriptor != null) {
                    @Suppress("NON_TAIL_RECURSIVE_CALL")
                    return (getOuterClassNamesForElement(containingClassOrFile.parentInReadAction) +
                           getOuterClassNamesForElement(containingClassOrFile)).distinct()
                }
            }

            if (containingClassOrFile != null)
                getOuterClassNamesForElement(containingClassOrFile)
            else
                getOuterClassNamesForElement(actualElement.parentInReadAction)
        }
        is KtNamedFunction -> {
            if (!findInlineUseSites) {
                return getOuterClassNamesForElement(actualElement.parentInReadAction)
            }

            @Suppress("NON_TAIL_RECURSIVE_CALL")
            val nonInlineClasses = getOuterClassNamesForElement(actualElement.parentInReadAction)
            val inlineCallSiteClasses = nameProvider.findInlinedCalls(
                    actualElement,
                    KotlinDebuggerCaches.getOrCreateTypeMapper(actualElement).bindingContext,
                    transformer = this::getOuterClassNamesForElement)

            nonInlineClasses + inlineCallSiteClasses
        }
        is KtFunctionLiteral -> {
            val typeMapper = KotlinDebuggerCaches.getOrCreateTypeMapper(actualElement)

            val nonInlinedLambdaClassName = runReadAction {
                CodegenBinding.asmTypeForAnonymousClass(typeMapper.bindingContext, actualElement).internalName
            }

            if (!alwaysReturnLambdaParentClass && !InlineUtil.isInlinedArgument(actualElement, typeMapper.bindingContext, true)) {
                return listOf(nonInlinedLambdaClassName)
            }

            @Suppress("NON_TAIL_RECURSIVE_CALL")
            return getOuterClassNamesForElement(actualElement.parentInReadAction) + listOf(nonInlinedLambdaClassName)
        }
        else -> error("Unexpected element type ${element::class.java.name}")
    }
}

private val PsiElement.parentInReadAction
    get() = readAction { it.parent }

private fun <T : PsiElement> getNonStrictParentOfType(element: PsiElement, elementTypes: Array<Class<out T>>): T? {
    for (elementType in elementTypes) {
        if (elementType.isInstance(element)) {
            @Suppress("UNCHECKED_CAST")
            return element as T
        }
    }

    // Do not copy the array (*elementTypes) if the element is one we look for
    return element.readAction { PsiTreeUtil.getNonStrictParentOfType<T>(it, *elementTypes) }
}

private val CLASS_ELEMENT_TYPES = arrayOf<Class<out PsiElement>>(
        KtFile::class.java,
        KtClassOrObject::class.java,
        KtProperty::class.java,
        KtNamedFunction::class.java,
        KtFunctionLiteral::class.java)

// Should be run inside a read action
private fun getNameForNonLocalClass(classOrObject: KtClassOrObject, handleDefaultImpls: Boolean = true): String? {
    val simpleName = classOrObject.name ?: return null

    val containingClass = PsiTreeUtil.getParentOfType(classOrObject, KtClassOrObject::class.java, true)
    val containingClassName = containingClass?.let {
        getNameForNonLocalClass(
                containingClass,
                !(containingClass is KtClass && classOrObject is KtObjectDeclaration && classOrObject.isCompanion())
        ) ?: return null
    }

    val packageFqName = classOrObject.containingKtFile.packageFqName.asString()
    val selfName = if (containingClassName != null) "$containingClassName$$simpleName" else simpleName
    val selfNameWithPackage = if (packageFqName.isEmpty() || containingClassName != null) selfName else "$packageFqName/$selfName"

    return if (handleDefaultImpls && classOrObject is KtClass && classOrObject.isInterface())
        selfNameWithPackage + JvmAbi.DEFAULT_IMPLS_SUFFIX
    else
        selfNameWithPackage
}

private fun DebugProcess.findTargetClass(outerClass: ReferenceType, lineAt: Int): ReferenceType? {
    val vmProxy = virtualMachineProxy
    if (!outerClass.isPrepared) return null

    try {
        val nestedTypes = vmProxy.nestedTypes(outerClass)
        for (nested in nestedTypes) {
            findTargetClass(nested, lineAt)?.let { return it }
        }

        for (location in outerClass.allLineLocations()) {
            val locationLine = location.lineNumber() - 1
            if (locationLine <= 0) {
                // such locations are not correspond to real lines in code
                continue
            }

            val method = location.method()
            if (method == null || DebuggerUtils.isSynthetic(method) || method.isBridge) {
                // skip synthetic methods
                continue
            }

            if (lineAt == locationLine) {
                return outerClass
            }
        }
    }
    catch (_: AbsentInformationException) {}
    return null
}