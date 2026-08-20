package de.unimuenster.imi.fhir.transform

import mu.KotlinLogging
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.hl7.fhir.instance.model.api.IBase
import org.hl7.fhir.r4.model.Bundle
import org.hl7.fhir.r4.model.Resource
import java.io.StringReader
import kotlin.reflect.*
import kotlin.reflect.full.*

class CsvToFhirTransformer {

    private val fhirPackage = "org.hl7.fhir.r4.model"
    private val log = KotlinLogging.logger { }
    private val typeResolver = TypeResolver()
    private val typeValueHandler = TypeValueHandler()

    fun parseCsvToBundle(csvData: String): Bundle {
        val tables = this.parseCsvToResultTable(csvData)
        val resultBundle = Bundle()

        for (row in tables.subtables) {
            val resource = instantiateResource(row)
            fillResourceWithRow(row, resource)
            resultBundle.addEntry().resource = resource as Resource
        }
        return resultBundle
    }

    private fun parseCsvToResultTable(csvData: String): ResultTable {
        val parser = CSVParser.builder().apply {
            reader = StringReader(csvData)
            setFormat(CSVFormat.DEFAULT.builder().apply {
                setHeader()
                setSkipHeaderRecord(true)
            }.get())
        }.get()
        val headers = parser.headerMap.keys.toList()
        val resultTables = mutableListOf<SubTable>()

        for (record in parser) {
            val columnTable = SubTable()
            for ((index, header) in headers.withIndex()) {
                val value = record.get(header)
                columnTable.data[index to header] = mutableListOf(value)
            }
            resultTables.add(columnTable)
        }

        return ResultTable(resultTables)
    }


    private fun fillResourceWithRow(row: SubTable, resource: IBase?) {
        getSubTableWithFilledRows(row).data.forEach { cell ->
            val columnName = cell.key.second
            val value = cell.value.first()

            fillResourceWithValue(resource, columnName, value)
        }
    }

    private fun fillResourceWithValue(resource: IBase?, columnName: String, value: String?) {
        if (value != null) {
            try {
                recursivelyProcessAndFillPath(resource, columnName, value)
                log.info("Value set successfully for FHIRPath: $columnName")
            } catch (e: Exception) {
                log.error("Error setting value for FHIRPath: $columnName. Message: ${e.message}")
            }
        }
    }

    private fun String.getPathWithoutIndex(): String = this.replace(Regex("\\[\\d+]"), "")

    private fun recursivelyProcessAndFillPath(resource: IBase?, columnName: String, value: Any) {
        if (columnName == "Patient.identifier[0].type[0].coding[0].code[0]") {
            print("Hallo")
        }


        val columnPathParts = columnName.split(".")
        val isPathWithNesting = columnPathParts.size > 2
        val index: Int? = if (isPathWithNesting) 1 else null
        val pathWithoutIndex = columnName.getPathWithoutIndex()
        val fieldName = pathWithoutIndex.split(".").last()

        val type = typeResolver.resolveAttributeType(resource!!, pathWithoutIndex, index)

        val instantiatedObject = if (isPathWithNesting) {
            val newObject = instantiateType(type)
            recursivelyProcessAndFillPath(newObject, removeFirstPath(columnName), value)
            newObject
        } else {
            val params = typeValueHandler.processValue(value, type, fieldName)
            instantiateTypeWithValue(type, resource, *params)
        }

        val (part, partIndex) = columnPathParts[1].getPartWithIndex()

        doHandleSetForResource(instantiatedObject, resource, part, partIndex)
    }

    private fun doHandleSetForResource(
        instantiatedObject: IBase?, resource: IBase, propertyName: String, indexValue: Int?
    ) {
        val setter = resource.getSetterFunction(propertyName)
        val getter = resource.getGetterFunction(propertyName)

        val existingEntries = getter?.invoke()

        setter?.let {
            //IDE says "existingEntries.toString()" is always false, but that is not true, so keep it
            if (existingEntries == null || existingEntries.toString() == null) {
                log.info("Entry $propertyName on ${resource.fhirType()} does not exist. Simply set it")
                setObjectForProperty(resource, propertyName, instantiatedObject!!, it)
            } else if (checkExistanceOfPathAtIndex(existingEntries, indexValue)) {
                log.info("Entry $propertyName on ${resource.fhirType()} already exists. It will be merged.")
                val entryToMerge = getExistingEntryAtIndex(existingEntries, indexValue) as IBase
                mergeObjectForProperty(instantiatedObject!!, entryToMerge, existingEntries, indexValue!!, setter)
            } else if (existingEntries is List<*>) {
                log.info(
                    "Entry $propertyName on ${resource.fhirType()} exists, but index points to non-existing entry. A new one will be added."
                )
                addObjectToList(instantiatedObject!!, setter, existingEntries)
            } else {
                log.debug("None of the setting conditions was true. Unable to set $propertyName on ${resource.fhirType()}. Skipping.")
            }
        }
    }

    private fun setObjectForProperty(
        resource: IBase, propertyName: String, objectToSet: IBase, setter: (Any?) -> Unit
    ) {
        if (resource.isSetterParameterAList(propertyName) == true) {
            setter.invoke(listOf(objectToSet))
        } else {
            setter.invoke(objectToSet)
        }
    }

    private fun mergeObjectForProperty(
        objectToSet: IBase,
        existingEntry: IBase,
        existingEntries: Any?,
        index: Int,
        setter: (Any?) -> Unit
    ) {
        if (objectToSet::class != existingEntry::class) {
            throw IllegalArgumentException(
                "objectToSet and existingEntry must be of the same type. " +
                        "${objectToSet::class.simpleName} and ${existingEntry::class.simpleName} were provided."
            )
        }
        val mergedObject = objectToSet.mergeInto(existingEntry)

        if (existingEntries is MutableList<*>) {
            @Suppress("UNCHECKED_CAST")
            (existingEntries as MutableList<Any?>).set(index, mergedObject)
            setter.invoke(existingEntries)
        } else {
            setter.invoke(mergedObject)
        }
    }

    private fun addObjectToList(
        objectToAdd: IBase, setter: (Any?) -> Unit, existingEntries: List<*>
    ) {
        existingEntries.toMutableList().apply { add(objectToAdd) }.let {
            setter.invoke(it)
        }
    }

    private fun <T : Any> T.mergeInto(target: T): T {
        mergeIntoInternal(
            source = this,
            target = target,
            visited = mutableSetOf()
        )

        return target
    }

    private fun mergeIntoInternal(
        source: Any,
        target: Any,
        visited: MutableSet<IdentityPair>
    ) {
        val pair = IdentityPair(source, target)

        if (!visited.add(pair)) {
            return
        }

        source::class.allProperties().forEach { property ->
            if (property !is KMutableProperty<*>) {
                return@forEach
            }

            try {
                val setter = target.getSetterFunction(property.name)
                    ?: return@forEach

                val sourceGetter = source.getGetterFunction(property.name)
                    ?: return@forEach

                val targetGetter = target.getGetterFunction(property.name)

                val sourceValue: Any? = sourceGetter.invoke()

                if (!isPresent(sourceValue)) {
                    return@forEach
                }

                val targetValue: Any? = targetGetter?.invoke()

                when {
                    sourceValue is Collection<*> -> {
                        val mergedList = mergeListsByIndex(
                            targetValue = targetValue,
                            sourceValue = sourceValue,
                            visited = visited
                        )

                        setter.invoke(mergedList)
                    }

                    shouldMergeRecursively(sourceValue, targetValue) -> {
                        mergeIntoInternal(
                            source = sourceValue!!,
                            target = targetValue!!,
                            visited = visited
                        )

                        setter.invoke(targetValue)
                    }

                    else -> {
                        setter.invoke(sourceValue)
                    }
                }
            } catch (ex: Exception) {
                log.debug("Property ${property.name} could not be merged. Skipping.", ex)
            }
        }
    }

    private fun mergeListsByIndex(
        targetValue: Any?,
        sourceValue: Collection<*>,
        visited: MutableSet<IdentityPair>
    ): MutableList<Any?> {
        val targetList: MutableList<Any?> = when (targetValue) {
            is Collection<*> -> targetValue.toMutableList()
            else -> mutableListOf()
        }

        sourceValue.forEachIndexed { index, sourceItem ->
            if (!isPresent(sourceItem)) {
                return@forEachIndexed
            }

            if (index >= targetList.size) {
                targetList.add(sourceItem)
                return@forEachIndexed
            }

            val targetItem = targetList[index]

            when {
                shouldMergeRecursively(sourceItem, targetItem) -> {
                    mergeIntoInternal(
                        source = sourceItem!!,
                        target = targetItem!!,
                        visited = visited
                    )

                    targetList[index] = targetItem
                }

                else -> {
                    targetList[index] = sourceItem
                }
            }
        }

        return targetList
    }

    private fun shouldMergeRecursively(
        sourceValue: Any?,
        targetValue: Any?
    ): Boolean {
        if (sourceValue == null || targetValue == null) {
            return false
        }

        if (sourceValue is Collection<*> || targetValue is Collection<*>) {
            return false
        }

        if (isLeafValue(sourceValue) || isLeafValue(targetValue)) {
            return false
        }

        val sourceClass = sourceValue.javaClass
        val targetClass = targetValue.javaClass

        return sourceClass == targetClass ||
                sourceClass.isAssignableFrom(targetClass) ||
                targetClass.isAssignableFrom(sourceClass)
    }

    private fun isLeafValue(value: Any?): Boolean {
        if (value == null) {
            return true
        }

        return value is CharSequence ||
                value is Number ||
                value is Boolean ||
                value is Enum<*> ||
                value is java.util.Date ||
                value is java.time.temporal.Temporal ||
                value.javaClass.isPrimitive ||
                value.javaClass.packageName.startsWith("java.") ||
                value.javaClass.packageName.startsWith("kotlin.")
    }

    private fun isPresent(value: Any?): Boolean {
        if (value == null) {
            return false
        }

        if (value is Collection<*>) {
            return value.any { isPresent(it) }
        }

        return !isFhirEmpty(value)
    }

    private fun isFhirEmpty(value: Any?): Boolean {
        if (value == null) {
            return true
        }

        return runCatching {
            val method = value.javaClass.methods.firstOrNull {
                it.name == "isEmpty" &&
                        it.parameterCount == 0 &&
                        it.returnType == Boolean::class.javaPrimitiveType
            }

            method?.invoke(value) as? Boolean ?: false
        }.getOrDefault(false)
    }

    private class IdentityPair(
        private val source: Any,
        private val target: Any
    ) {
        override fun equals(other: Any?): Boolean {
            return other is IdentityPair &&
                    source === other.source &&
                    target === other.target
        }

        override fun hashCode(): Int {
            return 31 * System.identityHashCode(source) + System.identityHashCode(target)
        }
    }


    private fun checkExistanceOfPathAtIndex(existingEntry: Any?, index: Int?): Boolean {
        if (existingEntry == null) return false
        if (index == null || existingEntry !is List<*>) return true

        return existingEntry.getOrNull(index) != null
    }

    private fun getExistingEntryAtIndex(existingEntries: Any?, index: Int?): Any? {
        if (existingEntries == null) return null
        if (index == null || existingEntries !is List<*>) return existingEntries
        return existingEntries.getOrNull(index)
    }

    private fun removeFirstPath(columnName: String): String =
        columnName.split(".").toMutableList().apply {
            removeAt(0)
        }.joinToString(".")

    private fun String.getPartWithIndex(): Pair<String, Int> {
        if (this.contains("[") && this.contains("]")) {
            val index = this.substringAfter("[").substringBefore("]").toInt()
            val part = this.substringBefore("[")
            return Pair(part, index)
        }
        return Pair(
            this, 0
        )
    }

    private fun instantiateResource(row: SubTable): IBase? {
        val resourceType = getResourceTypeForRow(row)
        return try {
            val clazz = Class.forName("$fhirPackage.$resourceType").kotlin
            return clazz.createInstance() as IBase?
        } catch (e: ClassNotFoundException) {
            log.error("Could not instantiate. Resource type not found: $resourceType")
            null
        } catch (e: Exception) {
            log.error("Error instantiating resource: ${e.message}")
            null
        }
    }

    private fun instantiateType(type: Class<*>?): IBase? {
        return type?.kotlin?.createInstance() as IBase?
    }

    private fun instantiateTypeWithValue(type: Class<*>?, resource: IBase?, vararg params: Any?): IBase? {
        val kClass = type?.kotlin
        typeValueHandler.createSpecialTypeInstancesWithValue(type, resource, params)?.let { return it as IBase? }

        kClass?.constructors?.forEach { constructor ->
            try {
                return constructor.call(*params) as? IBase
            } catch (_: Exception) {
            }
        }
        log.error("Could not instantiate type '${kClass?.simpleName}' with parameters: ${params.toList()}")
        return null

    }

    private fun getResourceTypeForRow(row: SubTable): String {
        val filledCells = row.data.filter { it -> it.value.any { !it?.isEmpty()!! } }
        val resourceName = filledCells.keys.first().second.split(".").first()
        return resourceName
    }

    private fun getSubTableWithFilledRows(row: SubTable): SubTable {
        val filledCells = row.data.filter { it.value.any { !it.isNullOrEmpty() } }
        return SubTable().apply { data.putAll(filledCells) }
    }




}