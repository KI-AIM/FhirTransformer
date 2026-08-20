package de.unimuenster.imi.fhir.transform

import ca.uhn.fhir.context.BaseRuntimeChildDatatypeDefinition
import ca.uhn.fhir.context.BaseRuntimeChildDefinition
import ca.uhn.fhir.context.FhirVersionEnum
import ca.uhn.fhir.context.FhirVersionEnum.determineVersionForType
import ca.uhn.fhir.context.RuntimeChildResourceBlockDefinition
import ca.uhn.fhir.context.RuntimeChildResourceDefinition
import ca.uhn.fhir.parser.DataFormatException
import org.hl7.fhir.r4.model.CodeableConcept as R4CodeableConcept
import org.hl7.fhir.instance.model.api.IBase
import org.hl7.fhir.instance.model.api.IBaseBackboneElement
import org.hl7.fhir.instance.model.api.IBaseResource
import org.hl7.fhir.r4.model.DecimalType as R4DecimalType
import org.hl7.fhir.r4.model.IntegerType as R4IntegerType
import org.hl7.fhir.r4.model.StringType as R4StringType
import org.hl7.fhir.r4.model.BooleanType as R4BooleanType
import org.hl7.fhir.r4.model.DateTimeType as R4DateTimeType
import org.hl7.fhir.r4.model.DateType as R4DateType
import org.hl7.fhir.r4.model.TimeType as R4TimeType
import org.hl7.fhir.r4.model.Quantity as R4Quantity
import org.hl7.fhir.r4.model.Range as R4Range
import org.hl7.fhir.r4.model.Ratio as R4Ratio
import org.hl7.fhir.r4.model.SampledData as R4SampledData
import org.hl7.fhir.r4.model.Period as R4Period
import org.hl7.fhir.r4.model.Attachment as R4Attachment
import org.hl7.fhir.r4.model.Reference as R4Reference
import org.hl7.fhir.r4.model.Timing as R4Timing
import org.hl7.fhir.r4.model.InstantType as R4Instant


class TypeResolver {
    private var fhirVersion = FhirVersionEnum.R4
    private var currentProcessingContext = fhirVersion.newContext()
    private val log = mu.KotlinLogging.logger("de.unimuenster.imi.fhir.transform.TypeResolver")

    private fun updateFhirVersionForResource(resource: IBase) {
        val newFhirVersion = determineVersionForType(resource::class.java)
        if (newFhirVersion != fhirVersion) {
            log.info("Detected FHIR version change for resource ${resource.fhirType()}. New version: $newFhirVersion")
            fhirVersion = newFhirVersion
            currentProcessingContext = fhirVersion.newContext()
        }
    }

    fun resolveAttributeType(
        resource: IBase, fhirPath: String, resolveForPathIndex: Int? = null
    ): Class<*>? {
        try {
            val structureDefinition = getStructureDefinition(resource)

            val parsedPath = parseFhirPath(fhirPath, resolveForPathIndex)

            checkAndReturnValueTypes(parsedPath[0])?.let { return it }
            checkAndReturnTemporalTypes(parsedPath[0])?.let { return it }

            if (parsedPath.isEmpty() || parsedPath[0].isPathAnInteger()) {
                return null
            }


            val childDefinition =
                structureDefinition.getChildByName(parsedPath[0]) ?: throw IllegalArgumentException(
                    "ChildDefinition not found for path: $parsedPath"
                )

            parsedPath.removeAt(0)
            return recursivelyProcessChildTypes(parsedPath, childDefinition)

        } catch (_: DataFormatException) {

        } catch (e: Exception) {
            log.error("Error resolving attribute type for FHIRPath: $fhirPath. Exception: ${e.message}")
        }
        return null

    }

    private fun getStructureDefinition(resource: IBase): Any {
        return when (resource) {
            is IBaseResource -> currentProcessingContext.getResourceDefinition(
                fhirVersion, resource.fhirType()
            )
                ?: throw IllegalArgumentException("StructureDefinition not found for resource type: ${resource.fhirType()}")

            is IBaseBackboneElement -> {
                val parts = resource.fhirType().split(".")
                val parentDefinition = currentProcessingContext.getResourceDefinition(
                    fhirVersion, parts[0]
                )
                parentDefinition.getChildByName(parts[1]) as BaseRuntimeChildDefinition
            }

            else -> currentProcessingContext.getElementDefinition(resource.fhirType())
                ?: throw IllegalArgumentException("StructureDefinition not found for element type: ${resource.fhirType()}")
        }
    }

    private fun parseFhirPath(
        fhirPath: String, resolveForPathIndex: Int? = null
    ): MutableList<String> {
        val parsedPath = fhirPath.split('.').toMutableList()

        return if (resolveForPathIndex != null) {
            parsedPath.filterIndexed { index, _ -> index == resolveForPathIndex }.toMutableList()
        } else {
            parsedPath.apply { removeAt(0) }
        }
    }

    private fun recursivelyProcessChildTypes(
        parsedPath: MutableList<String>, currentDefinition: BaseRuntimeChildDefinition
    ): Class<*>? {
        if (parsedPath.isNotEmpty() && !parsedPath[0].isPathAnInteger()) {
            val childDefinition = currentDefinition.getElementDefinition()
                ?.getChildByName(parsedPath[0]) as BaseRuntimeChildDefinition?
                ?: throw IllegalArgumentException("ChildDefinition not found for path: $parsedPath")

            parsedPath.removeAt(0)
            return recursivelyProcessChildTypes(parsedPath, childDefinition)
        } else {
            return when (currentDefinition) {
                is BaseRuntimeChildDatatypeDefinition -> {
                    currentDefinition.datatype
                }

                is RuntimeChildResourceDefinition -> {
                    currentDefinition.field.type
                }

                is RuntimeChildResourceBlockDefinition-> {
                    currentDefinition.privateField<Class<*>>("myResourceBlockType")
                }

                else -> {
                    null
                }
            }
        }
    }

    private fun String.isPathAnInteger(): Boolean {
        try {
            this.toInt()
            return true
        } catch (_: NumberFormatException) {
            return false
        }
    }

    private fun checkAndReturnValueTypes(path: String): Class<*>? {
        return when (path) {
            "valueQuantity" -> R4Quantity::class.java
            "valueCodeableConcept" -> R4CodeableConcept::class.java
            "valueRange" -> R4Range::class.java
            "valueRatio" -> R4Ratio::class.java
            "valueSampledData" -> R4SampledData::class.java
            "valueTime" -> R4TimeType::class.java
            "valueDateTime" -> R4DateTimeType::class.java
            "valuePeriod" -> R4Period::class.java
            "valueAttachment" -> R4Attachment::class.java
            "valueString" -> R4StringType::class.java
            "valueBoolean" -> R4BooleanType::class.java
            "valueInteger" -> R4IntegerType::class.java
            "valueDecimal" -> R4DecimalType::class.java
            "valueReference" -> R4Reference::class.java
            else -> null
        }
    }

    private fun checkAndReturnTemporalTypes(path: String): Class<*>? {
        return when {
            path.contains("DateTime") -> R4DateTimeType::class.java
            path.contains("Date") && !path.contains("DateTime") -> R4DateType::class.java
            path.contains("Period") -> R4Period::class.java
            path.contains("Timing") -> R4Timing::class.java
            path.contains("Instant") -> R4Instant::class.java
            else -> null
        }
    }
}