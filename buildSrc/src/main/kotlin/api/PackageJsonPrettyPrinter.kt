package buildsrc.convention.api

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter

/**
 * @author CJ
 */
@Suppress("DEPRECATION")
class PackageJsonPrettyPrinter : DefaultPrettyPrinter() {
    init {
        _spacesInObjectEntries = false
//        indentArraysWith(FixedSpaceIndenter.instance)
//        indentObjectsWith(FixedSpaceIndenter.instance)
    }

    override fun writeObjectFieldValueSeparator(g: JsonGenerator?) {
        g?.writeRaw(_separators.objectFieldValueSeparator)
        g?.writeRaw(' ')
    }

    override fun writeEndArray(g: JsonGenerator?, nrOfValues: Int) {
        if (!_arrayIndenter.isInline) {
            --_nesting
        }
        if (nrOfValues > 0) {
            _arrayIndenter.writeIndentation(g, _nesting)
        } else {
//            g!!.writeRaw(' ')
        }
        g?.writeRaw(']')
    }

    override fun writeEndObject(g: JsonGenerator?, nrOfEntries: Int) {
        if (!_objectIndenter.isInline) {
            --_nesting
        }
        if (nrOfEntries > 0) {
            _objectIndenter.writeIndentation(g, _nesting)
        } else {
//            g!!.writeRaw(' ')
        }
        g?.writeRaw('}')
    }

    override fun createInstance(): PackageJsonPrettyPrinter {
        return PackageJsonPrettyPrinter()
    }
}