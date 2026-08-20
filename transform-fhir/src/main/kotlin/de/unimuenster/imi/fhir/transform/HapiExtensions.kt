package de.unimuenster.imi.fhir.transform

import ca.uhn.fhir.context.BaseRuntimeChildDatatypeDefinition
import ca.uhn.fhir.context.BaseRuntimeChildDefinition
import ca.uhn.fhir.context.BaseRuntimeElementDefinition
import ca.uhn.fhir.context.RuntimeChildChoiceDefinition
import ca.uhn.fhir.context.RuntimeChildCompositeBoundDatatypeDefinition
import ca.uhn.fhir.context.RuntimeChildCompositeDatatypeDefinition
import ca.uhn.fhir.context.RuntimeChildContainedResources
import ca.uhn.fhir.context.RuntimeChildNarrativeDefinition
import ca.uhn.fhir.context.RuntimeChildPrimitiveBoundCodeDatatypeDefinition
import ca.uhn.fhir.context.RuntimeChildPrimitiveDatatypeDefinition
import ca.uhn.fhir.context.RuntimeChildPrimitiveEnumerationDatatypeDefinition
import ca.uhn.fhir.context.RuntimeChildResourceBlockDefinition
import ca.uhn.fhir.context.RuntimeResourceDefinition
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.jvm.isAccessible

private val log = mu.KotlinLogging.logger("de.unimuenster.imi.fhir.transform.HapiExtensions")

fun <T> Any.privateField(name: String): T {
    val property = this::class.declaredMemberProperties.firstOrNull { it.name == name }

    if (property != null) {
        property.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return property.getter.call(this) as T
    }
    throw IllegalArgumentException("No private field found with name: $name")
}

fun <T> Any.getPrivateElementDefinition(): T {
    val prop = BaseRuntimeChildDatatypeDefinition::class.java.getDeclaredField("myElementDefinition")
    prop.isAccessible = true
    @Suppress("UNCHECKED_CAST")
    return prop.get(this) as T
}

fun Any.getChildByName(name: String): BaseRuntimeChildDefinition? {
    return when (this) {
        is RuntimeResourceDefinition -> this.getChildByName(name)
        is RuntimeChildResourceBlockDefinition -> this.getChildByName(name)
        is BaseRuntimeElementDefinition<*> -> this.getChildByName(name)
        else -> null
    } as BaseRuntimeChildDefinition?
}

//These do not have a myElementDefinition prop. They probably need separate handling, if relevant
//TODO: RuntimeChildDeclaredExtensionDefinition
//TODO: RuntimeChildDirectResource
//TODO: RuntimeChildExt
//TODO: RuntimeChildExtension
//TODO: RuntimeChildResourceBlockDefinition
//TODO: RuntimeChildResourceDefinition
//TODO: RuntimeChildUndeclaredExtensionDefinition

fun BaseRuntimeChildDefinition.getElementDefinition(): BaseRuntimeElementDefinition<*> {
    return when (this) {
        is RuntimeChildChoiceDefinition -> getElementDefinition()
        is RuntimeChildCompositeBoundDatatypeDefinition -> getElementDefinition()
        is RuntimeChildCompositeDatatypeDefinition -> getElementDefinition()
        is RuntimeChildContainedResources -> getElementDefinition()
        is RuntimeChildPrimitiveBoundCodeDatatypeDefinition -> getElementDefinition()
        is RuntimeChildPrimitiveDatatypeDefinition -> getElementDefinition()
        is BaseRuntimeChildDatatypeDefinition -> getElementDefinition()
        else -> throw IllegalArgumentException("Unknown RuntimeChildDefinition: ${this::class.java}")
    }
}

fun BaseRuntimeChildDatatypeDefinition.getElementDefinition(): BaseRuntimeElementDefinition<*> {
    return when (this) {
        is RuntimeChildNarrativeDefinition -> getElementDefinition()
        is RuntimeChildPrimitiveEnumerationDatatypeDefinition -> getElementDefinition()
        else -> throw IllegalArgumentException("Unknown RuntimeChildDefinition: ${this::class.java}")
    }
}

fun RuntimeChildChoiceDefinition.getElementDefinition(): BaseRuntimeElementDefinition<*> {
    return getPrivateElementDefinition() as BaseRuntimeElementDefinition<*>
}

fun RuntimeChildCompositeBoundDatatypeDefinition.getElementDefinition(): BaseRuntimeElementDefinition<*> {
    return getPrivateElementDefinition() as BaseRuntimeElementDefinition<*>
}

fun RuntimeChildCompositeDatatypeDefinition.getElementDefinition(): BaseRuntimeElementDefinition<*> {
    return getPrivateElementDefinition() as BaseRuntimeElementDefinition<*>
}

fun RuntimeChildContainedResources.getElementDefinition(): BaseRuntimeElementDefinition<*> {
    //TODO: Seems to be called "myElem" for this class...
    return getPrivateElementDefinition() as BaseRuntimeElementDefinition<*>
}

fun RuntimeChildNarrativeDefinition.getElementDefinition(): BaseRuntimeElementDefinition<*> {
    return getPrivateElementDefinition() as BaseRuntimeElementDefinition<*>
}

fun RuntimeChildPrimitiveBoundCodeDatatypeDefinition.getElementDefinition(): BaseRuntimeElementDefinition<*> {
    return getPrivateElementDefinition() as BaseRuntimeElementDefinition<*>
}

fun RuntimeChildPrimitiveDatatypeDefinition.getElementDefinition(): BaseRuntimeElementDefinition<*> {
    return getPrivateElementDefinition() as BaseRuntimeElementDefinition<*>
}

fun RuntimeChildPrimitiveEnumerationDatatypeDefinition.getElementDefinition(): BaseRuntimeElementDefinition<*> {
    return getPrivateElementDefinition() as BaseRuntimeElementDefinition<*>
}




