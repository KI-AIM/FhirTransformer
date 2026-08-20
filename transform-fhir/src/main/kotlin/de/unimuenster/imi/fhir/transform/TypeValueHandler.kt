package de.unimuenster.imi.fhir.transform

import org.hl7.fhir.instance.model.api.IBase
import org.hl7.fhir.instance.model.api.IBaseCoding
import org.hl7.fhir.instance.model.api.IBaseEnumFactory
import org.hl7.fhir.instance.model.api.IBaseEnumeration
import org.hl7.fhir.utilities.xhtml.NodeType
import org.hl7.fhir.utilities.xhtml.XhtmlNode

class TypeValueHandler {

    fun createSpecialTypeInstancesWithValue(type: Class<*>?, resource: IBase?, vararg params: Array<out Any?>): Any? {
        with(type) {
            return when {
                IBaseEnumeration::class.java.isAssignableFrom(type) -> {
                    val fieldName = params[0][1] as String
                    val value = params[0][0].toString()

                    val getter = resource?.getGetterFunction(fieldName) ?: return null

                    @Suppress("UNCHECKED_CAST")
                    val enumInstance = getter.invoke() as IBaseEnumeration<*>

                    enumInstance.valueAsString = value
                    enumInstance
                }

                XhtmlNode::class.java.isAssignableFrom(type) -> createXHtmlNode(params)
                else -> null
            }
        }
    }

    fun processValue(value: Any, type: Class<*>?, fieldName: String): Array<Any> {
        with (type) {
            return when {
                IBaseCoding::class.java.isAssignableFrom(this) -> handleCoding(value)
                IBaseEnumeration::class.java.isAssignableFrom(this) -> {arrayOf(value, fieldName)}
                else -> arrayOf(value)
            }
        }
    }

    private fun handleCoding(value: Any): Array<Any> {
        //Constructor: Coding(String theSystem, String theCode, String theDisplay) {
        if (value is String && value.contains("|")) {
            val parts = value.split("|")
            return arrayOf(parts[0], parts[1], "")
        } else {
            return arrayOf(value, "", "")
        }
    }

    private fun createEnumeration(type: Class<*>, resource: IBase, value: String): IBaseEnumeration<*>? {
        val enumFactory = resource.initAndGetEnumFactoryFor(type)
        val params = arrayOf(enumFactory, value)
        return type.constructors
            .firstOrNull { it.parameterCount == 2 }
            ?.let { constructor ->
                try {
                    constructor.newInstance(*params) as IBaseEnumeration<*>
                } catch (_: Exception) {
                    null
                }
            }
    }

    private fun IBase.initAndGetEnumFactoryFor(type: Class<*>): IBaseEnumFactory<*> {
        val enumeration = this::class.java.methods
            .mapNotNull { it.name.takeIf { n -> n.startsWith("get") } }
            .mapNotNull { name -> this.getGetterFunction(name)?.invoke() }
            .filterIsInstance<IBaseEnumeration<*>>()
            .firstOrNull { it.javaClass == type }

        return enumeration?.enumFactory
            ?: throw IllegalArgumentException("No enum of type $type found in resource")
    }


    private fun createXHtmlNode(value: Array<out Any?>): XhtmlNode {
        return XhtmlNode().apply {
            nodeType = NodeType.Text
            content = (value[0] as Array<*>)[0].toString()
        }
    }
}