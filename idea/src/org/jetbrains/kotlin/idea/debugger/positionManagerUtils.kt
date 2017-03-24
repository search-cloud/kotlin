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
import com.intellij.psi.util.PsiTreeUtil
import com.sun.jdi.AbsentInformationException
import com.sun.jdi.ReferenceType
import org.jetbrains.kotlin.codegen.AsmUtil
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.fileClasses.NoResolveFileClassesProvider
import org.jetbrains.kotlin.fileClasses.getFileClassInternalName
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptor
import org.jetbrains.kotlin.idea.debugger.breakpoints.getLambdasAtLineIfAny
import org.jetbrains.kotlin.load.java.JvmAbi
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.isObjectLiteral

fun DebugProcess.getClassesForPosition(position: SourcePosition): List<ReferenceType> {
    return doGetClassesForPosition(position) { className, lineNumber ->
        virtualMachineProxy.classesByName(className).map { findTargetClass(it, lineNumber) }
    }
}

fun DebugProcess.getOuterClassesForPosition(position: SourcePosition): List<ReferenceType> {
    return doGetClassesForPosition(position) { className, _ -> virtualMachineProxy.classesByName(className) }
}

fun getOuterClassInternalNamesForPosition(position: SourcePosition): List<String> {
    return doGetClassesForPosition(position) { className, _ -> listOf(className) }
}

private inline fun <T: Any> doGetClassesForPosition(
        position: SourcePosition,
        transformer: (className: String, lineNumber: Int) -> List<T?>
): List<T> {
    val line = position.line
    val result = getOuterClassNamesForElement(position.elementAt)
            .flatMap { transformer(it, line) }
            .filterNotNullTo(mutableSetOf())

    for (lambda in getLambdasAtLineIfAny(position)) {
        getOuterClassNamesForElement(lambda).flatMap { transformer(it, line) }.filterNotNullTo(result)
    }

    return result.toList()
}

@PublishedApi
internal tailrec fun getOuterClassNamesForElement(element: PsiElement?): List<String> {
    if (element == null) return emptyList()
    val actualElement = getNonStrictParentOfType(element, CLASS_ELEMENT_TYPES)

    return when (actualElement) {
        is KtFile -> {
            listOf(NoResolveFileClassesProvider.getFileClassInternalName(actualElement))
        }
        is KtClassOrObject -> {
            val enclosingElementForLocal = KtPsiUtil.getEnclosingElementForLocalDeclaration(actualElement)
            if (enclosingElementForLocal != null) { // A local class
                getOuterClassNamesForElement(enclosingElementForLocal)
            }
            else if (actualElement.isObjectLiteral()) {
                getOuterClassNamesForElement(actualElement.parent)
            }
            else { // Guaranteed to be non-local class or object
                getNameForNonLocalClass(actualElement)?.let { listOf(it) } ?: emptyList()
            }
        }
        is KtProperty -> {
            if (actualElement.isTopLevel) {
                return getOuterClassNamesForElement(actualElement.parent)
            }

            val enclosingElementForLocal = KtPsiUtil.getEnclosingElementForLocalDeclaration(actualElement)
            if (enclosingElementForLocal != null) {
                return getOuterClassNamesForElement(enclosingElementForLocal)
            }

            val containingClassOrFile = PsiTreeUtil.getParentOfType(actualElement, KtFile::class.java, KtClassOrObject::class.java)
            if (containingClassOrFile is KtObjectDeclaration && containingClassOrFile.isCompanion()) {
                val descriptor = actualElement.resolveToDescriptor() as? PropertyDescriptor
                // Properties from the companion object are placed in the companion object's containing class
                if (descriptor != null && AsmUtil.isPropertyWithBackingFieldCopyInOuterClass(descriptor)) {
                    return getOuterClassNamesForElement(containingClassOrFile.parent)
                }
            }

            if (containingClassOrFile != null)
                getOuterClassNamesForElement(containingClassOrFile)
            else
                getOuterClassNamesForElement(actualElement.parent)
        }
        else -> error("Unexpected element type ${element::class.java.name}")
    }
}

private fun <T : PsiElement> getNonStrictParentOfType(element: PsiElement, elementTypes: Array<Class<out T>>): T? {
    for (elementType in elementTypes) {
        if (elementType.isInstance(element)) {
            @Suppress("UNCHECKED_CAST")
            return element as T
        }
    }

    // Do not copy the array (*elementTypes) if the element is one we look for
    return PsiTreeUtil.getNonStrictParentOfType<T>(element, *elementTypes)
}

private val CLASS_ELEMENT_TYPES = arrayOf<Class<out PsiElement>>(
        KtFile::class.java,
        KtClassOrObject::class.java,
        KtProperty::class.java)

private fun getNameForNonLocalClass(classOrObject: KtClassOrObject, handleDefaultImpls: Boolean = true): String? {
    val simpleName = classOrObject.name ?: return null

    val containingClass = PsiTreeUtil.getParentOfType(classOrObject, KtClassOrObject::class.java, true)
    val containingClassName = containingClass?.let {
        getNameForNonLocalClass(
                containingClass,
                !(containingClass is KtClass && classOrObject is KtObjectDeclaration && classOrObject.isCompanion())
        ) ?: return null
    }

    val packageFqName = classOrObject.containingKtFile.packageFqName
    val selfName = if (containingClassName != null) "$containingClassName$$simpleName" else simpleName
    val selfNameWithPackage = if (packageFqName.isRoot) selfName else "$packageFqName.$selfName"

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