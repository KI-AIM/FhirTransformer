package de.unimuenster.imi.fhir.transform

import ca.uhn.fhir.model.api.annotation.Child
import kotlin.reflect.KCallable
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KProperty1
import kotlin.reflect.KTypeProjection
import kotlin.reflect.full.createType
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.isSubtypeOf
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.superclasses

private val log = mu.KotlinLogging.logger("de.unimuenster.imi.fhir.transform.PropertyHandler")

fun <T : Any> KClass<out T>.getTargetProperty(attributeName: String): KProperty1<out T, *>? {
    handleSpecialProperties(attributeName)?.let { return it }

    return this.allProperties().firstOrNull { property ->
        val childAnnotation = property.findAnnotation<Child>()
        (childAnnotation?.name == attributeName ||
                property.name == attributeName)
    }
}

fun <T: Any> KClass<out T>.handleSpecialProperties(attributeName: String): KProperty1<out T, *>? {
    val kClass: KClass<out T> = this
    return with(attributeName) {
        when {
            this.lowercase().contains("value") -> {
                kClass.allProperties().firstOrNull { property ->
                    property.name == "value"
                }
            }

            this.contains("DateTime") ||
                    this.contains("Date") ||
                    this.contains("Period") ||
                    this.contains("Timing") ||
                    this.contains("Instant")
                -> {
                kClass.allProperties().firstOrNull { property ->
                    property.name == this.removeTemporalString()
                }
            }

            else -> {
                null
            }
        } as KProperty1<out T, *>?
    }
}

fun String.removeTemporalString(): String {
    return with(this) {
        when {
            contains("DateTime") -> this.replace("DateTime", "")

            contains("Date") &&
                    !contains("DateTime") -> this.replace("Date", "")

            contains("Period") -> this.replace("Period", "")

            contains("Timing") -> this.replace("Timing", "")

            contains("Instant") -> this.replace("Instant", "")

            else -> this
        }
    }
}

fun <T : Any> T.getGetterFunction(attributeName: String): (() -> Any?)? {
    val kClass: KClass<out T> = this::class

    val targetProperty = kClass.getTargetProperty(attributeName)

    if (targetProperty != null) {
        val getterFunctions = kClass.getGetterFunctionsForClass(targetProperty)

        if (getterFunctions?.isNotEmpty() ?: false) {
            return getterFunctions!!.first().let { method ->
                { method.call(this) }
            }
        }
    }

    log.info("Could not find getter function for attribute '$attributeName' on class ${kClass.simpleName}")
    return null
}

fun <T : Any> T.getSetterFunction(
    attributeName: String
): ((Any?) -> Unit)? {
    val kClass: KClass<out T> = this::class

    val targetProperty = kClass.getTargetProperty(attributeName)

    if (targetProperty != null) {
        val setterFunctions = kClass.getSetterFunctionsForClass(targetProperty)

        if (setterFunctions.isNotEmpty()) {
            return setterFunctions.first().let { method ->
                { value: Any? -> method.call(this, value) }
            }
        }
    }

    log.info("Could not find setter function for attribute '$attributeName' on class ${kClass.simpleName}")
    return null
}

fun <T : Any> T.isSetterParameterAList(
    attributeName: String,
): Boolean? {
    val kClass: KClass<out T> = this::class

    val targetProperty = kClass.getTargetProperty(attributeName)

    if (targetProperty != null) {
        val setterFunctions = kClass.getSetterFunctionsForClass(targetProperty)

        if (setterFunctions.isNotEmpty()) {
            return setterFunctions.first().parameters[1].type.isSubtypeOf(
                Collection::class.createType(arguments = listOf(KTypeProjection.STAR))
            )
        }
    }

    log.info("Could not find setter function when searching for parameter type - '$attributeName' on class ${kClass.simpleName}")
    return null
}

fun <T : Any> KClass<out T>.getSetterFunctionsForClass(
    targetProperty: KProperty1<out T, *>
): List<KCallable<*>> {
    val members = this.allMembers()

    val candidate = members.filter { member ->
        member is KFunction<*> && member.parameters.size == 2 && targetProperty.returnType == member.parameters[1].type && member.name.lowercase()
            .contains(targetProperty.name.lowercase())
    }

    if (candidate.isNotEmpty()) return candidate

    return this.handlePrimitiveSetter(targetProperty)
}

fun <T: Any> KClass<out T>.handlePrimitiveSetter(targetProperty: KProperty1<out T, *>): List<KCallable<*>> {
    return when (targetProperty.name) {
        "myStringValue", "myCoercedValue" -> {
            listOf(this.members.firstOrNull { member ->
                member is KFunction<*> && member.parameters.size == 2 && member.name == "fromStringValue"
            })
        }

        else -> {
            listOf()
        }
    } as List<KCallable<*>>
}

fun <T : Any> KClass<out T>.getGetterFunctionsForClass(
    targetProperty: KProperty1<out T, *>
): List<KCallable<*>>? {
    val members = this.allMembers()
    val candidate = members.filter { member ->
        member is KFunction<*> && member.parameters.size == 1 && targetProperty.returnType == member.returnType && member.name.lowercase()
            .contains(targetProperty.name.lowercase())
    }

    if (candidate.isNotEmpty()) return candidate

    return this.handlePrimitiveGetter(targetProperty)

}

fun <T: Any> KClass<out T>.handlePrimitiveGetter(targetProperty: KProperty1<out T, *>): List<KCallable<*>> {
    return when (targetProperty.name) {
        "myStringValue" -> {
            listOf(this.members.first { member ->
                member is KFunction<*> && member.parameters.size == 1 && member.name == "asStringValue"
            })
        }
        "myCoercedValue" -> {
            listOf(this.members.first { member ->
                member is KFunction<*> && member.parameters.size == 1 && member.name == "getValue"
            })
        }
        else -> {
            listOf()
        }
    }
}

fun <T : Any> KClass<T>.allProperties(): List<KProperty1<out T, *>> {
    val properties = mutableSetOf<KProperty1<out T, *>>()

    // Recursively collect all properties
    fun collectProperties(currentClass: KClass<out Any>) {
        properties.addAll(currentClass.memberProperties as Collection<KProperty1<out T, *>>)
        currentClass.superclasses.forEach { parent ->
            collectProperties(parent)
        }
    }

    collectProperties(this)

    return properties.toList()
}

fun <T : Any> KClass<T>.allMembers(): List<KCallable<*>> {
    val members = mutableSetOf<KCallable<*>>()

    fun collectMembers(currentClass: KClass<out Any>) {
        members.addAll(currentClass.members as Collection<KCallable<*>>)
        currentClass.superclasses.forEach { parent ->
            collectMembers(parent)
        }
    }

    collectMembers(this)
    return members.toList()
}